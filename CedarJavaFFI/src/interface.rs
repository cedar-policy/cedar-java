use cedar::frontend::{
    is_authorized::json_is_authorized, utils::InterfaceResult, validate::json_validate,
};
use jni::{
    objects::{JClass, JString},
    sys::jstring,
    JNIEnv,
};
use jni_fn::jni_fn;
use serde::{Deserialize, Serialize};

const V0_AUTH_OP: &str = "AuthorizationOperation";
const V0_VALIDATE_OP: &str = "ValidateOperation";

fn build_err_obj(env: JNIEnv<'_>, err: &str) -> jstring {
    env.new_string(
        serde_json::to_string(&InterfaceResult::fail_bad_request(vec![format!(
            "Failed {} Java string",
            err
        )]))
        .expect("could not serialise response"),
    )
    .expect("error creating Java string")
    .into_inner()
}

/// The main JNI entry point
#[jni_fn("cedar-policy.WrapperAuthorizationEngine")]
pub fn callCedarJNI(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    j_call: JString<'_>,
    j_input: JString<'_>,
) -> jstring {
    let parsing_err = || build_err_obj(env, "parsing");
    let getting_err = || build_err_obj(env, "getting");

    let j_call_str = match env.get_string(j_call) {
        Ok(call_str) => call_str,
        _ => return getting_err(),
    };
    let j_call_str = match j_call_str.to_str() {
        Ok(call_str) => call_str,
        _ => return parsing_err(),
    };

    let j_input_str: String = match env.get_string(j_input) {
        Ok(s) => s.into(),
        Err(_) => return parsing_err(),
    };

    let result = call_cedar(j_call_str, &j_input_str);

    let res = env.new_string(result);
    match res {
        Ok(r) => r.into_inner(),
        _ => env
            .new_string(
                serde_json::to_string(&InterfaceResult::fail_internally(
                    "Failed creating Java string".to_string(),
                ))
                .expect("could not serialise response"),
            )
            .expect("error creating Java string")
            .into_inner(),
    }
}

/// The main JNI entry point
#[jni_fn("cedar-policy.WrapperAuthorizationEngine")]
pub fn getCedarJNIVersion(env: JNIEnv<'_>) -> jstring {
    env.new_string("1.2")
        .expect("error creating Java string")
        .into_inner()
}

fn call_cedar(call: &str, input: &str) -> String {
    let result = match call {
        V0_AUTH_OP => json_is_authorized(input),
        V0_VALIDATE_OP => json_validate(input),
        _ => InterfaceResult::fail_internally(format!("unsupported operation: {}", call)),
    };
    serde_json::to_string(&result).expect("could not serialise response")
}

#[derive(Debug, Serialize, Deserialize)]
struct JavaInterfaceCall {
    pub call: String,
    arguments: String,
}

#[cfg(test)]
mod test {
    use super::*;

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
            "resource": "Resource:\"thing\""
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
