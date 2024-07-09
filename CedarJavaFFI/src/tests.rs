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

#![cfg(test)]

use crate::answer::Answer;
use crate::call_cedar;
#[cfg(feature = "partial-eval")]
use cedar_policy::ffi::PartialAuthorizationAnswer;
use cedar_policy::ffi::{AuthorizationAnswer, ValidationAnswer};
use cool_asserts::assert_matches;

#[track_caller]
fn assert_failure(result: String) {
    let result: Answer = serde_json::from_str(result.as_str()).unwrap();
    assert_matches!(result, Answer::Failure { .. });
}

#[track_caller]
fn assert_authorization_success(result: String) {
    let result: AuthorizationAnswer = serde_json::from_str(result.as_str()).unwrap();
    assert_matches!(result, AuthorizationAnswer::Success { .. });
}

#[track_caller]
fn assert_authorization_failure(result: String) {
    let result: AuthorizationAnswer = serde_json::from_str(result.as_str()).unwrap();
    assert_matches!(result, AuthorizationAnswer::Failure { .. });
}

#[cfg(feature = "partial-eval")]
#[track_caller]
fn assert_partial_authorization_success(result: String) {
    let result: PartialAuthorizationAnswer = serde_json::from_str(result.as_str()).unwrap();
    assert_matches!(result, PartialAuthorizationAnswer::Residuals { .. });
}

#[track_caller]
fn assert_validation_success(result: String) {
    let result: ValidationAnswer = serde_json::from_str(result.as_str()).unwrap();
    assert_matches!(result, ValidationAnswer::Success { .. });
}

#[test]
fn unrecognized_call_fails() {
    let result = call_cedar("BadOperation", "");
    assert_failure(result);
}

mod authorization_tests {
    use super::*;

    #[test]
    fn empty_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
    {
    "principal" : { "type" : "User", "id" : "alice" },
    "action" : { "type" : "Photo", "id" : "view" },
    "resource" : { "type" : "Photo", "id" : "photo" },
    "policies": {},
    "entities": [],
    "context": {}
    }
            "#,
        );
        assert_authorization_success(result);
    }

    #[test]
    fn test_unspecified_principal_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
    {
        "context": {},
        "policies": {
            "staticPolicies": {
            "001": "permit(principal, action, resource);"
            },
            "templates": {},
            "templateLinks": []
        },
        "entities": [],
        "principal": null,
        "action" : { "type" : "Action", "id" : "view" },
        "resource" : { "type" : "Resource", "id" : "thing" }
    }
    "#,
        );
        assert_authorization_failure(result);
    }

    #[test]
    fn test_unspecified_resource_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
    {
        "context": {},
        "policies": {
            "staticPolicies": {
            "001": "permit(principal, action, resource);"
            },
            "templates": {},
            "templateLinks": []
        },
        "entities": [],
        "principal" : { "type" : "User", "id" : "alice" },
        "action" : { "type" : "Action", "id" : "view" },
        "resource": null
    }
    "#,
        );
        assert_authorization_failure(result);
    }

    #[test]
    fn template_authorization_call_succeeds() {
        let result = call_cedar(
            "AuthorizationOperation",
            r#"
        {
            "principal" : {
                "type" : "User",
                "id" : "alice"
            },
            "action" : {
                "type" : "Photo",
                "id" : "view"
            },
            "resource" : {
                "type" : "Photo",
                "id" : "door"
            },
            "context" : {},
            "policies" : {
                "staticPolicies" : {},
                "templates" : {
                    "ID0": "permit(principal == ?principal, action, resource);"
                },
                "templateLinks" : [
                    {
                        "templateId" : "ID0",
                        "newId" : "ID0_User_alice",
                        "values" : {
                            "?principal": {
                                "type" : "User",
                                "id" : "alice"
                            }
                        }
                    }
                ]
            },
            "entities" : []
        }
            "#,
        );
        assert_authorization_success(result);
    }
}

mod validation_tests {
    use super::*;

    #[test]
    fn empty_validation_call_json_schema_succeeds() {
        let result = call_cedar("ValidateOperation", r#"{ "schema": {}, "policies": {} }"#);
        assert_validation_success(result);
    }

    #[test]
    fn empty_validation_call_succeeds() {
        let result = call_cedar("ValidateOperation", r#"{ "schema": "", "policies": {} }"#);
        assert_validation_success(result);
    }
}

#[cfg(feature = "partial-eval")]
mod partial_authorization_tests {
    use super::*;

    #[test]
    fn test_missing_resource_call_succeeds() {
        let result = call_cedar(
            "AuthorizationPartialOperation",
            r#"
    {
        "context": {},
        "policies": {
            "staticPolicies": {
            "001": "permit(principal == User::\"alice\", action, resource == Photo::\"door\");"
            },
            "templates": {},
            "templateLinks": []
        },
        "entities": [],
        "principal" : { "type" : "User", "id" : "alice" },
        "action" : { "type" : "Action", "id" : "view" }
    }
    "#,
        );
        assert_partial_authorization_success(result);
    }

    #[test]
    fn test_missing_principal_call_succeeds() {
        let result = call_cedar(
            "AuthorizationPartialOperation",
            r#"
    {
        "context": {},
        "policies": {
            "staticPolicies": {
            "001": "permit(principal == User::\"alice\", action, resource == Photo::\"door\");"
            },
            "templates": {},
            "templateLinks": []
        },
        "entities": [],
        "action" : { "type" : "Action", "id" : "view" },
        "resource" : { "type" : "Photo", "id" : "door" }
    }
    "#,
        );
        assert_partial_authorization_success(result);
    }
}

mod parsing_tests {}
