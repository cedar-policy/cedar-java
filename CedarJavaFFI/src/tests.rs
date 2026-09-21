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

#[cfg(feature = "tpe")]
mod tpe_validation_tests {
    use super::*;
    use crate::tpe::{validate_partial_entities, validate_partial_entity};
    use serde_json::json;

    const SCHEMA: &str = r#"
        entity Group;
        entity User in [Group] = { "isAdmin": Bool };
        entity Photo;
        action view appliesTo {
            principal: [User],
            resource: [Photo],
            context: { "authenticated": Bool }
        };
    "#;

    fn schema_json() -> String {
        json!(SCHEMA).to_string()
    }

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
            validate_partial_entity(&entity.to_string(), &schema_json()),
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
            validate_partial_entity(&entity.to_string(), &schema_json()),
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
            validate_partial_entity(&entity.to_string(), &schema_json()),
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
            validate_partial_entities(&entities.to_string(), &schema_json()),
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
            validate_partial_entities(&entities.to_string(), &schema_json()),
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
            validate_partial_entities(&entities.to_string(), &schema_json()),
            &["duplicate entity entry", "User::\"alice\""],
        );
    }
}

mod parsing_tests {}
