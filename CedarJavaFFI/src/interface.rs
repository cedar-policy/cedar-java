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
#[cfg(feature = "partial-eval")]
use cedar_policy::ffi::is_authorized_partial_json_str;
use cedar_policy::ffi::Schema as FFISchema;
use cedar_policy::{
    entities_errors::EntitiesError,
    ffi::{schema_to_json, schema_to_text, SchemaToJsonAnswer, SchemaToTextAnswer},
};

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

use crate::objects::JFormatterConfig;
use crate::{
    answer::Answer,
    jmap::Map,
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
pub fn getPolicyAnnotationsJni<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    policy_jstr: JString<'a>,
) -> jvalue {
    match get_policy_annotations_internal(&mut env, policy_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(annotations) => annotations.as_jni(),
    }
}

pub fn get_policy_annotations_internal<'a>(
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
            Ok(policy) => {
                let java_map = create_java_map_from_annotations(env, policy.annotations());
                Ok(JValueGen::Object(java_map))
            }
        }
    }
}

#[jni_fn("com.cedarpolicy.model.policy.Policy")]
pub fn getTemplateAnnotationsJni<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    template_jstr: JString<'a>,
) -> jvalue {
    match get_template_annotations_internal(&mut env, template_jstr) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(annotations) => annotations.as_jni(),
    }
}

pub fn get_template_annotations_internal<'a>(
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
                let java_map = create_java_map_from_annotations(env, template.annotations());
                Ok(JValueGen::Object(java_map))
            }
        }
    }
}

fn create_java_map_from_annotations<'a, 'b>(
    env: &mut JNIEnv<'a>,
    annotations: impl Iterator<Item = (&'b str, &'b str)>,
) -> JObject<'a> {
    let mut map = Map::new(env).unwrap();

    for (annotation_key, annotation_value) in annotations {
        let key: JString = env.new_string(annotation_key).unwrap().into();
        let value: JString = env.new_string(annotation_value).unwrap().into();
        map.put(env, key, value).unwrap();
    }

    map.into_inner()
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
#[jni_fn("com.cedarpolicy.model.schema.Schema")]
pub fn jsonToCedarJni<'a>(mut env: JNIEnv<'a>, _: JClass, json_schema: JString<'a>) -> jvalue {
    match get_cedar_schema_internal(&mut env, json_schema) {
        Ok(val) => val.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}
pub fn get_cedar_schema_internal<'a>(
    env: &mut JNIEnv<'a>,
    schema_json_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    let rust_str = env.get_string(&schema_json_jstr)?;
    let schema_str = rust_str.to_str()?;

    let schema: FFISchema = serde_json::from_str(schema_str)?;
    let cedar_format = schema_to_text(schema);

    match cedar_format {
        SchemaToTextAnswer::Success { text, warnings } => 
        {
            let jstr = env.new_string(&text)?;
            Ok(JValueGen::Object(JObject::from(jstr)).into())
        },
        SchemaToTextAnswer::Failure { errors } => {
            let joined_errors = errors
                .iter()
                .map(|e| e.message.clone())
                .collect::<Vec<_>>()
                .join("; ");
            Err(joined_errors.into())
        }
    }
}

#[jni_fn("com.cedarpolicy.model.schema.Schema")]
pub fn cedarToJsonJni<'a>(mut env: JNIEnv<'a>, _: JClass, cedar_schema: JString<'a>) -> jvalue {
    match get_json_schema_internal(&mut env, cedar_schema) {
        Ok(val) => val.as_jni(),
        Err(e) => jni_failed(&mut env, e.as_ref()),
    }
}

pub fn get_json_schema_internal<'a>(
    env: &mut JNIEnv<'a>, 
    cedar_schema_jstr: JString<'a>
) -> Result<JValueOwned<'a>> {
    let schema_jstr = env.get_string(&cedar_schema_jstr)?;
    let schema_str = schema_jstr.to_str()?;
    let cedar_schema_str = FFISchema::Cedar(schema_str.into());
    let json_format = schema_to_json(cedar_schema_str);

    match json_format {
        SchemaToJsonAnswer::Success { json, warnings: _ } => {
            let json_pretty = serde_json::to_string_pretty(&json)?;
            let jstr = env.new_string(&json_pretty)?;
            Ok(JValueGen::Object(JObject::from(jstr)).into())
        }
        SchemaToJsonAnswer::Failure { errors } => {
            let joined_errors = errors
                .iter()
                .map(|e| e.message.clone())
                .collect::<Vec<_>>()
                .join("; ");
            Err(joined_errors.into())
        }
    }
}                 


#[cfg(test)]
pub(crate) mod jvm_based_tests {
    use super::*;
    use crate::jvm_test_utils::*;
    use jni::JavaVM;
    use std::sync::LazyLock;

    pub(crate) static JVM: LazyLock<JavaVM> = LazyLock::new(|| create_jvm().unwrap());
    // Static JVM to be used by all the tests. LazyLock for thread-safe lazy initialization

    mod policy_tests {
        use std::result;

        use cedar_policy::Effect;

        use super::*;

        #[track_caller]
        fn policy_effect_test_util(env: &mut JNIEnv, policy: &str, expected_effect: &str) {
            let policy_string = env.new_string(policy).unwrap();
            let effect_result = policy_effect_jni_internal(env, policy_string).unwrap();
            let effect_jstr = JString::cast(env, effect_result.l().unwrap()).unwrap();
            let effect = String::from(env.get_string(&effect_jstr).unwrap());
            assert_eq!(effect, expected_effect);
        }

        #[test]
        fn policy_effect_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            policy_effect_test_util(&mut env, "permit(principal,action,resource);", "permit");
            policy_effect_test_util(&mut env, "forbid(principal,action,resource);", "forbid");
        }
        #[test]
        fn policy_effect_jni_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_obj = JObject::null();
            let result = policy_effect_jni_internal(&mut env, null_obj.into());
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }

        #[track_caller]
        fn assert_id_annotation_eq(
            env: &mut JNIEnv,
            annotations: &JObject,
            annotation_key: &str,
            expected_annotation_value: &str,
        ) {
            let annotation_key_jstr = env.new_string(annotation_key).unwrap();
            let actual_annotation_value_obj = env
                .call_method(
                    annotations,
                    "get",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    &[JValueGen::Object(annotation_key_jstr.as_ref())],
                )
                .unwrap()
                .l()
                .unwrap();

            let actual_annotation_value_jstr =
                JString::cast(env, actual_annotation_value_obj).unwrap();
            let actual_annotation_value_str =
                String::from(env.get_string(&actual_annotation_value_jstr).unwrap());

            assert_eq!(
                actual_annotation_value_str, expected_annotation_value,
                "Returned annotation value should match the annotation in the policy."
            )
        }

        #[test]
        fn static_policy_annotations_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            let policy_string = env
                .new_string("@id(\"policyID1\") @myAnnotationKey(\"myAnnotatedValue\") permit(principal,action,resource);")
                .unwrap();
            let annotations = get_policy_annotations_internal(&mut env, policy_string)
                .unwrap()
                .l()
                .unwrap();

            assert_id_annotation_eq(&mut env, &annotations, "id", "policyID1");
            assert_id_annotation_eq(
                &mut env,
                &annotations,
                "myAnnotationKey",
                "myAnnotatedValue",
            );
        }

        #[test]
        fn template_policy_annotations_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            let policy_string = env
                .new_string("@id(\"policyID1\") @myAnnotationKey(\"myAnnotatedValue\") permit(principal==?principal,action,resource);")
                .unwrap();
            let annotations = get_template_annotations_internal(&mut env, policy_string)
                .unwrap()
                .l()
                .unwrap();

            assert_id_annotation_eq(&mut env, &annotations, "id", "policyID1");
            assert_id_annotation_eq(
                &mut env,
                &annotations,
                "myAnnotationKey",
                "myAnnotatedValue",
            );
        }
        #[test]
        fn get_template_annotations_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_obj = JObject::null();
            let result = get_template_annotations_internal(&mut env, null_obj.into());
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected java exception due to a null input"
            );
        }
        #[test]
        fn parse_policy_internal_success_basic() {
            let mut env = JVM.attach_current_thread().unwrap();
            let input = r#"permit(principal,action,resource);"#;
            let policy_jstr = env.new_string(input).unwrap();

            let result = parse_policy_internal(&mut env, policy_jstr);
            assert!(
                result.is_ok(),
                "Expected parse_policy_internal to succeed: {:?}",
                result
            );

            let jvalue = result.unwrap();
            let parsed_jstring = JString::cast(&mut env, jvalue.l().unwrap()).unwrap();
            let actual_parsed_string = String::from(env.get_string(&parsed_jstring).unwrap());
            let expected_policy_object = cedar_policy::Policy::from_str(input).unwrap();
            let expected_canonical_string = expected_policy_object.to_string();

            assert_eq!(
                actual_parsed_string, expected_canonical_string,
                "Parsed policy string should match the expected canonical format."
            );
        }

        #[test]
        fn parse_policy_template_internal_invalid_missing_template_slots() {
            let mut env = JVM.attach_current_thread().unwrap();
            let input = r#"permit(principal == User::"alice", action == Action::"read", resource == Resource::"file");"#;
            let jstr = env.new_string(input).unwrap();

            let result = parse_policy_template_internal(&mut env, jstr);

            assert!(
                result.is_err(),
                "Expected parse_policy_template_internal to fail due to missing template slots"
            );
        }
        #[test]
        fn get_policy_annotations_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_obj = JObject::null();
            let result = get_policy_annotations_internal(&mut env, null_obj.into());
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected java exception due to a null input"
            );
        }
        #[test]
        fn parse_policy_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = parse_policy_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
        #[test]
        fn parse_policy_template_valid_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let policy_template = r#"permit(principal==?principal,action == Action::"readfile",resource==?resource );"#;
            let jstr = env.new_string(policy_template).unwrap();
            let result = parse_policy_template_internal(&mut env, jstr);
            assert!(result.is_ok());

            let jvalue = result.unwrap();
            let parsed_jstring = JString::cast(&mut env, jvalue.l().unwrap()).unwrap();
            let actual_parsed_string = String::from(env.get_string(&parsed_jstring).unwrap());
            let expected_template_object =
                cedar_policy::Template::from_str(policy_template).unwrap();
            let expected_canonical_string = expected_template_object.to_string();

            assert_eq!(
                actual_parsed_string, expected_canonical_string,
                "Parsed template string should match the expected canonical format."
            );
        }

        #[test]
        fn parse_policy_template_invalid_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_input = r#"permit(Principa,Action,Resource );"#;
            let jstr = env.new_string(invalid_input).unwrap();
            let result = parse_policy_template_internal(&mut env, jstr);
            assert!(result.is_err(), "Expected to fail for invalid input");
        }
        #[test]
        fn parse_policy_template_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = parse_policy_template_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
        #[test]
        fn from_json_test_valid() {
            let mut env = JVM.attach_current_thread().unwrap();

            let policy_json = r#"
    {
        "effect": "permit",
        "principal": {
            "op": "==",
            "entity": { "type": "User", "id": "12UA45" }
        },
        "action": {
            "op": "==",
            "entity": { "type": "Action", "id": "view" }
        },
        "resource": {
            "op": "in",
            "entity": { "type": "Folder", "id": "abc" }
        },
        "conditions": [
            {
                "kind": "when",
                "body": {
                    "==": {
                        "left": {
                            ".": {
                                "left": { "Var": "context" },
                                "attr": "tls_version"
                            }
                        },
                        "right": { "Value": "1.3" }
                    }
                }
            }
        ]
    }
    "#;

            let java_str = env.new_string(policy_json).unwrap();
            let result = from_json_internal(&mut env, java_str);
            assert!(
                result.is_ok(),
                "Expected from_json parsing to succeed, got: {:?}",
                result
            );

            let java_val = result.unwrap();
            let obj = java_val.l().unwrap();
            let policy_str: String = env.get_string(&obj.into()).unwrap().into();

            let policy_json_value: serde_json::Value = serde_json::from_str(policy_json).unwrap();
            let expected_policy = cedar_policy::Policy::from_json(None, policy_json_value).unwrap();
            let expected_policy_str = expected_policy.to_string();

            assert_eq!(
                policy_str, expected_policy_str,
                "Parsed policy string should match the expected Cedar policy format"
            );
        }
        #[test]
        fn from_json_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = from_json_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }

        #[test]
        fn from_json_invalid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_input = r#"
        {
            "Effect": "permit",
            "Principal": {
                "op": "==",
                "Entity": { "type": "User", "id": "12UA45" }
            },
            "Action": {
                "op": "==",
                "entity": { "type": "Action", "id": "view" }
            },
            "Resource": {
                "op": "in",
                "entity": { "type": "Folder", "id": "abc" }
            },
            "Conditions": [
                {
                    "kind": "when",
                    "body": {
                        "==": {
                            "left": {
                                ".": {
                                    "left": {
                                        "Var": "context"
                                    },
                                    "attr": "tls_version"
                                }
                            },
                            "right": {
                                "Value": "1.3"
                            }
                        }
                    }
                }
            ]
        }
        "#;

            let java_str = env.new_string(invalid_input).unwrap();
            let result = from_json_internal(&mut env, java_str);
            assert!(
                result.is_err(),
                "Expected json parsing to fail: {:?}",
                result
            );
        }

        #[test]
        fn to_json_internal_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let input = r#"permit(principal, action, resource);"#;
            let jstr = env.new_string(input).unwrap();
            let result = to_json_internal(&mut env, jstr);

            assert!(result.is_ok(), "Expected to_json to succeed");

            let jval = result.unwrap();
            let obj = jval.l().unwrap();
            let actual_json_str: String = env.get_string(&obj.into()).unwrap().into();

            let expected_policy = cedar_policy::Policy::from_str(input).unwrap();
            let expected_json_str =
                serde_json::to_string(&expected_policy.to_json().unwrap()).unwrap();

            assert_eq!(
                actual_json_str, expected_json_str,
                "Generated JSON should match expected policy JSON format"
            );
        }

        #[test]
        fn to_json_internal_invalid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_input = r#"Permit(Principal, Resource, Action);"#;
            let jstr = env.new_string(invalid_input).unwrap();

            let result = from_json_internal(&mut env, jstr);
            assert!(
                result.is_err(),
                "Expected json_internal parsing to fail: {:?}",
                result
            );
        }
        #[test]
        fn to_json_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = to_json_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
        #[test]
        fn template_effect_jni_internal_permit_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let template_policy = r#"permit(principal==?principal,action == Action::"readfile",resource==?resource );"#;

            let jstr = env.new_string(template_policy).unwrap();
            let result = template_effect_jni_internal(&mut env, jstr);
            assert!(result.is_ok());

            let jvalue = result.unwrap();
            let jstring = JString::cast(&mut env, jvalue.l().unwrap()).unwrap();
            let effect = String::from(env.get_string(&jstring).unwrap());
            assert_eq!(effect, "permit");
        }

        #[test]
        fn template_effect_jni_internal_forbid_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let cedar_policy = r#"forbid(principal==?principal,action == Action::"readfile",resource==?resource );"#;
            let jstr = env.new_string(cedar_policy).unwrap();

            let result = template_effect_jni_internal(&mut env, jstr);
            assert!(result.is_ok());

            let jvalue = result.unwrap();
            let jstring = JString::cast(&mut env, jvalue.l().unwrap()).unwrap();
            let effect = String::from(env.get_string(&jstring).unwrap());

            assert_eq!(effect, "forbid");
        }

        #[test]
        fn template_effect_jni_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_obj = JObject::null();
            let result = template_effect_jni_internal(&mut env, null_obj.into());
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
    }
    mod map_tests {
        use super::*;

        #[test]
        fn map_new_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            let java_hash_map = Map::<JString, JString>::new(&mut env);

            assert!(java_hash_map.is_ok(), "Map creation should succeed");

            assert!(
                env.is_instance_of(java_hash_map.unwrap().into_inner(), "java/util/HashMap")
                    .unwrap(),
                "Object should be a HashMap instance."
            );
        }

        #[test]
        fn map_put_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            let mut java_hash_map = Map::<JString, JString>::new(&mut env).unwrap();

            let key = env.new_string("test_key").unwrap();
            let value = env.new_string("test_value").unwrap();

            let result = java_hash_map.put(&mut env, key, value);

            assert!(result.is_ok(), "Map put should succeed.");

            let new_key = env.new_string("test_key").unwrap();
            let new_value = env.new_string("updated_value").unwrap();

            let update_result = java_hash_map.put(&mut env, new_key, new_value);

            assert!(result.is_ok(), "Map put should succeed.");

            let update_result_jstr = JString::cast(&mut env, update_result.unwrap()).unwrap();
            let update_result_str = String::from(env.get_string(&update_result_jstr).unwrap());

            assert_eq!(
                update_result_str, "test_value",
                "Value returned from map update should match the original value of test_key."
            )
        }

        #[test]
        fn map_get_tests() {
            let mut env = JVM.attach_current_thread().unwrap();
            let mut java_hash_map = Map::<JString, JString>::new(&mut env).unwrap();

            let key = env.new_string("test_key").unwrap();
            let value = env.new_string("test_value").unwrap();

            let _ = java_hash_map.put(&mut env, key, value);

            let retrieval_key = env.new_string("test_key").unwrap();
            let retrieved_value = java_hash_map.get(&mut env, retrieval_key).unwrap();

            let retrieved_value_jstr = JString::cast(&mut env, retrieved_value).unwrap();
            let retrieved_value_str = String::from(env.get_string(&retrieved_value_jstr).unwrap());

            assert_eq!(
                retrieved_value_str, "test_value",
                "Retrieved value should be equal to the inserted value."
            )
        }
    }
    mod schema_test {
        use std::result;

        use super::*;
        use cedar_policy::{EntityId, Schema};

        #[test]
        fn parse_json_schema_internal_valid_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let input = r#"{
    "schema": {
        "entityTypes": {
            "User": {
                "memberOfTypes": ["Group"]
            },
            "Group": {},
            "File": {}
        },
        "actions": {
            "read": {
                "appliesTo": {
                    "principalTypes": ["User"],
                    "resourceTypes": ["File"]
                }
            }
        }
    }
}"#;
            let jstr = env.new_string(input).unwrap();
            let result = parse_json_schema_internal(&mut env, jstr);
            assert!(result.is_ok(), "Expected schema to parse successfully");

            let output = result.unwrap();
            let jstring_obj = output.l().unwrap();
            let jstring: jni::objects::JString = JString::from(jstring_obj);
            let rust_output: String = env.get_string(&jstring).unwrap().into();
            assert_eq!(rust_output, "success");
        }

        #[test]
        fn parse_json_schema_internal_invalid_test() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_input = r#"{
    "Schema": {
        "entityTypes": {
            "User": {
                "MemberOfTypes": ["Group"]
            },
            "Group": {},
            "File": {}
        },
        "Actions": {
            "read": {
                "AppliesTo": {
                    "principalTypes": ["User"],
                    "AesourceTypes": ["File"]
                }
            }
        }
    }
}
    "#;

            let jstr = env.new_string(invalid_input).unwrap();
            let result = parse_json_schema_internal(&mut env, jstr);
            assert!(
                result.is_err(),
                "Expected json_schema_internal parsing to fail: {:?}",
                result
            );
        }
        #[test]
        fn parse_json_schema_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = parse_json_schema_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
        #[test]
        fn parse_cedar_schema_internal_invalid() {
            let mut env = JVM.attach_current_thread().unwrap();

            let invalid_input = "Not a valid input";
            let schema_jstr = env.new_string(invalid_input).unwrap();
            let result = parse_cedar_schema_internal(&mut env, schema_jstr);
            assert!(
                result.is_err(),
                "Expected parse_cedar_schema_internal to fail"
            );
        }
        #[test]
        fn parse_cedar_schema_internal_valid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let input = r#"
            entity User = {
                name: String,
                age?: Long,
            };
            entity Photo in Album;
            entity Album;
            action view appliesTo {
                principal : [User],
                resource: [Album,Photo]
            };
            "#;

            let schema_jstr = env.new_string(input).unwrap();
            let result = parse_cedar_schema_internal(&mut env, schema_jstr);

            assert!(
                result.is_ok(),
                "Expected parse_cedar_schema_internal to succeed"
            );

            let jvalue = result.unwrap();
            let parsed_jstring = JString::cast(&mut env, jvalue.l().unwrap()).unwrap();
            let parsed_string = String::from(env.get_string(&parsed_jstring).unwrap());
            assert_eq!(parsed_string, "success");
        }
        #[test]
        fn parse_cedar_schema_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = parse_cedar_schema_internal(&mut env, null_str);
            assert!(result.is_ok(), "Expected error on null input");
            assert!(
                env.exception_check().unwrap(),
                "Expected Java exception due to a null input"
            );
        }
        #[test]
        fn test_get_template_annotations_internal_invalid_template() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_template = "invalid template syntax";
            let jstr = env.new_string(invalid_template).unwrap();

            let result = get_template_annotations_internal(&mut env, jstr);
            assert!(
                result.is_err(),
                "Expected error for invalid template syntax"
            );
        }
    }
    mod conversion_tests {
        use super::*;
        
        #[test]
        fn get_cedar_schema_internal_valid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let json_input = r#"{
    "schema": {
        "entityTypes": {
            "User": {
                "memberOfTypes": ["Group"]
            },
            "Group": {},
            "File": {}
        },
        "actions": {
            "read": {
                "appliesTo": {
                    "principalTypes": ["User"],
                    "resourceTypes": ["File"]
                }
            }
        }
    }
}"#;

            let jstr = env.new_string(json_input).unwrap();
            let result = get_cedar_schema_internal(&mut env, jstr);
            assert!(result.is_ok(), "Expected Cedar conversion to succeed");
            
            let cedar_jval = result.unwrap();
            let cedar_jstr = JString::cast(&mut env, cedar_jval.l().unwrap()).unwrap();
            let cedar_str = String::from(env.get_string(&cedar_jstr).unwrap());
           
            assert!(cedar_str.contains("User"), "Expected output to contain 'User'");
            assert!(cedar_str.contains("Group"), "Expected output to contain 'Group'");
            assert!(cedar_str.contains("File"), "Expected output to contain 'File'");
            assert!(cedar_str.contains("read"), "Expected output to contain 'read'");
        }

        #[test]
        fn get_cedar_schema_internal_invalid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let json_input = r#"
        
            entity User = {
                        name: String,
                        age?: Long,
                    };
                    entity Photo in Album;
                    entity Album;
                    action view appliesTo {
                        principal : [User],
                        resource: [Album,Photo]
                    };
            "#;

            let jstr = env.new_string(json_input).unwrap();
            let result = get_cedar_schema_internal(&mut env, jstr);
            assert!(
                result.is_err(),
                "Expected get_cedar_schema_internal to fail {:?}",
                result
            );
        }
        
        #[test]
        fn get_cedar_schema_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = get_cedar_schema_internal(&mut env, null_str);
            assert!(result.is_err(), "Expected error on null input");
        }
        
        #[test]
        fn get_json_schema_internal_valid() {
            let mut env = JVM.attach_current_thread().unwrap();
            let cedar_input = r#"
        entity User = {
            name: String,
            age?: Long,
        };
        entity Photo in Album;
        entity Album;
        action view appliesTo {
            principal : [User],
            resource: [Album,Photo]
        }; 
    "#;

            let jstr = env.new_string(cedar_input).unwrap();
            let result = get_json_schema_internal(&mut env, jstr);
            assert!(result.is_ok(), "Expected JSON conversion to succeed");

            let json_jval = result.unwrap();
            let json_jstr = JString::cast(&mut env, json_jval.l().unwrap()).unwrap();
            let json_str = String::from(env.get_string(&json_jstr).unwrap());
            
            assert!(json_str.contains("\"entityTypes\""), "Expected output to contain 'entityTypes'");
            assert!(json_str.contains("\"User\""), "Expected output to contain 'User'");
            assert!(json_str.contains("\"Album\""), "Expected output to contain 'Album'");
            assert!(json_str.contains("\"Photo\""), "Expected output to contain 'Photo'");
            assert!(json_str.contains("\"actions\""), "Expected output to contain 'actions'");
            assert!(json_str.contains("\"view\""), "Expected output to contain 'view'");
        }
        
        #[test]
        fn get_json_schema_internal_invalid_input() {
            let mut env = JVM.attach_current_thread().unwrap();
            let invalid_cedar = "this is not cedar schema";
            let jstr = env.new_string(invalid_cedar).unwrap();

            let result = get_json_schema_internal(&mut env, jstr);
            assert!(
                result.is_err(),
                "Expected get_json_schema_internal to fail: {:?}",
                result
            );
        }
        
        #[test]
        fn get_json_schema_internal_null() {
            let mut env = JVM.attach_current_thread().unwrap();
            let null_str = JString::from(JObject::null());
            let result = get_json_schema_internal(&mut env, null_str);
            assert!(result.is_err(), "Expected error on null input");
        }
}
}
