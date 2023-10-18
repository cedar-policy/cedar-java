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

use cedar_policy::{
    frontend::{
        is_authorized::json_is_authorized, utils::InterfaceResult, validate::json_validate,
    },
    EntityTypeName,
};
use jni::{
    objects::{JClass, JObject, JString, JValueGen, JValueOwned},
    sys::{jobject, jstring, jvalue},
    JNIEnv,
};
use jni_fn::jni_fn;
use serde::{Deserialize, Serialize};
use std::{error::Error, str::FromStr, thread};

const V0_AUTH_OP: &str = "AuthorizationOperation";
const V0_VALIDATE_OP: &str = "ValidateOperation";
const V0_PARSE_EUID_OP: &str = "ParseEntityUidOperation";

fn build_err_obj(env: &JNIEnv<'_>, err: &str) -> jstring {
    env.new_string(
        serde_json::to_string(&InterfaceResult::fail_bad_request(vec![format!(
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

/// The main JNI entry point
#[jni_fn("com.cedarpolicy.BasicAuthorizationEngine")]
pub fn callCedarJNI(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    j_call: JString<'_>,
    j_input: JString<'_>,
) -> jstring {
    let parsing_err = build_err_obj(&env, "parsing");
    let getting_err = build_err_obj(&env, "getting");

    let j_call_str: String = match env.get_string(&j_call) {
        Ok(call_str) => call_str.into(),
        _ => return getting_err,
    };
    let j_call_str = j_call_str;

    let j_input_str: String = match env.get_string(&j_input) {
        Ok(s) => s.into(),
        Err(_) => return parsing_err,
    };
    let mut j_input_str = j_input_str;
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
                serde_json::to_string(&InterfaceResult::fail_internally(
                    "Failed creating Java string".to_string(),
                ))
                .expect("could not serialise response"),
            )
            .expect("error creating Java string")
            .into_raw(),
    }
}

/// The main JNI entry point
#[jni_fn("com.cedarpolicy.BasicAuthorizationEngine")]
pub fn getCedarJNIVersion(env: JNIEnv<'_>) -> jstring {
    env.new_string("2.3")
        .expect("error creating Java string")
        .into_raw()
}

fn call_cedar(call: &str, input: &str) -> String {
    let call = String::from(call);
    let input = String::from(input);
    let result = match call.as_str() {
        V0_AUTH_OP => json_is_authorized(&input),
        V0_VALIDATE_OP => json_validate(&input),
        V0_PARSE_EUID_OP => json_parse_entity_uid(&input),
        _ => InterfaceResult::fail_internally(format!("unsupported operation: {}", call)),
    };
    serde_json::to_string(&result).expect("could not serialise response")
}

#[derive(Debug, Serialize, Deserialize)]
struct JavaInterfaceCall {
    pub call: String,
    arguments: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct ParseEUIDCall {
    euid: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct ParseEUIDOutput {
    ty: String,
    id: String,
}

#[jni_fn("com.cedarpolicy.value.EntityTypeName")]
pub fn parseEntityTypeName<'a>(mut env: JNIEnv<'a>, _: JClass, src_string: JString<'a>) -> jvalue {
    match parse_entity_type_name_internal(&mut env, src_string) {
        Ok(v) => v.as_jni(),
        Err(e) => {
            // If we already generated an exception, then let that go up the stack
            // Otherwise, generate a cedar InternalException and return null
            if !env.exception_check().unwrap_or_default() {
                env.throw_new(
                    "com/cedarpolicy/model/exception/InternalException",
                    format!("Internal JNI Error: {e}"),
                )
                .unwrap();
            }
            JValueOwned::Object(JObject::null()).as_jni()
        }
    }
}

fn parse_entity_type_name_internal<'a>(
    env: &mut JNIEnv<'a>,
    src_string: JString<'a>,
) -> Result<JValueOwned<'a>, Box<dyn Error>> {
    if src_string.is_null() {
        build_empty(env)
    } else {
        let jstring = env.get_string(&src_string)?;
        let src = jstring.to_str()?;
        let r: Result<EntityTypeName, _> = src.parse();
        match r {
            Ok(etype) => build_entity_type_object(env, etype),
            _ => build_empty(env),
        }
    }
}

fn build_empty<'a>(env: &mut JNIEnv<'a>) -> Result<JValueOwned<'a>, Box<dyn Error>> {
    Ok(env.call_static_method("java/util/Optional", "empty", "()Ljava/util/Optional;", &[])?)
}

fn build_entity_type_object<'a>(
    env: &mut JNIEnv<'a>,
    etype: EntityTypeName,
) -> Result<JValueOwned<'a>, Box<dyn Error>> {
    let basename_str = env.new_string(etype.basename())?;
    let basename_value = JValueGen::Object(basename_str.as_ref());
    let namespace_array = env.new_object("java/util/ArrayList", "()V", &[])?;
    for part in etype.namespace_components() {
        let part_str = env.new_string(part)?;
        let part_ref = JValueGen::Object(part_str.as_ref());
        env.call_method(
            &namespace_array,
            "add",
            "(Ljava/lang/Object;)Z",
            &[part_ref],
        )?;
    }
    let namespace_array_value = JValueGen::Object(&namespace_array);

    let entity_type_obj = env.new_object(
        "com/cedarpolicy/value/EntityTypeName",
        "(Ljava/util/List;Ljava/lang/String;)V",
        &[namespace_array_value, basename_value],
    )?;

    let optional_obj = env.call_static_method(
        "java/util/Optional",
        "of",
        "(Ljava/lang/Object;)Ljava/util/Optional;",
        &[JValueGen::Object(&entity_type_obj)],
    )?;

    Ok(optional_obj)
}

/// public string-based JSON interface to be invoked by FFIs. Takes in a `ParseEUIDCall`, parses it and (if successful)
/// returns a serialized `ParseEUIDOutput`
pub fn json_parse_entity_uid(input: &str) -> InterfaceResult {
    match serde_json::from_str::<ParseEUIDCall>(input) {
        Err(e) => {
            InterfaceResult::fail_internally(format!("error parsing call to parse EntityUID: {e:}"))
        }
        Ok(euid_call) => match cedar_policy::EntityUid::from_str(euid_call.euid.as_str()) {
            Ok(euid) => match serde_json::to_string(&ParseEUIDOutput {
                ty: euid.type_name().to_string(),
                id: euid.id().to_string(),
            }) {
                Ok(s) => InterfaceResult::succeed(s),
                Err(e) => {
                    InterfaceResult::fail_internally(format!("error serializing EntityUID: {e:}"))
                }
            },
            Err(e) => InterfaceResult::fail_internally(format!("error parsing EntityUID: {e:}")),
        },
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn parse_entityuid() {
        let result = call_cedar("ParseEntityUidOperation", r#"{"euid": "User::\"Alice\""} "#);
        assert_success(result);
    }

    #[test]
    fn empty_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
{
  "principal": "User::\"alice\"",
  "action": "Photo::\"view\"",
  "resource": "Photo::\"photo\"",
  "slice": {
    "policies": {},
    "entities": []
  },
  "context": {}
}
            "#,
        );
        assert_success(result);
    }

    #[test]
    fn empty_validation_call_succeeds() {
        let result = call_cedar(
            "ValidateOperation",
            r#"{ "schema": { "": {"entityTypes": {}, "actions": {} } }, "policySet": {} }"#,
        );
        assert_success(result);
    }

    #[test]
    fn unrecognised_call_fails() {
        let result = call_cedar("BadOperation", "");
        assert_failure(result);
    }

    #[test]
    fn test_unspecified_principal_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
        {
            "context": {},
            "slice": {
              "policies": {
                "001": "permit(principal, action, resource);"
              },
              "entities": [],
              "templates": {},
              "template_instantiations": []
            },
            "principal": null,
            "action": "Action::\"view\"",
            "resource": "Resource::\"thing\""
        }
        "#,
        );
        assert_success(result);
    }

    #[test]
    fn test_unspecified_resource_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
        {
            "context": {},
            "slice": {
              "policies": {
                "001": "permit(principal, action, resource);"
              },
              "entities": [],
              "templates": {},
              "template_instantiations": []
            },
            "principal": "User::\"alice\"",
            "action": "Action::\"view\"",
            "resource": null
        }
        "#,
        );
        assert_success(result);
    }

    #[test]
    fn template_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
	             { "principal": "User::\"alice\""
	             , "action" : "Photo::\"view\""
	             , "resource" : "Photo::\"door\""
	             , "context" : {}
	             , "slice" : {
	                   "policies" : {}
	                 , "entities" : []
                     , "templates" : {
                        "ID0": "permit(principal == ?principal, action, resource);"
                      }
                     , "template_instantiations" : [
                        {
                            "template_id" : "ID0",
                            "result_policy_id" : "ID0_User_alice",
                            "instantiations" : [
                                {
                                    "slot": "?principal",
                                    "value": {
                                        "ty" : "User",
                                        "eid" : "alice"
                                    }
                                }
                            ]
                        }
                     ]
	             }
	          }
	         "#,
        );
        assert_success(result);
    }

    fn assert_success(result: String) {
        let result: InterfaceResult = serde_json::from_str(result.as_str()).unwrap();
        match result {
            InterfaceResult::Success { .. } => {}
            InterfaceResult::Failure { .. } => panic!("expected a success, not {:?}", result),
        };
    }

    fn assert_failure(result: String) {
        let result: InterfaceResult = serde_json::from_str(result.as_str()).unwrap();
        match result {
            InterfaceResult::Success { .. } => panic!("expected a failure, not {:?}", result),
            InterfaceResult::Failure { .. } => {}
        };
    }
}
