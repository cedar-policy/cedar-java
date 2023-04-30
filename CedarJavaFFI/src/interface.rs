use cedar_policy::frontend::{
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
    .into_raw()
}

/// The main JNI entry point
#[jni_fn("cedarpolicy.WrapperAuthorizationEngine")]
pub fn callCedarJNI(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    j_call: JString<'_>,
    j_input: JString<'_>,
) -> jstring {
    let parsing_err = || build_err_obj(env, "parsing");
    let getting_err = || build_err_obj(env, "getting");

    let j_call_str: String = match env.get_string(j_call) {
        Ok(call_str) => call_str.into(),
        _ => return getting_err(),
    };
    let mut j_call_str = j_call_str.clone();
    j_call_str.push(' ');
    j_call_str = j_call_str.trim_end().to_string();
    // let j_call_str = match j_call_str.to_str() {
    //     Ok(call_str) => call_str,
    //     _ => return parsing_err(),
    // };

    let j_input_str: String = match env.get_string(j_input) {
        Ok(s) => s.into(),
        Err(_) => return parsing_err(),
    };
    let mut j_input_str = j_input_str.clone();
    j_input_str.push(' ');


    let result = call_cedar(&j_call_str, &j_input_str);
    println!("Done with call_cedar");

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
#[jni_fn("cedarpolicy.WrapperAuthorizationEngine")]
pub fn getCedarJNIVersion(env: JNIEnv<'_>) -> jstring {
    env.new_string("2.0")
        .expect("error creating Java string")
        .into_raw()
}

fn call_cedar(call: &str, input: &str) -> String {
    println!("Rust input: {input}");
    let call = String::from(call);
    let input = String::from(input);
    let result = match call.as_str() {
        V0_AUTH_OP => {
            println!("Calling: `json_is_authorized`");
            json_is_authorized(&input)
        }
        V0_VALIDATE_OP => json_validate(&input),
        _ => InterfaceResult::fail_internally(format!("unsupported operation: {}", call)),
    };
    println!("Response:");
    println!("{:?}", result);
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
    fn long_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
{
  "principal": "User::\"alice\"",
  "action": "Photo::\"view\"",
  "resource": "Photo::\"photo\"",
  "slice": {
    "policies": {
        "001": "permit( principal==User::\"alice\", action==Action::\"view\", resource==Resource::\"photo.jpg\" ) when { resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" };"
    },
    "entities": [
        { "uid": { "__entity": { "type": "Photo", "id": "photo"} },
    "attrs": {
      "name": "my_photo"
    },
    "parents": []
}
     ]
  },
  "context": {}
}
            "#,
        );
        assert_success(result);
    }

    #[test]
    fn java_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"{"context":{},"schema":null,"slice":{"policies":{"ID1":"permit( principal==User::\"alice\", action==Action::\"view\", resource==Resource::\"my_photo.jpg\" ) when { resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" && resource.name123==\"my_photo123\" };"},"entities":[{"uid":{"__expr":"Resource::\"my_photo.jpg\""},"attrs":{"name123":"my_photo123"},"parents":[]},{"uid":{"__expr":"Action::\"view\""},"attrs":{},"parents":[]},{"uid":{"__expr":"User::\"alice\""},"attrs":{},"parents":[]}],"templates":{},"template_instantiations":[]},"principal":"User::\"alice\"","action":"Action::\"view\"","resource":"Resource::\"my_photo.jpg\""}"#,
        );
        println!("Result: {:?}", result);
        assert_success(result);
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
