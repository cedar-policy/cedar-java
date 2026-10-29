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
    ffi::{DetailedError, PolicySet as FFIPolicySet, Schema as FFISchema},
    Context, Decision, EntityId, EntityTypeName, EntityUid, PartialEntities, PartialEntityUid,
    PartialRequest, Schema,
};
#[cfg(feature = "tpe")]
use serde::Deserialize;
#[cfg(feature = "tpe")]
use serde::Serialize;
#[cfg(feature = "tpe")]
use serde_json::Value;
#[cfg(feature = "tpe")]
use std::collections::{HashMap, HashSet};
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

/// The authorization call the Java engine sends. Deliberately **not**
/// `#[serde(deny_unknown_fields)]`: a newer Java sending a field an older `.so` does not know about
/// must still work, and `CEDAR_JAVA_FFI_LIB` makes that skew reachable.
#[cfg(feature = "tpe")]
#[derive(Debug, Deserialize)]
struct TypeAwarePartialAuthCall {
    principal: PartialEntityUidJson,
    action: Value,
    resource: PartialEntityUidJson,
    #[serde(default)]
    context: Option<Value>,
    schema: FFISchema,
    policies: FFIPolicySet,
    entities: Value,
}

/// The residual set, mirroring the field names `TypeAwarePartialAuthorizationSuccessResponse` binds.
#[cfg(feature = "tpe")]
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct TypeAwarePartialAuthSuccess {
    /// `None` becomes JSON `null`, meaning no conclusive decision.
    decision: Option<Decision>,
    /// Every residual, as Cedar source keyed by policy id. Source rather than EST because a residual
    /// whose evaluation errored contains a node Cedar can write but not read back, in either form.
    /// Sending source keeps it faithful and lets Java hold it without parsing.
    residuals: HashMap<String, String>,
    /// Ids only. The bodies are already in `residuals`, and this is a subset of its keys.
    nontrivial_residuals: HashSet<String>,
    /// Ids of the residuals that *contain* a subexpression Cedar already knows would error. Cedar
    /// cannot parse these back, so the `Policy` Java builds from one carries its source and nothing
    /// more.
    ///
    /// Containing an error node does not mean the policy will error: `unknown || error` decides
    /// normally when the left side turns out true. Derived from the PST rather than
    /// `error_permits`/`error_forbids`, which report only policies that reduced *entirely* to an
    /// error and so miss this nested case.
    errored_residuals: HashSet<String>,
}

/// The reauthorization call. Carries everything the original type-aware partial authorization needed,
/// plus the now-known request and entities.
///
/// The partial side is resent rather than the residuals because Cedar's `reauthorize` validates the
/// concrete request against the partial one — residuals alone cannot detect a caller completing a
/// *different* request, which silently grants access the original policies would have denied.
#[cfg(feature = "tpe")]
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TypeAwarePartialReauthorizeCall {
    #[serde(flatten)]
    partial: TypeAwarePartialAuthCall,
    concrete_request: ConcreteRequestJson,
    concrete_entities: Value,
}

/// A fully concrete request.
#[cfg(feature = "tpe")]
#[derive(Debug, Deserialize)]
struct ConcreteRequestJson {
    principal: Value,
    action: Value,
    resource: Value,
    /// Required, unlike the partial request's context where absence means *unknown*. A concrete request
    /// has no unknowns, so an absent context is a caller error — defaulting it to empty would answer a
    /// different question than the caller asked, and silently.
    context: Value,
}

#[cfg(feature = "tpe")]
#[derive(Debug, Serialize)]
#[serde(tag = "type")]
enum TypeAwarePartialAuthAnswer {
    #[serde(rename = "residuals")]
    Success {
        response: TypeAwarePartialAuthSuccess,
        warnings: Vec<String>,
    },
    #[serde(rename = "failure")]
    Failure {
        errors: Vec<DetailedError>,
        warnings: Vec<String>,
    },
}

/// Wrap a `miette::Diagnostic` error as a `DetailedError`, preserving its help text, code, severity,
/// source spans and `related` errors. Policy-set validation puts the actual failures in `related`, so
/// flattening to `to_string()` would report only "policy failed to validate against the schema".
#[cfg(feature = "tpe")]
fn detailed<E>(e: E) -> DetailedError
where
    E: miette::Diagnostic + Send + Sync + 'static,
{
    DetailedError::from(miette::Report::new(e))
}

#[cfg(feature = "tpe")]
fn tpe_failure_detailed(errors: Vec<DetailedError>) -> TypeAwarePartialAuthAnswer {
    TypeAwarePartialAuthAnswer::Failure {
        errors,
        warnings: vec![],
    }
}

/// A `DetailedError` carrying only a message, for the error types on these paths that are not
/// `miette::Diagnostic`s — `serde_json::Error` and the boxed errors from `PartialEntityUidJson::parse`.
#[cfg(feature = "tpe")]
fn plain(message: String) -> DetailedError {
    DetailedError {
        message,
        help: None,
        code: None,
        url: None,
        severity: None,
        source_locations: vec![],
        related: vec![],
    }
}

/// Cedar 4.11's `TpeError` is not a `miette::Diagnostic`, and its `Validation` variant displays as a
/// `{:#?}` dump. Rebuild the shape later Cedar versions produce — one "failed to validate" error with
/// each validation error, help text and source span intact, under `related` — so callers see the same
/// failure on either Cedar line. The other variants carry only a message.
#[cfg(feature = "tpe")]
fn tpe_error_details(e: cedar_policy::tpe_err::TpeError) -> Vec<DetailedError> {
    use cedar_policy::tpe_err::TpeError;
    match e {
        TpeError::Validation(errs) => vec![DetailedError {
            related: errs.into_iter().map(detailed).collect(),
            ..plain("policy failed to validate against the schema".to_string())
        }],
        e => vec![plain(e.to_string())],
    }
}

#[cfg(feature = "tpe")]
fn tpe_failure(message: String) -> TypeAwarePartialAuthAnswer {
    tpe_failure_detailed(vec![plain(message)])
}

/// Run type-aware partial authorization. Every failure that a caller could have caused — malformed
/// JSON, an unparseable schema or policy set, a request that does not type check, entities that do
/// not conform — comes back as a `Failure` answer rather than an error, because `call_cedar`
/// panics on an `Err` and the panic reaches Java as a non-JSON string.
#[cfg(feature = "tpe")]
pub fn json_type_aware_partial_is_authorized(input: &str) -> serde_json::Result<String> {
    serde_json::to_string(&type_aware_partial_is_authorized(input))
}

/// The inputs a type-aware partial evaluation needs, parsed and validated. Shared so that
/// authorization and reauthorization cannot drift in how they interpret the same payload.
#[cfg(feature = "tpe")]
struct ParsedCall {
    policies: cedar_policy::PolicySet,
    schema: Schema,
    request: PartialRequest,
    entities: PartialEntities,
}

#[cfg(feature = "tpe")]
impl TypeAwarePartialAuthCall {
    /// Every error here is caller-caused, so the diagnostics are returned for the caller to funnel into
    /// a `Failure` answer. One `DetailedError` per underlying report, each keeping its help text and
    /// source span.
    fn parse_all(self) -> std::result::Result<ParsedCall, Vec<DetailedError>> {
        let schema = parse_schema(self.schema).map_err(|e| vec![plain(e.to_string())])?;
        let policies = self.policies.parse().map_err(|errs| {
            errs.into_iter()
                .map(DetailedError::from)
                .collect::<Vec<_>>()
        })?;
        let action = EntityUid::from_json(self.action).map_err(|e| vec![detailed(e)])?;
        let principal = self
            .principal
            .parse()
            .map_err(|e| vec![plain(e.to_string())])?;
        let resource = self
            .resource
            .parse()
            .map_err(|e| vec![plain(e.to_string())])?;
        let context = self
            .context
            .map(|c| Context::from_json_value(c, Some((&schema, &action))))
            .transpose()
            .map_err(|e| vec![detailed(e)])?;
        // Cedar 4.11's `tpe_err::EntitiesError` is not a `miette::Diagnostic`.
        let entities = PartialEntities::from_json_value(self.entities, &schema)
            .map_err(|e| vec![plain(e.to_string())])?;
        let request = PartialRequest::new(principal, action, resource, context, &schema)
            .map_err(|e| vec![detailed(e)])?;
        Ok(ParsedCall {
            policies,
            schema,
            request,
            entities,
        })
    }
}

/// Whether this residual contains a known-error subexpression, at any depth.
///
/// This is about *representability*, not evaluation: Cedar's own `has_error` doc notes it "says
/// nothing about whether the expression will, or even can, evaluate to an error", and is meant for
/// deciding whether an expression can be written as valid Cedar text. That is exactly the question
/// here, because such a residual cannot be parsed back.
///
/// Uses the PST rather than `TpeResponse::error_permits`/`error_forbids`, which report only policies
/// that reduced *entirely* to an error. An error nested inside a live residual — `unknown || error` —
/// is absent from those buckets but still makes the residual unparseable.
#[cfg(feature = "tpe")]
fn residual_errors(policy: &cedar_policy::Policy) -> std::result::Result<bool, String> {
    use cedar_policy::pst::Clause;
    let pst = policy.to_pst().map_err(|e| e.to_string())?;
    Ok(pst.body().clauses().iter().any(|clause| match clause {
        Clause::When(e) | Clause::Unless(e) => e.has_error(),
    }))
}

#[cfg(feature = "tpe")]
fn type_aware_partial_is_authorized(input: &str) -> TypeAwarePartialAuthAnswer {
    macro_rules! or_failure {
        ($e:expr) => {
            match $e {
                Ok(v) => v,
                Err(e) => return tpe_failure(e.to_string()),
            }
        };
    }

    let call: TypeAwarePartialAuthCall = or_failure!(serde_json::from_str(input));
    let ParsedCall {
        policies,
        schema,
        request,
        entities,
    } = match call.parse_all() {
        Ok(parsed) => parsed,
        Err(errors) => return tpe_failure_detailed(errors),
    };
    let response = match policies.tpe(&request, &entities, &schema) {
        Ok(response) => response,
        Err(e) => return tpe_failure_detailed(tpe_error_details(e)),
    };

    let mut residuals = HashMap::new();
    let mut errored_residuals = HashSet::new();
    // In Cedar 4.11 `residual_policies` yields every residual, trivial or not.
    for p in response.residual_policies() {
        let id = p.id().to_string();
        if or_failure!(residual_errors(&p)) {
            errored_residuals.insert(id.clone());
        }
        residuals.insert(id, p.to_string());
    }
    // `nontrivial_residual_policies` deliberately, not `residual_policies`: this spelling means the
    // non-trivial subset in Cedar 4.11 *and* 4.12+, while `residual_policies` means every residual in
    // 4.11 and the non-trivial subset in 4.12+. Deprecated here, but a deprecation warning is cheaper
    // than a rename that compiles clean and silently inverts the set on the 4.11 release branch.
    #[allow(deprecated)]
    let nontrivial_residuals = response
        .nontrivial_residual_policies()
        .map(|p| p.id().to_string())
        .collect();

    TypeAwarePartialAuthAnswer::Success {
        response: TypeAwarePartialAuthSuccess {
            decision: response.decision(),
            residuals,
            nontrivial_residuals,
            errored_residuals,
        },
        // TpeResponse exposes no warnings, so there is nothing to populate this with yet.
        warnings: vec![],
    }
}

/// Complete a type-aware partial authorization now that the unknowns are known.
///
/// Runs entirely natively: the residuals never cross to Java and back. That is deliberate — Cedar's
/// `reauthorize` checks the concrete request against the partial one and rejects a mismatch, which
/// authorizing a residual policy set cannot do.
#[cfg(feature = "tpe")]
pub fn json_type_aware_partial_reauthorize(input: &str) -> serde_json::Result<String> {
    serde_json::to_string(&type_aware_partial_reauthorize(input))
}

#[cfg(feature = "tpe")]
fn auth_failure(errors: Vec<DetailedError>) -> cedar_policy::ffi::AuthorizationAnswer {
    cedar_policy::ffi::AuthorizationAnswer::Failure {
        errors,
        warnings: vec![],
    }
}

#[cfg(feature = "tpe")]
fn type_aware_partial_reauthorize(input: &str) -> cedar_policy::ffi::AuthorizationAnswer {
    /// For errors that implement `miette::Diagnostic`, so the help text, source spans and `related`
    /// errors survive. Entity-conformance failures put the entity and attribute in the cause chain,
    /// which `to_string()` drops — see `detailed`.
    macro_rules! or_failure {
        ($e:expr) => {
            match $e {
                Ok(v) => v,
                Err(e) => return auth_failure(vec![detailed(e)]),
            }
        };
    }
    /// For the error types on this path that are not `miette::Diagnostic`s: `serde_json::Error`, and
    /// `TpeReauthorizationError`, which Cedar does not derive `Diagnostic` on even though every one of
    /// its inner variants does.
    macro_rules! or_failure_plain {
        ($e:expr) => {
            match $e {
                Ok(v) => v,
                Err(e) => return auth_failure(vec![plain(e.to_string())]),
            }
        };
    }

    let call: TypeAwarePartialReauthorizeCall = or_failure_plain!(serde_json::from_str(input));
    let ParsedCall {
        policies,
        schema,
        request,
        entities,
    } = match call.partial.parse_all() {
        Ok(parsed) => parsed,
        Err(errors) => return auth_failure(errors),
    };
    let response = match policies.tpe(&request, &entities, &schema) {
        Ok(response) => response,
        Err(e) => return auth_failure(tpe_error_details(e)),
    };

    let concrete = call.concrete_request;
    let action = or_failure!(EntityUid::from_json(concrete.action));
    let context = or_failure!(Context::from_json_value(
        concrete.context,
        Some((&schema, &action))
    ));
    let concrete_request = or_failure!(cedar_policy::Request::new(
        or_failure!(EntityUid::from_json(concrete.principal)),
        action,
        or_failure!(EntityUid::from_json(concrete.resource)),
        context,
        Some(&schema),
    ));
    let concrete_entities = or_failure!(cedar_policy::Entities::from_json_value(
        call.concrete_entities,
        Some(&schema)
    ));

    match response.reauthorize(&concrete_request, &concrete_entities) {
        Ok(response) => cedar_policy::ffi::AuthorizationAnswer::Success {
            response: response.into(),
            warnings: vec![],
        },
        Err(e) => auth_failure(vec![plain(e.to_string())]),
    }
}

/// A serialized failure answer carrying [`TPE_DISABLED`]. Both TPE operations stay in `call_cedar`'s
/// dispatch table when the feature is off so that the Java layer receives something it can
/// deserialize, and recognises the token; falling through to the unsupported-operation arm instead
/// would produce an `errors` array of bare strings that the response types cannot bind.
#[cfg(not(feature = "tpe"))]
fn tpe_disabled_answer(kind: &str) -> serde_json::Result<String> {
    serde_json::to_string(&serde_json::json!({
        "type": kind,
        "errors": [{
            "message": TPE_DISABLED,
            "help": null,
            "code": null,
            "url": null,
            "severity": null,
            "sourceLocations": [],
            "related": []
        }],
        "warnings": []
    }))
}

#[cfg(not(feature = "tpe"))]
pub fn json_type_aware_partial_is_authorized(_input: &str) -> serde_json::Result<String> {
    tpe_disabled_answer("failure")
}

#[cfg(not(feature = "tpe"))]
pub fn json_type_aware_partial_reauthorize(_input: &str) -> serde_json::Result<String> {
    tpe_disabled_answer("failure")
}
