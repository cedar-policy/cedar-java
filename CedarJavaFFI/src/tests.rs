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
fn assert_failure(result: &str) {
    let result: Answer = serde_json::from_str(result).unwrap();
    assert_matches!(result, Answer::Failure { .. });
}

#[track_caller]
fn assert_success(result: &str) {
    let result: Answer = serde_json::from_str(result).unwrap();
    assert_matches!(result, Answer::Success { .. });
}

#[track_caller]
fn assert_authorization_success(result: &str) {
    let result: AuthorizationAnswer = serde_json::from_str(result).unwrap();
    assert_matches!(result, AuthorizationAnswer::Success { .. });
}

#[track_caller]
fn assert_authorization_failure(result: &str) {
    let result: AuthorizationAnswer = serde_json::from_str(result).unwrap();
    assert_matches!(result, AuthorizationAnswer::Failure { .. });
}

#[cfg(feature = "partial-eval")]
#[track_caller]
fn assert_partial_authorization_success(result: &str) {
    let result: PartialAuthorizationAnswer = serde_json::from_str(result).unwrap();
    assert_matches!(result, PartialAuthorizationAnswer::Residuals { .. });
}

#[track_caller]
fn assert_validation_success(result: &str) {
    let result: ValidationAnswer = serde_json::from_str(result).unwrap();
    assert_matches!(result, ValidationAnswer::Success { .. });
}

#[test]
fn unrecognized_call_fails() {
    let result = call_cedar("BadOperation", "");
    assert_failure(&result);
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
        assert_authorization_success(&result);
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
        assert_authorization_failure(&result);
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
        assert_authorization_failure(&result);
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
        assert_authorization_success(&result);
    }
}

mod validation_tests {
    use super::*;

    #[test]
    fn empty_validation_call_json_schema_succeeds() {
        let result = call_cedar("ValidateOperation", r#"{ "schema": {}, "policies": {} }"#);
        assert_validation_success(&result);
    }

    #[test]
    fn empty_validation_call_succeeds() {
        let result = call_cedar("ValidateOperation", r#"{ "schema": "", "policies": {} }"#);
        assert_validation_success(&result);
    }

    #[test]
    fn validate_with_level_succeeds() {
        let input = r#" {
            "schema": {
                "": {
                    "entityTypes": {
                        "User": {
                            "memberOfTypes": [
                                "UserGroup"
                            ],
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "friend": {
                                        "type": "Entity",
                                        "name": "User"
                                    }
                                }
                            }
                        },
                        "Photo": {
                            "memberOfTypes": [
                                "Album",
                                "Account"
                            ],
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "owner": {
                                        "type": "Entity",
                                        "name": "User"
                                    }
                                }
                            }
                        },
                        "Album": {
                            "memberOfTypes": [
                                "Album",
                                "Account"
                            ]
                        },
                        "Account": {},
                        "UserGroup": {}
                    },
                    "actions": {
                        "readOnly": {},
                        "readWrite": {},
                        "createAlbum": {
                            "appliesTo": {
                                "resourceTypes": [
                                    "Account",
                                    "Album"
                                ],
                                "principalTypes": [
                                    "User"
                                ]
                            }
                        },
                        "addPhotoToAlbum": {
                            "appliesTo": {
                                "resourceTypes": [
                                    "Album"
                                ],
                                "principalTypes": [
                                    "User"
                                ]
                            }
                        },
                        "viewPhoto": {
                            "appliesTo": {
                                "resourceTypes": [
                                    "Photo"
                                ],
                                "principalTypes": [
                                    "User"
                                ]
                            }
                        },
                        "viewComments": {
                            "appliesTo": {
                                "resourceTypes": [
                                    "Photo"
                                ],
                                "principalTypes": [
                                    "User"
                                ]
                            }
                        }
                    }
                }
            },
            "policies": {
                "staticPolicies": {
                    "policy0": "permit(principal in UserGroup::\"alice_friends\", action == Action::\"viewPhoto\", resource) when {principal in resource.owner.friend};"
                }
            },
            "maxDerefLevel": 2
        }
        "#;

        let result = call_cedar("ValidateWithLevelOperation", input);

        assert_validation_success(&result);
    }
}

mod entity_validation_tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn validate_entities_succeeds() {
        let json_data = json!(
            {
              "entities":[
                {
                    "uid": {
                        "type": "PhotoApp::User",
                        "id": "alice"
                    },
                    "attrs": {
                        "userId": "897345789237492878",
                        "personInformation": {
                            "age": 25,
                            "name": "alice"
                        },
                    },
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "alice_friends"
                        },
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "AVTeam"
                        }
                    ]
                },
                {
                    "uid": {
                        "type": "PhotoApp::Photo",
                        "id": "vacationPhoto.jpg"
                    },
                    "attrs": {
                        "private": false,
                        "account": {
                            "__entity": {
                                "type": "PhotoApp::Account",
                                "id": "ahmad"
                            }
                        }
                    },
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "alice_friends"
                    },
                    "attrs": {},
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    },
                    "attrs": {},
                    "parents": []
                }
              ],
              "schema":{
                "PhotoApp": {
                    "commonTypes": {
                        "PersonType": {
                            "type": "Record",
                            "attributes": {
                                "age": {
                                    "type": "Long"
                                },
                                "name": {
                                    "type": "String"
                                }
                            }
                        },
                        "ContextType": {
                            "type": "Record",
                            "attributes": {
                                "ip": {
                                    "type": "Extension",
                                    "name": "ipaddr",
                                    "required": false
                                },
                                "authenticated": {
                                    "type": "Boolean",
                                    "required": true
                                }
                            }
                        }
                    },
                    "entityTypes": {
                        "User": {
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "userId": {
                                        "type": "String"
                                    },
                                    "personInformation": {
                                        "type": "PersonType"
                                    }
                                }
                            },
                            "memberOfTypes": [
                                "UserGroup"
                            ]
                        },
                        "UserGroup": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        },
                        "Photo": {
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "account": {
                                        "type": "Entity",
                                        "name": "Account",
                                        "required": true
                                    },
                                    "private": {
                                        "type": "Boolean",
                                        "required": true
                                    }
                                }
                            },
                            "memberOfTypes": [
                                "Album",
                                "Account"
                            ]
                        },
                        "Album": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        },
                        "Account": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        }
                    },
                    "actions": {}
                }
            }
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_success(&result);
    }

    #[test]
    fn validate_entities_with_cedarschema_succeeds() {
        let json_data = json!(
        {
            "entities":[
            {
                "uid": {
                    "type": "PhotoApp::User",
                    "id": "alice"
                },
                "attrs": {
                    "userId": "897345789237492878",
                    "personInformation": {
                        "age": 25,
                        "name": "alice"
                    },
                },
                "parents": [
                    {
                        "type": "PhotoApp::UserGroup",
                        "id": "alice_friends"
                    },
                    {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    }
                ]
            },
            {
                "uid": {
                    "type": "PhotoApp::Photo",
                    "id": "vacationPhoto.jpg"
                },
                "attrs": {
                    "private": false,
                    "account": {
                        "__entity": {
                            "type": "PhotoApp::Account",
                            "id": "ahmad"
                        }
                    }
                },
                "parents": []
            },
            {
                "uid": {
                    "type": "PhotoApp::UserGroup",
                    "id": "alice_friends"
                },
                "attrs": {},
                "parents": []
            },
            {
                "uid": {
                    "type": "PhotoApp::UserGroup",
                    "id": "AVTeam"
                },
                "attrs": {},
                "parents": []
            }
            ],
            "schema":r#"
                namespace PhotoApp {
                    type ContextType = {
                    "authenticated": __cedar::Bool,
                    "ip"?: __cedar::ipaddr
                    };

                    type PersonType = {
                    "age": __cedar::Long,
                    "name": __cedar::String
                    };

                    entity Account;

                    entity Album;

                    entity Photo in [Album, Account] = {
                    "account": Account,
                    "private": __cedar::Bool
                    };

                    entity User in [UserGroup] = {
                    "personInformation": PersonType,
                    "userId": __cedar::String
                    };

                    entity UserGroup;
                    }"#
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_success(&result);
    }

    #[test]
    fn validate_entities_field_missing() {
        let json_data = json!(
            {
              "entities":[
                {
                    "uid": {
                        "type": "PhotoApp::User",
                        "id": "alice"
                    },
                    "attrs": {
                        "userId": "897345789237492878"
                    },
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "alice_friends"
                        },
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "AVTeam"
                        }
                    ]
                },
                {
                    "uid": {
                        "type": "PhotoApp::Photo",
                        "id": "vacationPhoto.jpg"
                    },
                    "attrs": {
                        "private": false,
                        "account": {
                            "__entity": {
                                "type": "PhotoApp::Account",
                                "id": "ahmad"
                            }
                        }
                    },
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "alice_friends"
                    },
                    "attrs": {},
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    },
                    "attrs": {},
                    "parents": []
                }
              ],
              "schema":{
                "PhotoApp": {
                    "commonTypes": {
                        "PersonType": {
                            "type": "Record",
                            "attributes": {
                                "age": {
                                    "type": "Long"
                                },
                                "name": {
                                    "type": "String"
                                }
                            }
                        },
                        "ContextType": {
                            "type": "Record",
                            "attributes": {
                                "ip": {
                                    "type": "Extension",
                                    "name": "ipaddr",
                                    "required": false
                                },
                                "authenticated": {
                                    "type": "Boolean",
                                    "required": true
                                }
                            }
                        }
                    },
                    "entityTypes": {
                        "User": {
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "userId": {
                                        "type": "String"
                                    },
                                    "personInformation": {
                                        "type": "PersonType"
                                    }
                                }
                            },
                            "memberOfTypes": [
                                "UserGroup"
                            ]
                        },
                        "UserGroup": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        },
                        "Photo": {
                            "shape": {
                                "type": "Record",
                                "attributes": {
                                    "account": {
                                        "type": "Entity",
                                        "name": "Account",
                                        "required": true
                                    },
                                    "private": {
                                        "type": "Boolean",
                                        "required": true
                                    }
                                }
                            },
                            "memberOfTypes": [
                                "Album",
                                "Account"
                            ]
                        },
                        "Album": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        },
                        "Account": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            }
                        }
                    },
                    "actions": {}
                }
            }
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);
    }

    #[test]
    fn validate_entities_with_cedarschema_field_missing() {
        let json_data = json!(
            {
              "entities":[
                {
                    "uid": {
                        "type": "PhotoApp::User",
                        "id": "alice"
                    },
                    "attrs": {
                        "userId": "897345789237492878"
                    },
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "alice_friends"
                        },
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "AVTeam"
                        }
                    ]
                },
                {
                    "uid": {
                        "type": "PhotoApp::Photo",
                        "id": "vacationPhoto.jpg"
                    },
                    "attrs": {
                        "private": false,
                        "account": {
                            "__entity": {
                                "type": "PhotoApp::Account",
                                "id": "ahmad"
                            }
                        }
                    },
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "alice_friends"
                    },
                    "attrs": {},
                    "parents": []
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    },
                    "attrs": {},
                    "parents": []
                }
              ],
              "schema":r#"namespace PhotoApp {
                    type ContextType = {
                    "authenticated": __cedar::Bool,
                    "ip"?: __cedar::ipaddr
                    };

                    type PersonType = {
                    "age": __cedar::Long,
                    "name": __cedar::String
                    };

                    entity Account;

                    entity Album;

                    entity Photo in [Album, Account] = {
                    "account": Account,
                    "private": __cedar::Bool
                    };

                    entity User in [UserGroup] = {
                    "personInformation": PersonType,
                    "userId": __cedar::String
                    };

                    entity UserGroup;
                    }"#
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);
    }

    #[test]
    #[should_panic]
    fn validate_entities_invalid_json_fails() {
        call_cedar("ValidateEntities", "{]");
    }

    #[test]
    fn validate_entities_invalid_schema_fails() {
        let json_data = json!(
        {
            "entities": [

            ],
            "schema": {
                "PhotoApp": {
                    "commonTypes": {},
                    "entityTypes": {
                        "UserGroup": {
                            "shape44": {
                                "type": "Record",
                                "attributes": {}
                            },
                            "memberOfTypes": [
                                "UserGroup"
                            ]
                        }
                    },
                    "actions": {}
                }
            }
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);

        assert!(
            result.contains(
                "unknown field `shape44`, expected one of `memberOfTypes`, `shape`, `tags`"
            ),
            "result was `{result}`",
        );
    }

    #[test]
    fn validate_entities_invalid_cedarschema_fails() {
        let json_data = json!(
        {
            "entities": [

            ],
            "schema": r#"namespace PhotoApp {
                type ContextType = {
                "authenticated": __cedar::Bool,
                "ip"?: __cedar::ipaddr
                };

                type PersonType = {
                "age": __cedar::Long,
                "name": __cedar::String
                };

                entity Account;

                entity Album;

                entity Photo in [Album, Account] = {
                "account": Account,
                "private": __cedar::Tool
                };

                entity User in [UserGroup] = {
                "personInformation": PersonType,
                "userId": __cedar::String
                };

                entity UserGroup;
                }"#
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);

        assert!(
            result.contains("failed to resolve type: __cedar::Tool"),
            "result was `{result}`",
        );
    }

    #[test]
    fn validate_entities_detect_cycle_fails() {
        let json_data = json!(
        {
            "entities": [
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "ABCTeam"
                    },
                    "attrs": {},
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "AVTeam"
                        }
                    ]
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    },
                    "attrs": {},
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "ABCTeam"
                        }
                    ]
                }
            ],
            "schema": {
                "PhotoApp": {
                    "commonTypes": {},
                    "entityTypes": {
                        "UserGroup": {
                            "shape": {
                                "type": "Record",
                                "attributes": {}
                            },
                            "memberOfTypes": [
                                "UserGroup"
                            ]
                        }
                    },
                    "actions": {}
                }
            }
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);

        assert!(
            result.contains("input graph has a cycle containing vertex `PhotoApp::UserGroup"),
            "result was `{result}`",
        );
    }

    #[test]
    fn validate_entities_with_cedarschema_detect_cycle_fails() {
        let json_data = json!(
        {
            "entities": [
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "ABCTeam"
                    },
                    "attrs": {},
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "AVTeam"
                        }
                    ]
                },
                {
                    "uid": {
                        "type": "PhotoApp::UserGroup",
                        "id": "AVTeam"
                    },
                    "attrs": {},
                    "parents": [
                        {
                            "type": "PhotoApp::UserGroup",
                            "id": "ABCTeam"
                        }
                    ]
                }
            ],
            "schema": r#"namespace PhotoApp {
                entity UserGroup in [UserGroup];
                }
                "#
        });
        let result = call_cedar("ValidateEntities", json_data.to_string().as_str());
        assert_failure(&result);

        assert!(
            result.contains("input graph has a cycle containing vertex `PhotoApp::UserGroup"),
            "result was `{result}`",
        );
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
        assert_partial_authorization_success(&result);
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
        assert_partial_authorization_success(&result);
    }
}

/// Schema fixtures shared by the TPE tests here and the JNI-boundary tests in `interface.rs`.
///
/// `ffi::Schema` is an untagged enum discriminated by JSON type: a Cedar-format schema travels as a
/// JSON string, a JSON-format one as a JSON object. Each fixture selects one arm.
#[cfg(feature = "tpe")]
pub(crate) mod tpe_schema_fixtures {
    use cedar_policy::SchemaFragment;
    use serde_json::json;

    pub(crate) const CEDAR_SCHEMA_SRC: &str = r#"
        entity Group;
        entity User in [Group] = { "isAdmin": Bool, "num"?: Long };
        entity Photo;
        action view appliesTo {
            principal: [User],
            resource: [Photo],
            context: { "authenticated": Bool }
        };
    "#;

    /// The Cedar-format schema, JSON-encoded as a string.
    pub(crate) fn schema_cedar_src() -> String {
        json!(CEDAR_SCHEMA_SRC).to_string()
    }

    /// The same schema in Cedar's JSON schema format, as a JSON object. Derived from the Cedar
    /// source above rather than written out again, so the two cannot drift apart.
    pub(crate) fn schema_json() -> String {
        let (fragment, _) = SchemaFragment::from_cedarschema_str(CEDAR_SCHEMA_SRC)
            .expect("the Cedar-format fixture should parse");
        fragment
            .to_json_value()
            .expect("a parsed schema should convert to JSON")
            .to_string()
    }
}

/// With the `tpe` feature off, both type-aware operations must still be dispatched and must answer with
/// something Java can deserialize, carrying the token that becomes
/// `MissingExperimentalFeatureException`. Falling through to `call_cedar`'s unsupported-operation arm
/// would produce an `errors` array of bare strings that the TPE response types cannot bind.
#[cfg(not(feature = "tpe"))]
mod tpe_disabled_tests {
    use super::*;

    #[test]
    fn both_operations_report_the_disabled_feature_token() {
        for op in [
            "AuthorizationTypeAwarePartialOperation",
            "TypeAwarePartialReauthorizeOperation",
        ] {
            let answer: serde_json::Value = serde_json::from_str(&call_cedar(op, "{}"))
                .unwrap_or_else(|e| panic!("{op} must answer with valid JSON: {e}"));
            assert_eq!(answer["type"], "failure", "{op}");
            let message = answer["errors"][0]["message"].as_str().unwrap();
            assert!(
                message.contains("TypeAwarePartialEvaluationNotEnabled"),
                "{op} must carry the token but said: {message}"
            );
            // The error object must have the DetailedError shape, not a bare string.
            assert!(answer["errors"][0]["related"].is_array(), "{op}");
        }
    }
}

#[cfg(feature = "tpe")]
mod tpe_validation_tests {
    use super::*;
    use crate::tests::tpe_schema_fixtures::{schema_cedar_src, schema_json, CEDAR_SCHEMA_SRC};
    use crate::tpe::{
        validate_partial_entities, validate_partial_entity, validate_type_aware_partial_request,
    };
    use serde_json::json;

    /// Assert that validation failed for the expected reason. Checking several fragments of the
    /// message rather than only that an error occurred keeps the assertion specific to the rule
    /// under test: every error crossing this boundary is flattened to a `Box<dyn Error>`, so
    /// `Err(_)` alone would also match a schema that failed to parse or JSON that failed to
    /// deserialize.
    #[track_caller]
    fn assert_err_contains(result: crate::utils::Result<()>, fragments: &[&str]) {
        let err = result.expect_err("expected validation to fail").to_string();
        for fragment in fragments {
            assert!(
                err.contains(fragment),
                "expected the error to mention `{fragment}` but was: {err}"
            );
        }
    }

    #[test]
    fn validate_partial_entity_succeeds() {
        let entity = json!({
            "uid": { "type": "User", "id": "alice" },
            "attrs": { "isAdmin": false }
        });
        assert_matches!(
            validate_partial_entity(&entity.to_string(), &schema_cedar_src()),
            Ok(())
        );
    }

    #[test]
    fn validate_partial_entity_with_mistyped_attr_fails() {
        let entity = json!({
            "uid": { "type": "User", "id": "alice" },
            "attrs": { "isAdmin": 3 }
        });
        assert_err_contains(
            validate_partial_entity(&entity.to_string(), &schema_cedar_src()),
            &[
                "attribute `isAdmin`",
                "User::\"alice\"",
                "type mismatch",
                "expected to have type bool",
                "actually has type long",
            ],
        );
    }

    #[test]
    fn validate_partial_entity_with_undeclared_type_fails() {
        let entity = json!({ "uid": { "type": "Album", "id": "trip" } });
        assert_err_contains(
            validate_partial_entity(&entity.to_string(), &schema_cedar_src()),
            &[
                "entity `Album::\"trip\"`",
                "type `Album`",
                "not declared in the schema",
            ],
        );
    }

    #[test]
    fn validate_partial_entities_with_unknown_ancestors_of_parent_fails() {
        let entities = json!([
            {
                "uid": { "type": "User", "id": "alice" },
                "attrs": { "isAdmin": false },
                "parents": [ { "type": "Group", "id": "admins" } ]
            },
            {
                "uid": { "type": "Group", "id": "admins" },
                "attrs": {}
            }
        ]);
        assert_err_contains(
            validate_partial_entities(&entities.to_string(), &schema_cedar_src()),
            &[
                "ancestor `Group::\"admins\"`",
                "of `User::\"alice\"`",
                "has unknown ancestors",
            ],
        );
    }

    #[test]
    fn validate_partial_entities_with_absent_parent_succeeds() {
        let entities = json!([
            {
                "uid": { "type": "User", "id": "alice" },
                "attrs": { "isAdmin": false },
                "parents": [ { "type": "Group", "id": "admins" } ]
            }
        ]);
        assert_matches!(
            validate_partial_entities(&entities.to_string(), &schema_cedar_src()),
            Ok(())
        );
    }

    #[test]
    fn validate_partial_entities_with_duplicate_uid_fails() {
        let entities = json!([
            {
                "uid": { "type": "User", "id": "alice" },
                "attrs": { "isAdmin": false },
                "parents": []
            },
            {
                "uid": { "type": "User", "id": "alice" },
                "attrs": { "isAdmin": true },
                "parents": []
            }
        ]);
        assert_err_contains(
            validate_partial_entities(&entities.to_string(), &schema_cedar_src()),
            &["duplicate entity entry", "User::\"alice\""],
        );
    }

    #[test]
    fn validate_type_aware_partial_request_succeeds() {
        let request = json!({
            "principal": { "type": "User" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo", "id": "door" },
            "context": { "authenticated": true },
            "schema": CEDAR_SCHEMA_SRC
        });
        assert_matches!(
            validate_type_aware_partial_request(&request.to_string()),
            Ok(())
        );
    }

    #[test]
    fn validate_type_aware_partial_request_with_unknown_context_succeeds() {
        let request = json!({
            "principal": { "type": "User", "id": "alice" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo" },
            "schema": CEDAR_SCHEMA_SRC
        });
        assert_matches!(
            validate_type_aware_partial_request(&request.to_string()),
            Ok(())
        );
    }

    #[test]
    fn validate_type_aware_partial_request_with_undeclared_principal_type_fails() {
        let request = json!({
            "principal": { "type": "Admin" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo", "id": "door" },
            "context": { "authenticated": true },
            "schema": CEDAR_SCHEMA_SRC
        });
        assert_err_contains(
            validate_type_aware_partial_request(&request.to_string()),
            &[
                "principal type `Admin`",
                "is not valid for",
                "Action::\"view\"",
            ],
        );
    }

    #[test]
    fn validate_type_aware_partial_request_with_unknown_in_context_fails() {
        let request = json!({
            "principal": { "type": "User", "id": "alice" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo", "id": "door" },
            "context": { "authenticated": { "__extn": { "fn": "unknown", "arg": "authn" } } },
            "schema": CEDAR_SCHEMA_SRC
        });
        assert_err_contains(
            validate_type_aware_partial_request(&request.to_string()),
            &["Context contains unknowns"],
        );
    }

    /// The authorization handler, end to end through the operation dispatch.
    mod authorization {
        use super::*;
        use crate::call_cedar;

        const OP: &str = "AuthorizationTypeAwarePartialOperation";

        fn input(principal: serde_json::Value, policies: serde_json::Value) -> serde_json::Value {
            json!({
                "principal": principal,
                "action": { "__entity": { "type": "Action", "id": "view" } },
                "resource": { "type": "Photo", "id": "door" },
                "context": { "authenticated": true },
                "schema": CEDAR_SCHEMA_SRC,
                "policies": policies,
                "entities": []
            })
        }

        fn call_with(input: &serde_json::Value) -> serde_json::Value {
            serde_json::from_str(&call_cedar(OP, &input.to_string())).unwrap()
        }

        fn call(principal: serde_json::Value, static_policies: &str) -> serde_json::Value {
            call_with(&input(
                principal,
                json!({ "staticPolicies": static_policies }),
            ))
        }

        fn failure_messages(answer: &serde_json::Value) -> Vec<String> {
            assert_eq!(answer["type"], "failure");
            answer["errors"]
                .as_array()
                .expect("a failure answer must carry an errors array")
                .iter()
                .map(|e| {
                    e["message"]
                        .as_str()
                        .expect("every error must carry a message")
                        .to_string()
                })
                .collect()
        }

        #[test]
        fn concrete_request_reaches_a_decision() {
            let answer = call(
                json!({ "type": "User", "id": "alice" }),
                r#"permit(principal, action, resource);"#,
            );
            assert_eq!(answer["type"], "residuals");
            assert_eq!(answer["response"]["decision"], "allow");
            // A concrete Allow still carries the permit that decided it, and that permit is trivial.
            assert_eq!(
                answer["response"]["residuals"].as_object().unwrap().len(),
                1
            );
            assert!(answer["response"]["nontrivialResiduals"]
                .as_array()
                .unwrap()
                .is_empty());
        }

        #[test]
        fn unknown_principal_id_yields_a_residual() {
            let answer = call(
                json!({ "type": "User" }),
                r#"permit(principal, action, resource) when { principal.isAdmin };"#,
            );
            assert_eq!(answer["type"], "residuals");
            assert!(answer["response"]["decision"].is_null());
            let residuals = answer["response"]["residuals"].as_object().unwrap();
            assert_eq!(residuals.len(), 1);
            let id = residuals.keys().next().unwrap();
            assert_eq!(
                answer["response"]["nontrivialResiduals"]
                    .as_array()
                    .unwrap(),
                &vec![serde_json::Value::String(id.clone())],
                "a residual with a condition left must be non-trivial"
            );
        }

        #[test]
        fn trivial_and_nontrivial_residuals_are_distinguished() {
            let answer = call(
                json!({ "type": "User" }),
                r#"permit(principal, action, resource);
                   permit(principal, action, resource) when { principal.isAdmin };"#,
            );
            let residuals = answer["response"]["residuals"].as_object().unwrap();
            let nontrivial = answer["response"]["nontrivialResiduals"]
                .as_array()
                .unwrap();
            assert_eq!(residuals.len(), 2, "every residual is reported");
            assert_eq!(nontrivial.len(), 1, "only one of them is non-trivial");

            // Assert which residual landed on which side, so an inverted split fails here. The
            // non-trivial one is the policy whose condition survived, and it is identified by the
            // attribute it still reads rather than by id, because ids are generated positionally.
            let nontrivial_id = nontrivial[0]
                .as_str()
                .expect("a residual id must be a string");
            let nontrivial_body = residuals
                .get(nontrivial_id)
                .expect("nontrivialResiduals must be a subset of residuals' keys")
                .to_string();
            assert!(
                nontrivial_body.contains("isAdmin"),
                "the non-trivial residual should still read isAdmin but was: {nontrivial_body}"
            );
            let trivial_body = residuals
                .iter()
                .find(|(id, _)| *id != nontrivial_id)
                .map(|(_, body)| body.to_string())
                .expect("the other residual should be the trivial one");
            assert!(
                !trivial_body.contains("isAdmin"),
                "the trivial residual should carry no condition but was: {trivial_body}"
            );
        }

        #[test]
        fn undeclared_principal_type_is_a_failure_not_a_panic() {
            let answer = call(
                json!({ "type": "Admin" }),
                r#"permit(principal, action, resource);"#,
            );
            let messages = failure_messages(&answer);
            assert_eq!(messages.len(), 1);
            assert!(
                messages[0].contains("Admin") && messages[0].contains("is not valid for"),
                "expected the error to say the type is not valid for the action but was: {messages:?}"
            );
        }

        #[test]
        fn a_forbid_reaches_a_deny() {
            let answer = call(
                json!({ "type": "User", "id": "alice" }),
                r#"permit(principal, action, resource);
                   forbid(principal, action, resource);"#,
            );
            assert_eq!(answer["type"], "residuals");
            assert_eq!(answer["response"]["decision"], "deny");
        }

        /// Type-aware partial evaluation validates the policy set against the schema, which plain
        /// authorization does not. A policy that parses but does not type check is a failure, not a
        /// residual.
        #[test]
        fn a_policy_that_fails_schema_validation_is_a_failure() {
            let answer = call(
                json!({ "type": "User", "id": "alice" }),
                r#"permit(principal, action, resource) when { principal.nosuchattr };"#,
            );
            let messages = failure_messages(&answer);
            assert_eq!(
                messages,
                vec!["policy failed to validate against the schema"]
            );
        }

        /// A template-linked policy residualizes as a static policy keyed by the link id, which is
        /// what lets the Java side rebuild the residuals with no templates of its own.
        #[test]
        fn a_template_link_residualizes_under_its_link_id() {
            let answer = call_with(&input(
                json!({ "type": "User" }),
                json!({
                    "templates": {
                        "tmpl": "permit(principal == ?principal, action, resource) when { principal.isAdmin };"
                    },
                    "templateLinks": [{
                        "templateId": "tmpl",
                        "newId": "linked",
                        "values": { "?principal": { "type": "User", "id": "alice" } }
                    }]
                }),
            ));
            assert_eq!(answer["type"], "residuals");
            let residuals = answer["response"]["residuals"].as_object().unwrap();
            assert_eq!(
                residuals.keys().collect::<Vec<_>>(),
                vec!["linked"],
                "the residual is keyed by the link id, not the template id"
            );
            let body = residuals["linked"].to_string();
            assert!(
                !body.contains("slot") && !body.contains('?'),
                "the link's slot should be substituted away but was: {body}"
            );
        }

        #[test]
        fn malformed_json_is_a_failure_not_a_panic() {
            let answer: serde_json::Value =
                serde_json::from_str(&call_cedar(OP, "{ not json")).unwrap();
            let messages = failure_messages(&answer);
            assert_eq!(messages.len(), 1);
            assert!(
                messages[0].contains("key must be a string"),
                "expected the error to describe the bad JSON but was: {messages:?}"
            );
        }

        #[test]
        fn a_bad_policy_set_is_a_failure() {
            let answer = call(
                json!({ "type": "User", "id": "alice" }),
                "this is not a policy",
            );
            let messages = failure_messages(&answer);
            // The underlying parse error is preserved, not flattened to the wrapper's context.
            assert_eq!(
                messages,
                vec!["failed to parse policies from string: unexpected token `is`"]
            );
        }

        /// An entity set where alice's `num` is known, so arithmetic on it can overflow.
        fn overflowing_entities() -> serde_json::Value {
            json!([{ "uid": { "type": "User", "id": "alice" },
                     "attrs": { "isAdmin": true, "num": 1 },
                     "parents": [], "tags": {} }])
        }

        const OVERFLOWS: &str = "permit(principal, action, resource) \
             when { principal has num && principal.num + 9223372036854775807 > 0 };";

        /// Residuals arrive as Cedar source, and an errored one is reported by id.
        #[test]
        fn an_errored_residual_is_reported_by_id() {
            let mut input = input(
                json!({ "type": "User", "id": "alice" }),
                json!({ "staticPolicies": OVERFLOWS }),
            );
            input["entities"] = overflowing_entities();
            let answer = call_with(&input);
            assert_eq!(answer["type"], "residuals");
            let residuals = answer["response"]["residuals"].as_object().unwrap();
            assert_eq!(residuals.len(), 1);
            let (id, src) = residuals.iter().next().unwrap();
            assert!(
                src.as_str().unwrap().contains("error()"),
                "the residual should carry Cedar's own rendering but was: {src}"
            );
            assert_eq!(
                answer["response"]["erroredResiduals"].as_array().unwrap(),
                &vec![serde_json::Value::String(id.clone())]
            );
        }

        /// An error nested inside a live residual: absent from Cedar's error buckets, but still
        /// unparseable, so it must be reported.
        #[test]
        fn a_nested_errored_residual_is_reported_by_id() {
            // No `context` key at all, so `context.authenticated` cannot be resolved and the `||`
            // cannot short-circuit — which is what keeps the error nested rather than folded away.
            let input = json!({
                "principal": { "type": "User", "id": "alice" },
                "action": { "__entity": { "type": "Action", "id": "view" } },
                "resource": { "type": "Photo", "id": "door" },
                "schema": CEDAR_SCHEMA_SRC,
                "policies": { "staticPolicies":
                    "permit(principal, action, resource) when { context.authenticated || (principal has num && principal.num + 9223372036854775807 > 0) };" },
                "entities": overflowing_entities()
            });
            let answer = call_with(&input);
            assert_eq!(answer["type"], "residuals");
            let nontrivial = answer["response"]["nontrivialResiduals"]
                .as_array()
                .unwrap();
            let errored = answer["response"]["erroredResiduals"].as_array().unwrap();
            assert_eq!(errored.len(), 1, "the nested error must be reported");
            assert_eq!(
                nontrivial, errored,
                "and it is also a non-trivial residual, which is what error_permits() would miss"
            );
        }

        #[test]
        fn a_clean_residual_is_not_reported_as_errored() {
            let answer = call(
                json!({ "type": "User" }),
                r#"permit(principal, action, resource) when { principal.isAdmin };"#,
            );
            assert!(answer["response"]["erroredResiduals"]
                .as_array()
                .unwrap()
                .is_empty());
        }

        /// Reauthorization, which runs natively so residuals never cross to Java and back.
        mod reauthorize {
            use super::*;

            const REAUTH_OP: &str = "TypeAwarePartialReauthorizeOperation";

            /// A partial call with the context unknown, completed by `concrete`.
            fn reauth(
                policies: &str,
                entities: serde_json::Value,
                concrete_principal: serde_json::Value,
                concrete_context: serde_json::Value,
                concrete_entities: serde_json::Value,
            ) -> serde_json::Value {
                let input = json!({
                    "principal": { "type": "User", "id": "alice" },
                    "action": { "__entity": { "type": "Action", "id": "view" } },
                    "resource": { "type": "Photo", "id": "door" },
                    "schema": CEDAR_SCHEMA_SRC,
                    "policies": { "staticPolicies": policies },
                    "entities": entities,
                    "concreteRequest": {
                        "principal": concrete_principal,
                        "action": { "__entity": { "type": "Action", "id": "view" } },
                        "resource": { "type": "Photo", "id": "door" },
                        "context": concrete_context
                    },
                    "concreteEntities": concrete_entities
                });
                serde_json::from_str(&call_cedar(REAUTH_OP, &input.to_string())).unwrap()
            }

            #[test]
            fn completes_an_undecided_authorization() {
                let answer = reauth(
                    "permit(principal, action, resource) when { context.authenticated };",
                    json!([]),
                    json!({ "type": "User", "id": "alice" }),
                    json!({ "authenticated": true }),
                    json!([]),
                );
                assert_eq!(answer["type"], "success");
                assert_eq!(answer["response"]["decision"], "allow");
            }

            /// The case that motivates running this natively: an errored residual cannot be
            /// serialized, but reauthorization never serializes it.
            #[test]
            fn works_when_a_residual_errored() {
                let answer = reauth(
                    OVERFLOWS,
                    overflowing_entities(),
                    json!({ "type": "User", "id": "alice" }),
                    json!({ "authenticated": true }),
                    // The partial entities named alice, so the concrete ones must too — Cedar
                    // checks entity consistency as well as request consistency.
                    json!([{ "uid": { "type": "User", "id": "alice" },
                             "attrs": { "isAdmin": true, "num": 1 }, "parents": [] }]),
                );
                assert_eq!(
                    answer["type"], "success",
                    "an errored residual must not make reauthorization fail: {answer}"
                );
                assert_eq!(answer["response"]["decision"], "deny");
                let errors = answer["response"]["diagnostics"]["errors"]
                    .as_array()
                    .expect("the erroring policy should be reported in diagnostics");
                assert_eq!(errors.len(), 1, "expected one evaluation error: {answer}");
            }

            /// Diagnostics survive the reauthorize path, not just the partial-authorization one. An entity
            /// that does not conform names the entity and the attribute; flattening to `to_string()` used
            /// to reduce this to "entity does not conform to the schema".
            #[test]
            fn preserves_diagnostics_for_a_nonconforming_concrete_entity() {
                let input = json!({
                    "principal": { "type": "User", "id": "alice" },
                    "action": { "__entity": { "type": "Action", "id": "view" } },
                    "resource": { "type": "Photo", "id": "door" },
                    "schema": CEDAR_SCHEMA_SRC,
                    "policies": { "staticPolicies": "permit(principal, action, resource);" },
                    "entities": [],
                    "concreteRequest": {
                        "principal": { "type": "User", "id": "alice" },
                        "action": { "__entity": { "type": "Action", "id": "view" } },
                        "resource": { "type": "Photo", "id": "door" },
                        "context": { "authenticated": true }
                    },
                    // `isAdmin` is required by the fixture schema and omitted here.
                    "concreteEntities": [{ "uid": { "type": "User", "id": "alice" }, "attrs": {}, "parents": [] }]
                });
                let answer: serde_json::Value =
                    serde_json::from_str(&call_cedar(REAUTH_OP, &input.to_string())).unwrap();
                assert_eq!(answer["type"], "failure");
                let message = answer["errors"][0]["message"].as_str().unwrap();
                assert!(
                    message.contains("isAdmin") && message.contains("alice"),
                    "the diagnostic must name the entity and the attribute, but was: {message}"
                );
            }

            /// A concrete request must supply a context. Defaulting an absent one to empty would answer a
            /// different question than the caller asked: a `forbid ... when { context.blocked }` would
            /// evaluate false and flip a Deny to an Allow, silently.
            #[test]
            fn rejects_a_concrete_request_with_no_context() {
                let input = json!({
                    "principal": { "type": "User", "id": "alice" },
                    "action": { "__entity": { "type": "Action", "id": "view" } },
                    "resource": { "type": "Photo", "id": "door" },
                    "schema": CEDAR_SCHEMA_SRC,
                    "policies": { "staticPolicies": "permit(principal, action, resource);" },
                    "entities": [],
                    "concreteRequest": {
                        "principal": { "type": "User", "id": "alice" },
                        "action": { "__entity": { "type": "Action", "id": "view" } },
                        "resource": { "type": "Photo", "id": "door" }
                        // no context
                    },
                    "concreteEntities": []
                });
                let answer: serde_json::Value =
                    serde_json::from_str(&call_cedar(REAUTH_OP, &input.to_string())).unwrap();
                assert_eq!(answer["type"], "failure");
                let message = answer["errors"][0]["message"].as_str().unwrap();
                assert!(
                    message.contains("context"),
                    "expected the error to name the missing context but was: {message}"
                );
            }

            /// A JSON null context is rejected too, which is what Java sends for an empty `Optional`.
            #[test]
            fn rejects_a_concrete_request_with_a_null_context() {
                let input = json!({
                    "principal": { "type": "User", "id": "alice" },
                    "action": { "__entity": { "type": "Action", "id": "view" } },
                    "resource": { "type": "Photo", "id": "door" },
                    "schema": CEDAR_SCHEMA_SRC,
                    "policies": { "staticPolicies": "permit(principal, action, resource);" },
                    "entities": [],
                    "concreteRequest": {
                        "principal": { "type": "User", "id": "alice" },
                        "action": { "__entity": { "type": "Action", "id": "view" } },
                        "resource": { "type": "Photo", "id": "door" },
                        "context": null
                    },
                    "concreteEntities": []
                });
                let answer: serde_json::Value =
                    serde_json::from_str(&call_cedar(REAUTH_OP, &input.to_string())).unwrap();
                assert_eq!(answer["type"], "failure");
            }

            /// Completing a *different* request is rejected. Authorizing a residual policy set
            /// cannot detect this and would silently grant access.
            #[test]
            fn rejects_a_request_that_does_not_match_the_partial_one() {
                let answer = reauth(
                    "permit(principal == User::\"alice\", action, resource) when { context.authenticated };",
                    json!([]),
                    json!({ "type": "User", "id": "bob" }),
                    json!({ "authenticated": true }),
                    json!([]),
                );
                assert_eq!(answer["type"], "failure");
                let message = answer["errors"][0]["message"].as_str().unwrap();
                assert!(
                    message.contains("alice") && message.contains("bob"),
                    "expected the error to name both principals but was: {message}"
                );
            }
        }

        #[test]
        fn an_unknown_request_field_is_ignored() {
            let mut input = input(
                json!({ "type": "User", "id": "alice" }),
                json!({ "staticPolicies": "permit(principal, action, resource);" }),
            );
            input["aFieldFromTheFuture"] = json!(7);
            assert_eq!(call_with(&input)["type"], "residuals");
        }
    }

    #[test]
    fn validate_partial_entity_with_json_format_schema() {
        let entity = json!({
            "uid": { "type": "User", "id": "alice" },
            "attrs": { "isAdmin": false }
        });
        assert_matches!(
            validate_partial_entity(&entity.to_string(), &schema_json()),
            Ok(())
        );

        // The same violation the Cedar-format schema catches, so a passing case above cannot be a
        // schema that parsed into something permissive.
        let mistyped = json!({
            "uid": { "type": "User", "id": "alice" },
            "attrs": { "isAdmin": 3 }
        });
        assert_err_contains(
            validate_partial_entity(&mistyped.to_string(), &schema_json()),
            &[
                "attribute `isAdmin`",
                "User::\"alice\"",
                "type mismatch",
                "expected to have type bool",
                "actually has type long",
            ],
        );
    }

    #[test]
    fn validate_partial_entities_with_json_format_schema() {
        let entities = json!([
            {
                "uid": { "type": "User", "id": "alice" },
                "attrs": { "isAdmin": false },
                "parents": [ { "type": "Group", "id": "admins" } ]
            }
        ]);
        assert_matches!(
            validate_partial_entities(&entities.to_string(), &schema_json()),
            Ok(())
        );

        let duplicated = json!([
            { "uid": { "type": "User", "id": "alice" }, "attrs": { "isAdmin": false }, "parents": [] },
            { "uid": { "type": "User", "id": "alice" }, "attrs": { "isAdmin": true }, "parents": [] }
        ]);
        assert_err_contains(
            validate_partial_entities(&duplicated.to_string(), &schema_json()),
            &["duplicate entity entry", "User::\"alice\""],
        );
    }

    #[test]
    fn validate_type_aware_partial_request_with_json_format_schema() {
        let schema: serde_json::Value = serde_json::from_str(&schema_json()).unwrap();
        let request = json!({
            "principal": { "type": "User" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo", "id": "door" },
            "context": { "authenticated": true },
            "schema": schema
        });
        assert_matches!(
            validate_type_aware_partial_request(&request.to_string()),
            Ok(())
        );

        let bad_principal = json!({
            "principal": { "type": "Admin" },
            "action": { "__entity": { "type": "Action", "id": "view" } },
            "resource": { "type": "Photo", "id": "door" },
            "context": { "authenticated": true },
            "schema": schema
        });
        assert_err_contains(
            validate_type_aware_partial_request(&bad_principal.to_string()),
            &[
                "principal type `Admin`",
                "is not valid for",
                "Action::\"view\"",
            ],
        );
    }
}

mod parsing_tests {}
