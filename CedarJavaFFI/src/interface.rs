/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
use cedar_policy::{
    ffi::{is_authorized_json_str, validate_json_str},
    EntityUid, Policy, PolicyId, PolicySet, Schema, SlotId, Template,
};
use jni::{
    objects::{JClass, JObject, JString, JValueGen, JValueOwned},
    sys::{jstring, jvalue},
    JNIEnv,
};
use jni_fn::jni_fn;
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, error::Error, panic::catch_unwind, str::FromStr};

use crate::{
    answer::Answer,
    objects::{JEntityId, JEntityTypeName, JEntityUID, Object},
    utils::raise_npe,
};

type Result<T> = std::result::Result<T, Box<dyn Error>>;

const V0_AUTH_OP: &str = "AuthorizationOperation";
#[cfg(feature = "partial-eval")]
const V0_AUTH_PARTIAL_OP: &str = "AuthorizationPartialOperation";
const V0_VALIDATE_OP: &str = "ValidateOperation";

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

    let result = match catch_unwind(|| call_cedar(&j_call_str, &j_input_str)) {
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
        _ => {
            let ires = Answer::fail_internally(format!("unsupported operation: {}", call));
            serde_json::to_string(&ires)
        }
    };
    result.expect("failed to serialize or deserialize")
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

/// public string-based JSON interface to parse a schema in Cedar's human-readable format
#[jni_fn("com.cedarpolicy.model.schema.Schema")]
pub fn parseHumanSchemaJni<'a>(mut env: JNIEnv<'a>, _: JClass, schema_jstr: JString<'a>) -> jvalue {
    match parse_human_schema_internal(&mut env, schema_jstr) {
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
        match Schema::from_str(&schema_string) {
            Err(e) => Err(Box::new(e)),
            Ok(_) => Ok(JValueGen::Object(env.new_string("success")?.into())),
        }
    }
}

fn parse_human_schema_internal<'a>(
    env: &mut JNIEnv<'a>,
    schema_jstr: JString<'a>,
) -> Result<JValueOwned<'a>> {
    if schema_jstr.is_null() {
        raise_npe(env)
    } else {
        let schema_jstring = env.get_string(&schema_jstr)?;
        let schema_string = String::from(schema_jstring);
        match Schema::from_str_natural(&schema_string) {
            Err(e) => Err(Box::new(e)),
            Ok(_) => Ok(JValueGen::Object(env.new_string("success")?.into())),
        }
    }
}

#[jni_fn("com.cedarpolicy.model.slice.Policy")]
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

#[jni_fn("com.cedarpolicy.model.slice.Policy")]
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

#[jni_fn("com.cedarpolicy.model.slice.Policy")]
pub fn validateTemplateLinkedPolicyJni<'a>(
    mut env: JNIEnv<'a>,
    _: JClass,
    template_jstr: JString<'a>,
    jprincipal_euid: JObject<'a>,
    jresource_euid: JObject<'a>,
) -> jvalue {
    match validate_template_linked_policy_internal(
        &mut env,
        template_jstr,
        jprincipal_euid,
        jresource_euid,
    ) {
        Err(e) => jni_failed(&mut env, e.as_ref()),
        Ok(v) => v.as_jni(),
    }
}

fn validate_template_linked_policy_internal<'a>(
    env: &mut JNIEnv<'a>,
    template_jstr: JString<'a>,
    jprincipal_euid: JObject<'a>,
    jresource_euid: JObject<'a>,
) -> Result<JValueOwned<'a>> {
    if template_jstr.is_null() {
        raise_npe(env)
    } else {
        let template_jstring = env.get_string(&template_jstr)?;
        let template_string = String::from(template_jstring);
        let template = Template::from_str(&template_string)?;
        let mut slots_map: HashMap<SlotId, EntityUid> = HashMap::new();

        if !jprincipal_euid.is_null() {
            slots_map.insert(
                SlotId::principal(),
                JEntityUID::cast(env, jprincipal_euid)?.get_rust_repr(),
            );
        }
        if !jresource_euid.is_null() {
            slots_map.insert(
                SlotId::resource(),
                JEntityUID::cast(env, jresource_euid)?.get_rust_repr(),
            );
        }

        let template_id = template.id().clone();
        let instantiated_id = PolicyId::from_str("x")?;
        let mut policy_set = PolicySet::new();
        policy_set.add_template(template)?;

        policy_set.link(template_id, instantiated_id, slots_map)?;
        Ok(JValueGen::Bool(1))
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
