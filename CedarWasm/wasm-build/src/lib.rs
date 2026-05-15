use cedar_policy::entities_errors::EntitiesError;
#[cfg(feature = "partial-eval")]
use cedar_policy::ffi::is_authorized_partial_json_str;
use cedar_policy::ffi::{
    is_authorized_json_str, preparse_policy_set, preparse_schema,
    schema_to_json, schema_to_text, stateful_is_authorized, validate_json_str,
    CheckParseAnswer, PolicySet as PolicySetFFI, Schema as FFISchema,
    SchemaToJsonAnswer, SchemaToTextAnswer, StatefulAuthorizationCall,
};
use cedar_policy::{Entities as CedarEntities, Policy, PolicySet, Schema, Template};
use cedar_policy_formatter::{policies_str_to_pretty, Config};
use serde::{Deserialize, Serialize};
use serde_json::{from_str, Value};
use std::alloc::Layout;
use std::str::FromStr;

// ── Memory management ──────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    if size == 0 {
        return std::ptr::null_mut();
    }
    let layout = Layout::from_size_align(size, 1).unwrap();
    unsafe { std::alloc::alloc(layout) }
}

#[no_mangle]
pub unsafe extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    if size > 0 && !ptr.is_null() {
        let layout = Layout::from_size_align(size, 1).unwrap();
        std::alloc::dealloc(ptr, layout);
    }
}

// ── Wide pointer helpers ───────────────────────────────────────────────

fn return_string(s: &str) -> *const u32 {
    return_bytes(s.as_bytes())
}

fn return_bytes(data: &[u8]) -> *const u32 {
    let len = data.len();
    let data_ptr = alloc(len);
    unsafe {
        core::ptr::copy_nonoverlapping(data.as_ptr(), data_ptr, len);
        let wide = alloc(8) as *mut u32;
        core::ptr::write(wide, data_ptr as u32);
        core::ptr::write(wide.add(1), len as u32);
        wide as *const u32
    }
}

fn read_str<'a>(ptr: *const u8, len: usize) -> &'a str {
    unsafe { core::str::from_utf8_unchecked(core::slice::from_raw_parts(ptr, len)) }
}

// ── Answer type (mirrors CedarJavaFFI/src/answer.rs) ───────────────────

#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "success")]
enum Answer {
    #[serde(rename = "true")]
    Success { result: String },
    #[serde(rename = "false")]
    Failure {
        #[serde(rename = "isInternal")]
        is_internal: bool,
        errors: Vec<String>,
    },
}

impl Answer {
    fn fail_internally(message: String) -> Self {
        Self::Failure {
            is_internal: true,
            errors: vec![message],
        }
    }

    fn fail_bad_request(errors: Vec<String>) -> Self {
        Self::Failure {
            is_internal: false,
            errors,
        }
    }
}

// ── Validate entities ──────────────────────────────────────────────────

#[derive(Serialize, Deserialize)]
struct ValidateEntityCall {
    schema: Value,
    entities: Value,
}

fn json_validate_entities(input: &str) -> serde_json::Result<String> {
    let ans = validate_entities(input)?;
    serde_json::to_string(&ans)
}

fn validate_entities(input: &str) -> serde_json::Result<Answer> {
    let call = from_str::<ValidateEntityCall>(input)?;
    let schema = match call.schema {
        Value::String(cedarschema_str) => match Schema::from_cedarschema_str(&cedarschema_str) {
            Ok(s) => s.0,
            Err(e) => return Ok(Answer::fail_bad_request(vec![e.to_string()])),
        },
        cedarschema_json_obj => match Schema::from_json_value(cedarschema_json_obj) {
            Ok(s) => s,
            Err(e) => return Ok(Answer::fail_bad_request(vec![e.to_string()])),
        },
    };
    match CedarEntities::from_json_value(call.entities, Some(&schema)) {
        Err(error) => {
            let err_message = match error {
                EntitiesError::Serialization(err) => err.to_string(),
                EntitiesError::Deserialization(err) => err.to_string(),
                EntitiesError::Duplicate(err) => err.to_string(),
                EntitiesError::TransitiveClosureError(err) => err.to_string(),
                EntitiesError::InvalidEntity(err) => err.to_string(),
            };
            Ok(Answer::fail_bad_request(vec![err_message]))
        }
        Ok(_) => Ok(Answer::Success {
            result: "null".to_string(),
        }),
    }
}

// ── Validate with level (helpers.rs port) ──────────────────────────────

use cedar_policy::ffi::{
    JsonValueWithNoDuplicateKeys, PolicySet as FFIPolicies, ValidationAnswer, ValidationError,
};
use cedar_policy::{SchemaFragment, SchemaWarning, ValidationMode, Validator};

#[derive(Serialize, Deserialize, Debug, Default)]
#[serde(rename_all = "camelCase")]
#[serde(deny_unknown_fields)]
struct ValidationSettings {
    mode: ValidationMode,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
#[serde(deny_unknown_fields)]
struct LevelValidationCall {
    #[serde(default)]
    validation_settings: ValidationSettings,
    schema: FFISchemaLocal,
    policies: FFIPolicies,
    max_deref_level: u32,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(untagged)]
enum FFISchemaLocal {
    Cedar(String),
    Json(JsonValueWithNoDuplicateKeys),
}

impl FFISchemaLocal {
    fn parse(self) -> Result<(Schema, Box<dyn Iterator<Item = SchemaWarning>>), miette::Report> {
        use miette::Context;
        let (frag, warnings) = match self {
            Self::Cedar(s) => {
                let (frag, w) = SchemaFragment::from_cedarschema_str(&s)
                    .wrap_err("failed to parse schema from string")?;
                (
                    frag,
                    Box::new(w) as Box<dyn Iterator<Item = SchemaWarning>>,
                )
            }
            Self::Json(val) => {
                let frag = SchemaFragment::from_json_value(val.into())
                    .wrap_err("failed to parse schema from JSON")?;
                (
                    frag,
                    Box::new(std::iter::empty()) as Box<dyn Iterator<Item = SchemaWarning>>,
                )
            }
        };
        Ok((frag.try_into()?, warnings))
    }
}

fn validate_with_level_json_str(json: &str) -> Result<String, serde_json::Error> {
    let call: LevelValidationCall = serde_json::from_str(json)?;
    let ans = validate_with_level(call);
    serde_json::to_string(&ans)
}

fn validate_with_level(call: LevelValidationCall) -> ValidationAnswer {
    let mut errs = vec![];
    let policies = match call.policies.parse() {
        Ok(p) => p,
        Err(e) => {
            errs.extend(e);
            PolicySet::new()
        }
    };
    let pair = match call.schema.parse() {
        Ok((schema, warnings)) => Some((schema, warnings)),
        Err(e) => {
            errs.push(e);
            None
        }
    };
    match (errs.is_empty(), pair) {
        (true, Some((schema, warnings))) => {
            let validator = Validator::new(schema);
            let result =
                validator.validate_with_level(&policies, call.validation_settings.mode, call.max_deref_level);
            let validation_errors: Vec<ValidationError> = result
                .validation_errors()
                .map(|e| ValidationError {
                    policy_id: e.policy_id().clone(),
                    error: miette::Report::new(e.clone()).into(),
                })
                .collect();
            let validation_warnings: Vec<ValidationError> = result
                .validation_warnings()
                .map(|e| ValidationError {
                    policy_id: e.policy_id().clone(),
                    error: miette::Report::new(e.clone()).into(),
                })
                .collect();
            ValidationAnswer::Success {
                validation_errors,
                validation_warnings,
                other_warnings: warnings.map(|w| miette::Report::new(w).into()).collect(),
            }
        }
        _ => ValidationAnswer::Failure {
            errors: errs.into_iter().map(Into::into).collect(),
            warnings: vec![],
        },
    }
}

// ── Main dispatcher ────────────────────────────────────────────────────

const V0_AUTH_OP: &str = "AuthorizationOperation";
#[cfg(feature = "partial-eval")]
const V0_AUTH_PARTIAL_OP: &str = "AuthorizationPartialOperation";
const V0_VALIDATE_OP: &str = "ValidateOperation";
const V0_VALIDATE_LEVEL_OP: &str = "ValidateWithLevelOperation";
const V0_VALIDATE_ENTITIES: &str = "ValidateEntities";
const V0_STATEFUL_AUTH_OP: &str = "StatefulAuthorizationOperation";

fn stateful_is_authorized_json_str(json: &str) -> Result<String, serde_json::Error> {
    let call: StatefulAuthorizationCall = serde_json::from_str(json)?;
    let ans = stateful_is_authorized(call);
    serde_json::to_string(&ans)
}

fn call_cedar(call: &str, input: &str) -> String {
    let result = match call {
        V0_AUTH_OP => is_authorized_json_str(input),
        #[cfg(feature = "partial-eval")]
        V0_AUTH_PARTIAL_OP => is_authorized_partial_json_str(input),
        V0_VALIDATE_OP => validate_json_str(input),
        V0_VALIDATE_ENTITIES => json_validate_entities(input),
        V0_VALIDATE_LEVEL_OP => validate_with_level_json_str(input),
        V0_STATEFUL_AUTH_OP => stateful_is_authorized_json_str(input),
        _ => {
            let ires = Answer::fail_internally(format!("unsupported operation: {}", call));
            serde_json::to_string(&ires)
        }
    };
    result.unwrap_or_else(|err| {
        panic!("failed to handle call {call} with input {input}\nError: {err}")
    })
}

#[no_mangle]
pub extern "C" fn cedar_call(
    op_ptr: *const u8,
    op_len: usize,
    input_ptr: *const u8,
    input_len: usize,
) -> *const u32 {
    let op = read_str(op_ptr, op_len);
    let input = read_str(input_ptr, input_len);
    let result = call_cedar(op, input);
    return_string(&result)
}

// ── Version ────────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn cedar_version() -> *const u32 {
    return_string("4.0")
}

// ── Policy utilities ───────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn cedar_parse_policy(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Policy::from_str(input) {
        Ok(p) => return_string(&p.to_string()),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_parse_template(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Template::from_str(input) {
        Ok(t) => return_string(&t.to_string()),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_policy_effect(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Policy::from_str(input) {
        Ok(p) => return_string(&p.effect().to_string()),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_template_effect(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Template::from_str(input) {
        Ok(t) => return_string(&t.effect().to_string()),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_policy_to_json(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Policy::from_str(input) {
        Ok(p) => match serde_json::to_string(&p.to_json().unwrap()) {
            Ok(json) => return_string(&json),
            Err(e) => return_string(&format!("ERROR:{}", e)),
        },
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_policy_from_json(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match serde_json::from_str::<Value>(input) {
        Ok(json_val) => match Policy::from_json(None, json_val) {
            Ok(p) => return_string(&p.to_string()),
            Err(e) => return_string(&format!("ERROR:{}", e)),
        },
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_get_policy_annotations(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Policy::from_str(input) {
        Ok(p) => {
            let map: std::collections::HashMap<&str, &str> = p.annotations().collect();
            match serde_json::to_string(&map) {
                Ok(json) => return_string(&json),
                Err(e) => return_string(&format!("ERROR:{}", e)),
            }
        }
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_get_template_annotations(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Template::from_str(input) {
        Ok(t) => {
            let map: std::collections::HashMap<&str, &str> = t.annotations().collect();
            match serde_json::to_string(&map) {
                Ok(json) => return_string(&json),
                Err(e) => return_string(&format!("ERROR:{}", e)),
            }
        }
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_parse_policies(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match PolicySet::from_str(input) {
        Ok(ps) => {
            let policies: Vec<serde_json::Value> = ps
                .policies()
                .map(|p| {
                    serde_json::json!({
                        "id": p.id().to_string(),
                        "text": p.to_string(),
                    })
                })
                .collect();
            let templates: Vec<serde_json::Value> = ps
                .templates()
                .map(|t| {
                    serde_json::json!({
                        "id": t.id().to_string(),
                        "text": t.to_string(),
                    })
                })
                .collect();
            let result = serde_json::json!({
                "policies": policies,
                "templates": templates,
            });
            return_string(&result.to_string())
        }
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_policy_set_to_json(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match serde_json::from_str::<PolicySetFFI>(input) {
        Ok(ffi_ps) => match ffi_ps.parse() {
            Ok(ps) => match serde_json::to_string(&ps.to_json().unwrap()) {
                Ok(json) => return_string(&json),
                Err(e) => return_string(&format!("ERROR:{}", e)),
            },
            Err(errs) => {
                let msg = errs
                    .into_iter()
                    .map(|e| e.to_string())
                    .collect::<Vec<_>>()
                    .join("; ");
                return_string(&format!("ERROR:{}", msg))
            }
        },
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_format_policies(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match policies_str_to_pretty(input, &Config::default()) {
        Ok(formatted) => return_string(&formatted),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

// ── Schema utilities ───────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn cedar_parse_json_schema(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Schema::from_json_str(input) {
        Ok(_) => return_string("success"),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_parse_cedar_schema(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match Schema::from_cedarschema_str(input) {
        Ok(_) => return_string("success"),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_schema_to_cedar(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match serde_json::from_str::<FFISchema>(input) {
        Ok(schema) => match schema_to_text(schema) {
            SchemaToTextAnswer::Success { text, .. } => return_string(&text),
            SchemaToTextAnswer::Failure { errors } => {
                let msg = errors.iter().map(|e| e.message.clone()).collect::<Vec<_>>().join("; ");
                return_string(&format!("ERROR:{}", msg))
            }
        },
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_schema_to_json(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    let cedar_schema = FFISchema::Cedar(input.to_string());
    match schema_to_json(cedar_schema) {
        SchemaToJsonAnswer::Success { json, .. } => {
            match serde_json::to_string_pretty(&json) {
                Ok(s) => return_string(&s),
                Err(e) => return_string(&format!("ERROR:{}", e)),
            }
        }
        SchemaToJsonAnswer::Failure { errors } => {
            let msg = errors.iter().map(|e| e.message.clone()).collect::<Vec<_>>().join("; ");
            return_string(&format!("ERROR:{}", msg))
        }
    }
}

// ── Caching ────────────────────────────────────────────────────────────

#[no_mangle]
pub extern "C" fn cedar_preparse_policy_set(
    id_ptr: *const u8, id_len: usize,
    json_ptr: *const u8, json_len: usize,
) -> *const u32 {
    let id = read_str(id_ptr, id_len).to_string();
    let json = read_str(json_ptr, json_len);
    match serde_json::from_str::<PolicySetFFI>(json) {
        Ok(policies) => {
            let ans = preparse_policy_set(id, policies);
            return_string(&serde_json::to_string(&ans).unwrap())
        }
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_preparse_schema(
    id_ptr: *const u8, id_len: usize,
    json_ptr: *const u8, json_len: usize,
) -> *const u32 {
    let id = read_str(id_ptr, id_len).to_string();
    let json = read_str(json_ptr, json_len);
    match serde_json::from_str::<FFISchema>(json) {
        Ok(schema) => {
            let ans = preparse_schema(id, schema);
            return_string(&serde_json::to_string(&ans).unwrap())
        }
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

#[no_mangle]
pub extern "C" fn cedar_stateful_authorize(ptr: *const u8, len: usize) -> *const u32 {
    let input = read_str(ptr, len);
    match stateful_is_authorized_json_str(input) {
        Ok(result) => return_string(&result),
        Err(e) => return_string(&format!("ERROR:{}", e)),
    }
}

/// Single-crossing stateful authorization.
/// Input is already in Wasm memory at `input_ptr` (written by host via memory.write).
/// Result is written to `out_ptr` (a host-provided buffer).
/// Returns the number of bytes written to out_ptr, or negative on overflow.
#[no_mangle]
pub extern "C" fn cedar_stateful_authorize_buf(
    input_ptr: *const u8, input_len: usize,
    out_ptr: *mut u8, out_cap: usize,
) -> i32 {
    let input = read_str(input_ptr, input_len);
    let result = match stateful_is_authorized_json_str(input) {
        Ok(r) => r,
        Err(e) => format!("ERROR:{}", e),
    };
    let result_bytes = result.as_bytes();
    if result_bytes.len() > out_cap {
        return -(result_bytes.len() as i32);
    }
    unsafe {
        core::ptr::copy_nonoverlapping(result_bytes.as_ptr(), out_ptr, result_bytes.len());
    }
    result_bytes.len() as i32
}

/// Same for regular (non-cached) authorization.
#[no_mangle]
pub extern "C" fn cedar_authorize_buf(
    input_ptr: *const u8, input_len: usize,
    out_ptr: *mut u8, out_cap: usize,
) -> i32 {
    let input = read_str(input_ptr, input_len);
    let result = match is_authorized_json_str(input) {
        Ok(r) => r,
        Err(e) => format!("ERROR:{}", e),
    };
    let result_bytes = result.as_bytes();
    if result_bytes.len() > out_cap {
        return -(result_bytes.len() as i32);
    }
    unsafe {
        core::ptr::copy_nonoverlapping(result_bytes.as_ptr(), out_ptr, result_bytes.len());
    }
    result_bytes.len() as i32
}
