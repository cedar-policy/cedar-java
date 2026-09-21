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

//! Validation helpers for type-aware partial evaluation inputs.

#[cfg(feature = "tpe")]
use cedar_policy::{ffi::Schema as FFISchema, PartialEntities, Schema};
#[cfg(feature = "tpe")]
use serde_json::Value;

use crate::utils::Result;

/// Error message returned when this library was built without the `tpe` feature.
/// The Java layer matches on the leading token to raise
/// `MissingExperimentalFeatureException`.
#[cfg(not(feature = "tpe"))]
const TPE_DISABLED: &str =
    "TypeAwarePartialEvaluationNotEnabled: the `tpe` feature is disabled in this build";

#[cfg(feature = "tpe")]
fn parse_schema_str(schema_json: &str) -> Result<Schema> {
    match serde_json::from_str(schema_json)? {
        FFISchema::Cedar(src) => Ok(Schema::from_cedarschema_str(&src)?.0),
        FFISchema::Json(json) => Ok(Schema::from_json_value(json.into())?),
    }
}

/// Validates a single partial entity against a schema. Note that this cannot
/// detect problems that span multiple entities, such as an ancestor with
/// unknown ancestors.
#[cfg(feature = "tpe")]
pub fn validate_partial_entity(entity_json: &str, schema_json: &str) -> Result<()> {
    let schema = parse_schema_str(schema_json)?;
    let entity: Value = serde_json::from_str(entity_json)?;
    PartialEntities::from_json_value(Value::Array(vec![entity]), &schema)?;
    Ok(())
}

/// Validates a collection of partial entities against a schema.
#[cfg(feature = "tpe")]
pub fn validate_partial_entities(entities_json: &str, schema_json: &str) -> Result<()> {
    let schema = parse_schema_str(schema_json)?;
    let entities: Value = serde_json::from_str(entities_json)?;
    PartialEntities::from_json_value(entities, &schema)?;
    Ok(())
}

#[cfg(not(feature = "tpe"))]
pub fn validate_partial_entity(_entity_json: &str, _schema_json: &str) -> Result<()> {
    Err(TPE_DISABLED.into())
}

#[cfg(not(feature = "tpe"))]
pub fn validate_partial_entities(_entities_json: &str, _schema_json: &str) -> Result<()> {
    Err(TPE_DISABLED.into())
}
