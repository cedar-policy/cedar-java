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
use cedar_policy::{
    ffi::{
        JsonValueWithNoDuplicateKeys, PolicySet as FFIPolicySet, ValidationAnswer, ValidationError,
    },
    PolicySet, Schema, SchemaFragment, SchemaWarning, ValidationMode, Validator,
};
use miette::Context;
use serde::{Deserialize, Serialize};

/// Configuration for the validation call
#[derive(Serialize, Deserialize, Debug, Default)]
#[serde(rename_all = "camelCase")]
#[serde(deny_unknown_fields)]
pub struct ValidationSettings {
    /// Used to control how a policy is validated. See comments on [`ValidationMode`].
    mode: ValidationMode,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
#[serde(deny_unknown_fields)]
pub struct LevelValidationCall {
    /// Validation settings
    #[serde(default)]
    pub validation_settings: ValidationSettings,
    /// Schema to use for validation
    pub schema: FFISchema,
    /// Policies to validate
    pub policies: FFIPolicySet,
    /// Max deref level
    pub max_deref_level: u32,
}

pub struct WithWarnings<T> {
    pub t: T,
    pub warnings: Vec<miette::Report>,
}

/// Represents a schema in either the Cedar or JSON schema format
#[derive(Debug, Serialize, Deserialize)]
#[serde(untagged)]
#[serde(
    expecting = "expected a schema in the Cedar or JSON policy format (with no duplicate keys)"
)]
pub enum FFISchema {
    /// Schema in the Cedar schema format. See <https://docs.cedarpolicy.com/schema/human-readable-schema.html>
    Cedar(String),
    /// Schema in Cedar's JSON schema format. See <https://docs.cedarpolicy.com/schema/json-schema.html>
    Json(JsonValueWithNoDuplicateKeys),
}

impl FFISchema {
    /// Parse a [`Schema`] into a [`crate::Schema`]
    pub(super) fn parse(
        self,
    ) -> Result<(Schema, Box<dyn Iterator<Item = SchemaWarning>>), miette::Report> {
        let (schema_frag, warnings) = self.parse_schema_fragment()?;
        Ok((schema_frag.try_into()?, warnings))
    }

    /// Return a [`crate::SchemaFragment`], which can be printed with `.to_string()`
    /// and converted to JSON with `.to_json()`.
    pub(super) fn parse_schema_fragment(
        self,
    ) -> Result<(SchemaFragment, Box<dyn Iterator<Item = SchemaWarning>>), miette::Report> {
        match self {
            Self::Cedar(str) => SchemaFragment::from_cedarschema_str(&str)
                .map(|(sch, warnings)| {
                    (
                        sch,
                        Box::new(warnings) as Box<dyn Iterator<Item = SchemaWarning>>,
                    )
                })
                .wrap_err("failed to parse schema from string"),
            Self::Json(val) => SchemaFragment::from_json_value(val.into())
                .map(|sch| {
                    (
                        sch,
                        Box::new(std::iter::empty()) as Box<dyn Iterator<Item = SchemaWarning>>,
                    )
                })
                .wrap_err("failed to parse schema from JSON"),
        }
    }
}

impl LevelValidationCall {
    fn get_components(
        self,
    ) -> WithWarnings<Result<(PolicySet, Schema, ValidationSettings, u32), Vec<miette::Report>>>
    {
        let mut errs = vec![];
        let policies = match self.policies.parse() {
            Ok(policies) => policies,
            Err(e) => {
                errs.extend(e);
                PolicySet::new()
            }
        };
        let pair = match self.schema.parse() {
            Ok((schema, warnings)) => Some((schema, warnings)),
            Err(e) => {
                errs.push(e);
                None
            }
        };
        match (errs.is_empty(), pair) {
            (true, Some((schema, warnings))) => WithWarnings {
                t: Ok((
                    policies,
                    schema,
                    self.validation_settings,
                    self.max_deref_level,
                )),
                warnings: warnings.map(miette::Report::new).collect(),
            },
            _ => WithWarnings {
                t: Err(errs),
                warnings: vec![],
            },
        }
    }
}

pub fn validate_with_level_json_str(json: &str) -> Result<String, serde_json::Error> {
    let ans = validate_with_level(serde_json::from_str(json)?);
    serde_json::to_string(&ans)
}

pub fn validate_with_level(call: LevelValidationCall) -> ValidationAnswer {
    match call.get_components() {
        WithWarnings {
            t: Ok((policies, schema, settings, max_deref_level)),
            warnings,
        } => {
            let validator = Validator::new(schema);

            let validation_result =
                validator.validate_with_level(&policies, settings.mode, max_deref_level);
            let validation_errors = validation_result.validation_errors();
            let validation_warnings = validation_result.validation_warnings();

            let validation_errors: Vec<ValidationError> = validation_errors
                .map(|error| ValidationError {
                    policy_id: error.policy_id().clone(),
                    error: miette::Report::new(error.clone()).into(),
                })
                .collect();
            let validation_warnings: Vec<ValidationError> = validation_warnings
                .map(|error| ValidationError {
                    policy_id: error.policy_id().clone(),
                    error: miette::Report::new(error.clone()).into(),
                })
                .collect();
            ValidationAnswer::Success {
                validation_errors,
                validation_warnings,
                other_warnings: warnings.into_iter().map(Into::into).collect(),
            }
        }
        WithWarnings {
            t: Err(errors),
            warnings,
        } => ValidationAnswer::Failure {
            errors: errors.into_iter().map(Into::into).collect(),
            warnings: warnings.into_iter().map(Into::into).collect(),
        },
    }
}

#[cfg(test)]
mod test {
    use crate::helpers::validate_with_level;
    use cedar_policy::ffi::{ValidationAnswer, ValidationError};
    use cool_asserts::assert_matches;
    use serde_json::json;

    #[track_caller]
    fn assert_validates_without_errors(json: serde_json::Value) {
        let ans = validate_with_level(serde_json::from_value(json).unwrap());
        let ans_val = serde_json::to_value(ans).unwrap();
        let result: Result<ValidationAnswer, _> = serde_json::from_value(ans_val);
        assert_matches!(result, Ok(ValidationAnswer::Success { validation_errors, validation_warnings: _, other_warnings: _ }) => {
            assert_eq!(validation_errors.len(), 0, "Unexpected validation errors: {validation_errors:?}");
        });
    }

    #[track_caller]
    fn assert_validates_with_errors(json: serde_json::Value) -> Vec<ValidationError> {
        let ans = validate_with_level(serde_json::from_value(json).unwrap());
        let ans_val = serde_json::to_value(ans).unwrap();
        assert_matches!(ans_val.get("validationErrors"), Some(_)); // should be present, with this camelCased name
        assert_matches!(ans_val.get("validationWarnings"), Some(_)); // should be present, with this camelCased name
        let result: Result<ValidationAnswer, _> = serde_json::from_value(ans_val);
        assert_matches!(result, Ok(ValidationAnswer::Success { validation_errors, validation_warnings: _, other_warnings: _ }) => {
            validation_errors
        })
    }

    #[test]
    fn test_correct_policy_validates_without_errors() {
        let json = json!({
        "schema": { "": {
          "entityTypes": {
            "User": {
              "memberOfTypes": [ "UserGroup" ],
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
              "memberOfTypes": [ "Album", "Account" ],
              "shape":{
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
              "memberOfTypes": [ "Album", "Account" ]
            },
            "Account": { },
            "UserGroup": {}
          },
          "actions": {
            "readOnly": { },
            "readWrite": { },
            "createAlbum": {
              "appliesTo": {
                "resourceTypes": [ "Account", "Album" ],
                "principalTypes": [ "User" ]
              }
            },
            "addPhotoToAlbum": {
              "appliesTo": {
                "resourceTypes": [ "Album" ],
                "principalTypes": [ "User" ]
              }
            },
            "viewPhoto": {
              "appliesTo": {
                "resourceTypes": [ "Photo" ],
                "principalTypes": [ "User" ]
              }
            },
            "viewComments": {
              "appliesTo": {
                "resourceTypes": [ "Photo" ],
                "principalTypes": [ "User" ]
              }
            }
          }
        }},
        "policies": {
          "staticPolicies": {
            "policy0": "permit(principal in UserGroup::\"alice_friends\", action == Action::\"viewPhoto\", resource) when {principal in resource.owner.friend};"
          }
        },
        "maxDerefLevel": 2});
        assert_validates_without_errors(json);
    }

    #[test]
    fn test_invalid_policy_validates_with_errors() {
        let json = json!({
        "schema": { "": {
          "entityTypes": {
            "User": {
              "memberOfTypes": [ "UserGroup" ],
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
              "memberOfTypes": [ "Album", "Account" ],
              "shape":{
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
              "memberOfTypes": [ "Album", "Account" ]
            },
            "Account": { },
            "UserGroup": {}
          },
          "actions": {
            "readOnly": { },
            "readWrite": { },
            "createAlbum": {
              "appliesTo": {
                "resourceTypes": [ "Account", "Album" ],
                "principalTypes": [ "User" ]
              }
            },
            "addPhotoToAlbum": {
              "appliesTo": {
                "resourceTypes": [ "Album" ],
                "principalTypes": [ "User" ]
              }
            },
            "viewPhoto": {
              "appliesTo": {
                "resourceTypes": [ "Photo" ],
                "principalTypes": [ "User" ]
              }
            },
            "viewComments": {
              "appliesTo": {
                "resourceTypes": [ "Photo" ],
                "principalTypes": [ "User" ]
              }
            }
          }
        }},
        "policies": {
          "staticPolicies": {
            "policy0": "permit(principal in UserGroup::\"alice_friends\", action == Action::\"viewPhoto\", resource) when {principal in resource.owner.friend};"
          }
        },
        "maxDerefLevel": 1});

        let errs = assert_validates_with_errors(json);

        assert_eq!(errs.len(), 1, "expected 1 error but saw {}", errs.len());
        assert_eq!(errs[0].error.message, "for policy `policy0`, this policy requires level 2, which exceeds the maximum allowed level (1)");
    }
}
