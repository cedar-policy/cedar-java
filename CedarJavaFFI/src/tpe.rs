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
use cedar_policy::{
    ffi::Schema as FFISchema, Context, EntityId, EntityTypeName, EntityUid, PartialEntities,
    PartialEntityUid, PartialRequest, Schema,
};
#[cfg(feature = "tpe")]
use serde::Deserialize;
#[cfg(feature = "tpe")]
use serde_json::Value;
#[cfg(feature = "tpe")]
use std::str::FromStr;

use crate::utils::Result;

/// Error message returned when this library was built without the `tpe` feature.
/// The Java layer matches on the leading token to raise
/// `MissingExperimentalFeatureException`.
#[cfg(not(feature = "tpe"))]
const TPE_DISABLED: &str =
    "TypeAwarePartialEvaluationNotEnabled: the `tpe` feature is disabled in this build";

/// A partial entity UID, whose entity id may be unknown. Cedar has no serde for
/// `PartialEntityUid`, so the wire form is spelled out here.
#[cfg(feature = "tpe")]
#[derive(Debug, Deserialize)]
struct PartialEntityUidJson {
    #[serde(rename = "type")]
    ty: String,
    id: Option<String>,
}

#[cfg(feature = "tpe")]
impl PartialEntityUidJson {
    fn parse(self) -> Result<PartialEntityUid> {
        Ok(PartialEntityUid::new(
            EntityTypeName::from_str(&self.ty)?,
            self.id.map(EntityId::new),
        ))
    }
}

/// A partial request, whose principal id, resource id and context may be unknown
#[cfg(feature = "tpe")]
#[derive(Debug, Deserialize)]
struct TypeAwarePartialRequestJson {
    principal: PartialEntityUidJson,
    action: Value,
    resource: PartialEntityUidJson,
    context: Option<Value>,
    schema: FFISchema,
}

#[cfg(feature = "tpe")]
fn parse_schema(schema: FFISchema) -> Result<Schema> {
    match schema {
        FFISchema::Cedar(src) => Ok(Schema::from_cedarschema_str(&src)?.0),
        FFISchema::Json(json) => Ok(Schema::from_json_value(json.into())?),
    }
}

#[cfg(feature = "tpe")]
fn parse_schema_str(schema_json: &str) -> Result<Schema> {
    parse_schema(serde_json::from_str(schema_json)?)
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

/// Validates a partial request against the schema carried by the request itself.
#[cfg(feature = "tpe")]
pub fn validate_type_aware_partial_request(request_json: &str) -> Result<()> {
    let request: TypeAwarePartialRequestJson = serde_json::from_str(request_json)?;
    let schema = parse_schema(request.schema)?;
    let action = EntityUid::from_json(request.action)?;
    let principal = request.principal.parse()?;
    let resource = request.resource.parse()?;
    let context = request
        .context
        .map(|c| Context::from_json_value(c, Some((&schema, &action))))
        .transpose()?;
    PartialRequest::new(principal, action, resource, context, &schema)?;
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

#[cfg(not(feature = "tpe"))]
pub fn validate_type_aware_partial_request(_request_json: &str) -> Result<()> {
    Err(TPE_DISABLED.into())
}
