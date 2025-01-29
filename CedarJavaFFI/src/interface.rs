/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use cedar_policy::entities_errors::EntitiesError;
#[cfg(feature = "partial-eval")]
use cedar_policy::ffi::is_authorized_partial_json_str;
use cedar_policy::{
    ffi::{is_authorized_json_str, validate_json_str},
    Entities, EntityUid, Policy, PolicySet, Schema, Template,
};
use cedar_policy_formatter::{policies_str_to_pretty, Config};
use jni::{
    objects::{JClass, JObject, JString, JValueGen, JValueOwned},
    sys::{jstring, jvalue},
    JNIEnv,
};
use jni_fn::jni_fn;
use serde::{Deserialize, Serialize};
use serde_json::{from_str, Value};
use std::{error::Error, str::FromStr, thread};

use crate::objects::{JEntity, JFormatterConfig};
use crate::{
    answer::Answer,
    jset::Set,
    objects::{JEntityId, JEntityTypeName, JEntityUID, JPolicy, Object},
    utils::raise_npe,
};

type Result<T> = std::result::Result<T, Box<dyn Error>>;

const V0_AUTH_OP: &str = "AuthorizationOperation";
#[cfg(feature = "partial-eval")]
const V0_AUTH_PARTIAL_OP: &str = "AuthorizationPartialOperation";
const V0_VALIDATE_OP: &str = "ValidateOperation";
const V0_VALIDATE_ENTITIES: &str = "ValidateEntities";

fn build_err_obj(env: &JNIEnv<'_>, err: &str) -> jstring {
    env.new_string(
        serde_json::to_string(&Answer::fail_bad_request(vec![format!(
            "Failed {} Java string",
            err
        )]))
        .expect("could not serialise response"),
    )
    .expect("error creating Java string")
    .into_raw()
}

fn call_cedar_in_thread(call_str: String, input_str: String) -> String {
    call_cedar(&call_str, &input_str)
}

/// JNI entry point for authorization and validation requests
#[jni_fn("com.cedarpolicy.BasicAuthorizationEngine")]
pub fn callCedarJNI(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    j_call: JString<'_>,
    j_input: JString<'_>,
) -> jstring {
    let j_call_str: String = match env.get_string(&j_call) {
        Ok(call_str) => call_str.into(),
        _ => return build_err_obj(&env, "getting"),
    };

    let mut j_input_str: String = match env.get_string(&j_input) {
        Ok(s) => s.into(),
        Err(_) => return build_err_obj(&env, "parsing"),
    };
    j_input_str.push(' ');

    let handle = thread::spawn(move || call_cedar_in_thread(j_call_str, j_input_str));

    let result = match handle.join() {
        Ok(s) => s,
        Err(e) => format!("Authorization thread failed {e:?}"),
    };

    let res = env.new_string(result);
    match res {
        Ok(r) => r.into_raw(),
        _ => env
            .new_string(
                serde_json::to_string(&Answer::fail_internally(
                    "Failed creating Java string".to_string(),
                ))
                .expect("could not serialise response"),
            )
            .expect("error creating Java string")
            .into_raw(),
    }
}

/// JNI entry point to get the Cedar version
#[jni_fn("com.cedarpolicy.BasicAuthorizationEngine")]
pub fn getCedarJNIVersion(env: JNIEnv<'_>) -> jstring {
    env.new_string("4.0")
        .expect("error creating Java string")
        .into_raw()
}

pub(crate) fn call_cedar(call: &str, input: &str) -> String {
    let result = match call {
        V0_AUTH_OP => is_authorized_json_str(input),
        #[cfg(feature = "partial-eval")]
        V0_AUTH_PARTIAL_OP => is_authorized_partial_json_str(input),
        V0_VALIDATE_OP => validate_json_str(input),
        V0_VALIDATE_ENTITIES => json_validate_entities(&input),
        _ => {
            let ires = Answer::fail_internally(format!("unsupported operation: {}", call));
            serde_json::to_string(&ires)
        }
    };
    result.unwrap_or_else(|err| {
        panic!("failed to handle call {call} with input {input}\nError: {err}")
    })
}

#[derive(Serialize, Deserialize)]
struct ValidateEntityCall {
    schema: Value,
    entities: Value,
}

pub fn json_validate_entities(input: &str) -> serde_json::Result<String> {
    let ans = validate_entities(input)?;
    serde_json::to_string(&ans)
}

/// public string-based JSON interface to be invoked by FFIs. Takes in a `ValidateEntityCall` and (if successful)
/// returns unit value () which is null value when serialized to json.
pub fn validate_entities(input: &str) -> serde_json::Result<Answer> {
    let validate_entity_call = from_str::<ValidateEntityCall>(&input)?;
    match Schema::from_json_value(validate_entity_call.schema) {
        Err(e) => Ok(Answer::fail_bad_request(vec![e.to_string()])),
        Ok(schema) => {
            match Entities::from_json_value(validate_entity_call.entities, Some(&schema)) {
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
                Ok(_entities) => Ok(Answer::Success {
                    result: "null".to_string(),
                }),
            }
        }
    }
}

#[derive(Debug, Serialize, Deserialize)]
struct JavaInterfaceCall {
    pub call: String,
    arguments: String,
}

fn jni_failed(env: &mut JNIEnv<'_>, e: &dyn Error) -> jvalue {
    // If we already generated an exception, then let that go up the stack
    // Otherwise, generate a cedar InternalException and return null
    if !env.exception_check().unwrap_or_default() {
        // We have to unwrap here as we're doing exception handling
        // If we don't have the heap space to create an exception, the only valid move is ending the process
        env.throw_new(
            "com/cedarpolicy/model/exception/InternalException",
            format!("Internal JNI Error: {e}"),
        )
        .unwrap();
    }
    JValueOwned::Object(JObject::null()).as_jni()
}

/// Public string-based JSON interface to parse a schema in Cedar's JSON format
#[jni_fn("com.cedarpolicy.model.schema.Schema")]
pub fn parseJsonSchemaJni<'a>(mut env: JNIEnv<'a>, _: JClass, schema_jstr: JString<'a>) -> jvalue {
    match parse_json_schema_internal(&mut env, schema_jstr) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

/// public string-based JSON interface to parse a schema in Cedar's cedar-readable format
#[jni_fn("com.cedarpolicy.model.schema.Schema")]
pub fn parseCedarSchemaJni<'a>(mut env: JNIEnv<'a>, _: JClass, schema_jstr: JString<'a>) -> jvalue {
    match parse_cedar_schema_internal(&mut env, schema_jstr) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

fn parse_json_schema_internal<'a>(
    env: &mut JNIEnv<'a>,
    schema_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if schema_jstr.is_null() {
        raise_npe(env)
    } else {
        let schema_jstring = env.get_string(&schema_jstr)?;
        let schema_string = String::from(schema_jstring);
        match Schema::from_json_str(&schema_string) {
            Err(e) => Err(Box::new(e)),
            Ok(_) => Ok(JValueGen::Object(env.new_string("success")?.into())),
        }
    }
}

fn parse_cedar_schema_internal<'a>(
    env: &mut JNIEnv<'a>,
    schema_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if schema_jstr.is_null() {
        raise_npe(env)
    } else {
        let schema_jstring = env.get_string(&schema_jstr)?;
        let schema_string = String::from(schema_jstring);
        match Schema::from_cedarschema_str(&schema_string) {
            Err(e) => Err(Box::new(e)),
            Ok(_) => Ok(JValueGen::Object(env.new_string("success")?.into())),
        }
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn parsePolicyJni<'a>(mut env: JNIEnv<'a>, _: JClass, policy_jstr: JString<'a>) -> jvalue {
    match parse_policy_internal(&mut env, policy_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(policy_text) => policy_text.as_jni(),
    }
}

fn parse_policy_internal<'a>(
    env: &mut JNIEnv<'a>,
    policy_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if policy_jstr.is_null() {
        raise_npe(env)
    } else {
        let policy_jstring = env.get_string(&policy_jstr)?;
        let policy_string = String::from(policy_jstring);
        match Policy::from_str(&policy_string) {
            Err(e) => Err(Box::new(e)),
            Ok(p) => {
                let policy_text = format!("{}", p);
                Ok(JValueGen::Object(env.new_string(&policy_text)?.into()))
            }
        }
    }
}

#[jni_fn("com.cedarpolicy.model.policy.PolicySet")]
pub fn parsePoliciesJni<'a>(mut env: JNIEnv<'a>, _: JClass, policies_jstr: JString<'a>) -> jvalue {
    match parse_policies_internal(&mut env, policies_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(policies_set) => policies_set.as_jni(),
    }
}

fn parse_policies_internal<'a>(
    env: &mut JNIEnv<'a>,
    policies_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if policies_jstr.is_null() {
        raise_npe(env)
    } else {
        // Parse the string into the Rust PolicySet
        let policies_jstring = env.get_string(&policies_jstr)?;
        let policies_string = String::from(policies_jstring);
        let policy_set = PolicySet::from_str(&policies_string)?;

        // Enumerate over the parsed policies
        let mut policies_java_hash_set = Set::new(env)?;
        for policy in policy_set.policies() {
            let policy_id = format!("{}", policy.id());
            let policy_text = format!("{}", policy);
            let java_policy_object = JPolicy::new(
                env,
                &env.new_string(&policy_text)?,
                &env.new_string(&policy_id)?,
            )?;
            let _ = policies_java_hash_set.add(env, java_policy_object);
        }

        let mut templates_java_hash_set = Set::new(env)?;
        for template in policy_set.templates() {
            let policy_id = format!("{}", template.id());
            let policy_text = format!("{}", template);
            let java_policy_object = JPolicy::new(
                env,
                &env.new_string(&policy_text)?,
                &env.new_string(&policy_id)?,
            )?;
            let _ = templates_java_hash_set.add(env, java_policy_object);
        }

        let java_policy_set = create_java_policy_set(
            env,
            policies_java_hash_set.as_ref(),
            templates_java_hash_set.as_ref(),
        );

        Ok(JValueGen::Object(java_policy_set))
    }
}

fn create_java_policy_set<'a>(
    env: &mut JNIEnv<'a>,
    policies_java_hash_set: &JObject<'a>,
    templates_java_hash_set: &JObject<'a>,
) -> JObject<'a> {
    env.new_object(
        "com/cedarpolicy/model/policy/PolicySet",
        "(Ljava/util/Set;Ljava/util/Set;)V",
        &[
            JValueGen::Object(policies_java_hash_set),
            JValueGen::Object(templates_java_hash_set),
        ],
    )
    .expect("Failed to create new PolicySet object")
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn parsePolicyTemplateJni<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    template_jstr: JString<'a>,
) -> jvalue {
    match parse_policy_template_internal(&mut env, template_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(template_text) => template_text.as_jni(),
    }
}

fn parse_policy_template_internal<'a>(
    env: &mut JNIEnv<'a>,
    template_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if template_jstr.is_null() {
        raise_npe(env)
    } else {
        let template_jstring = env.get_string(&template_jstr)?;
        let template_string = String::from(template_jstring);
        match Template::from_str(&template_string) {
            Err(e) => Err(Box::new(e)),
            Ok(template) => {
                let template_text = template.to_string();
                Ok(JValueGen::Object(env.new_string(&template_text)?.into()))
            }
        }
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn toJsonJni<'a>(mut env: JNIEnv<'a>, _: JClass, policy_jstr: JString<'a>) -> jvalue {
    match to_json_internal(&mut env, policy_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(policy_json) => policy_json.as_jni(),
    }
}

fn to_json_internal<'a>(env: &mut JNIEnv<'a>, policy_jstr: JString<'a>) -> Result<JValueOwned<'a>> {
    if policy_jstr.is_null() {
        raise_npe(env)
    } else {
        let policy_jstring = env.get_string(&policy_jstr)?;
        let policy_string = String::from(policy_jstring);
        let policy = Policy::from_str(&policy_string)?;
        let policy_json = serde_json::to_string(&policy.to_json().unwrap())?;
        Ok(JValueGen::Object(env.new_string(&policy_json)?.into()))
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn policyEffectJni<'a>(mut env: JNIEnv<'a>, _: JClass, policy_jstr: JString<'a>) -> jvalue {
    match policy_effect_jni_internal(&mut env, policy_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(effect) => effect.as_jni(),
    }
}

fn policy_effect_jni_internal<'a>(
    env: &mut JNIEnv<'a>,
    policy_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if policy_jstr.is_null() {
        raise_npe(env)
    } else {
        let policy_jstring = env.get_string(&policy_jstr)?;
        let policy_string = String::from(policy_jstring);
        let policy = Policy::from_str(&policy_string)?;
        let policy_effect = policy.effect().to_string();
        Ok(JValueGen::Object(env.new_string(&policy_effect)?.into()))
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn templateEffectJni<'a>(mut env: JNIEnv<'a>, _: JClass, policy_jstr: JString<'a>) -> jvalue {
    match template_effect_jni_internal(&mut env, policy_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(effect) => effect.as_jni(),
    }
}

fn template_effect_jni_internal<'a>(
    env: &mut JNIEnv<'a>,
    policy_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if policy_jstr.is_null() {
        raise_npe(env)
    } else {
        let policy_jstring = env.get_string(&policy_jstr)?;
        let policy_string = String::from(policy_jstring);
        let policy = Template::from_str(&policy_string)?;
        let policy_effect = policy.effect().to_string();
        Ok(JValueGen::Object(env.new_string(&policy_effect)?.into()))
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn fromJsonJni<'a>(mut env: JNIEnv<'a>, _: JClass, policy_json_jstr: JString<'a>) -> jvalue {
    match from_json_internal(&mut env, policy_json_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(policy_text) => policy_text.as_jni(),
    }
}

fn from_json_internal<'a>(
    env: &mut JNIEnv<'a>,
    policy_json_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if policy_json_jstr.is_null() {
        raise_npe(env)
    } else {
        let policy_json_jstring = env.get_string(&policy_json_jstr)?;
        let policy_json_string = String::from(policy_json_jstring);
        let policy_json_value: Value = serde_json::from_str(&policy_json_string)?;
        match Policy::from_json(None, policy_json_value) {
            Err(e) => Err(Box::new(e)),
            Ok(p) => {
                let policy_text = format!("{}", p);
                Ok(JValueGen::Object(env.new_string(&policy_text)?.into()))
            }
        }
    }
}

#[jni_fn("com.cedarpolicy.model.entity.Entity")]
pub fn toJsonEntityJni<'a>(mut env: JNIEnv<'a>, _: JClass, entity: JEntity<'a>) -> jvalue {
    match to_json_entity_internal(&mut env, entity) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

fn to_json_entity_internal<'a>(
    env: &mut JNIEnv<'a>,
    java_entity: JEntity<'a>,
) -> Result<JValueOwned<'a>> {
    if java_entity.as_ref().is_null() {
        raise_npe(env)
    } else {
        let entity = java_entity.to_entity(env)?;
        let entity_json = serde_json::to_string(&entity.to_json_value()?)?;
        Ok(JValueGen::Object(env.new_string(&entity_json)?.into()))
    }
}

#[jni_fn("com.cedarpolicy.value.EntityIdentifier")]
pub fn getEntityIdentifierRepr<'a>(mut env: JNIEnv<'a>, _: JClass, obj: JObject<'a>) -> jvalue {
    match get_entity_identifier_repr_internal(&mut env, obj) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

fn get_entity_identifier_repr_internal<'a>(
    env: &mut JNIEnv<'a>,
    obj: JObject<'a>,
) -> Result<JValueOwned<'a>> {
    if obj.is_null() {
        raise_npe(env)
    } else {
        let eid = JEntityId::cast(env, obj)?;
        let repr = eid.get_string_repr();
        let jstring = env.new_string(repr)?.into();
        Ok(JValueGen::Object(jstring))
    }
}

#[jni_fn("com.cedarpolicy.value.EntityTypeName")]
pub fn parseEntityTypeName<'a>(mut env: JNIEnv<'a>, _: JClass, obj: JString<'a>) -> jvalue {
    match parse_entity_type_name_internal(&mut env, obj) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

pub fn parse_entity_type_name_internal<'a>(
    env: &mut JNIEnv<'a>,
    obj: JString<'a>,
) -> Result<JValueGen<JObject<'a>>> {
    if obj.is_null() {
        raise_npe(env)
    } else {
        let jstring = env.get_string(&obj)?;
        let src = String::from(jstring);
        JEntityTypeName::parse(env, &src).map(Into::into)
    }
}

#[jni_fn("com.cedarpolicy.value.EntityTypeName")]
pub fn getEntityTypeNameRepr<'a>(mut env: JNIEnv<'a>, _: JClass, obj: JObject<'a>) -> jvalue {
    match get_entity_type_name_repr_internal(&mut env, obj) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

fn get_entity_type_name_repr_internal<'a>(
    env: &mut JNIEnv<'a>,
    obj: JObject<'a>,
) -> Result<JValueOwned<'a>> {
    if obj.is_null() {
        raise_npe(env)
    } else {
        let etype = JEntityTypeName::cast(env, obj)?;
        let repr = etype.get_string_repr();
        Ok(env.new_string(repr)?.into())
    }
}

#[jni_fn("com.cedarpolicy.value.EntityUID")]
pub fn parseEntityUID<'a>(mut env: JNIEnv<'a>, _: JClass, obj: JString<'a>) -> jvalue {
    let r = match parse_entity_uid_internal(&mut env, obj) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    };
    r
}

fn parse_entity_uid_internal<'a>(
    env: &mut JNIEnv<'a>,
    obj: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if obj.is_null() {
        raise_npe(env)
    } else {
        let jstring = env.get_string(&obj)?;
        let src = String::from(jstring);
        let obj = JEntityUID::parse(env, &src)?;
        Ok(obj.into())
    }
}

#[jni_fn("com.cedarpolicy.value.EntityUID")]
pub fn getEUIDRepr<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    type_name: JObject<'a>,
    id: JObject<'a>,
) -> jvalue {
    let r = match get_euid_repr_internal(&mut env, type_name, id) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    };
    r
}

fn get_euid_repr_internal<'a>(
    env: &mut JNIEnv<'a>,
    type_name: JObject<'a>,
    id: JObject<'a>,
) -> Result<JValueOwned<'a>> {
    if type_name.is_null() || id.is_null() {
        raise_npe(env)
    } else {
        let etype = JEntityTypeName::cast(env, type_name)?.get_rust_repr();
        let id = JEntityId::cast(env, id)?.get_rust_repr();
        let euid = EntityUid::from_type_name_and_id(etype, id);
        let jstring = env.new_string(euid.to_string())?;
        Ok(jstring.into())
    }
}

#[jni_fn("com.cedarpolicy.formatter.PolicyFormatter")]
pub fn policiesStrToPretty<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    policies_jstr: JString<'a>,
) -> jvalue {
    match policies_str_to_pretty_internal(&mut env, policies_jstr, None) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

#[jni_fn("com.cedarpolicy.formatter.PolicyFormatter")]
pub fn policiesStrToPrettyWithConfig<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    policies_jstr: JString<'a>,
    config_obj: JObject<'a>,
) -> jvalue {
    match policies_str_to_pretty_internal(&mut env, policies_jstr, Some(config_obj)) {
        Ok(v) => v.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

fn policies_str_to_pretty_internal<'a>(
    env: &mut JNIEnv<'a>,
    policies_jstr: JString<'a>,
    config_obj: Option<JObject<'a>>,
) -> Result<JValueOwned<'a>> {
    if policies_jstr.is_null() || config_obj.as_ref().is_some_and(|obj| obj.is_null()) {
        raise_npe(env)
    } else {
        let config = if let Some(obj) = config_obj {
            JFormatterConfig::cast(env, obj)?.get_rust_repr()
        } else {
            Config::default()
        };
        let policies_str = String::from(env.get_string(&policies_jstr)?);
        match policies_str_to_pretty(&policies_str, &config) {
            Ok(formatted_policies) => Ok(env.new_string(formatted_policies)?.into()),
            Err(e) => Err(e.into()),
        }
    }
}
