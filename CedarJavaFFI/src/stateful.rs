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

//! Stateful ("cached") authorization: the policy set and schema were parsed once by
//! `PolicySet.cache()` / `Schema.cache()` and are looked up by key, so a call carries only the
//! request and the entities.
//!
//! Everything else is parsed exactly as the stateless operations in `cedar_policy::ffi` parse it:
//! through the same FFI wrapper types, in the same order, with every error converted by
//! `DetailedError::from(miette::Report)`. That keeps the errors identical on both paths. Converting
//! with `to_string()` instead keeps only the top-level message and drops the cause chain, `help`,
//! `code`, source locations and `related` errors.

use std::sync::Arc;

use cedar_policy::ffi::{
    AuthorizationAnswer, Context as FfiContext, DetailedError, Entities as FfiEntities,
    EntityUid as FfiEntityUid,
};
#[cfg(feature = "partial-eval")]
use cedar_policy::ffi::{PartialAuthorizationAnswer, ResidualResponse};
use cedar_policy::{PolicySet, Request, Schema};
use serde::Deserialize;
use serde_json::Value;

use crate::interface::{AUTHORIZER, CACHED_POLICY_SETS, CACHED_SCHEMAS};

/// The policy set and optional schema a stateful call refers to.
struct Cached {
    policies: Arc<PolicySet>,
    schema: Option<Arc<Schema>>,
}

/// Look up the cached policy set and schema. A miss is reported on its own, before anything else is
/// parsed, so that the Java layer can tell it apart from a caller error and fall back to the
/// stateless path. The Java layer matches these messages; keep the two in sync.
fn lookup(policy_set_id: &str, schema_name: Option<&str>) -> Result<Cached, String> {
    let policies = CACHED_POLICY_SETS
        .get(policy_set_id)
        .map(|entry| Arc::clone(entry.value()))
        .ok_or_else(|| format!("preparsed policy set '{policy_set_id}' not found"))?;
    let schema = schema_name
        .map(|name| {
            CACHED_SCHEMAS
                .get(name)
                .map(|entry| Arc::clone(entry.value()))
                .ok_or_else(|| format!("preparsed schema '{name}' not found"))
        })
        .transpose()?;
    Ok(Cached { policies, schema })
}

fn cache_miss(message: String) -> DetailedError {
    DetailedError::from(miette::Report::msg(message))
}

fn details(errs: Vec<miette::Report>) -> Vec<DetailedError> {
    errs.into_iter().map(Into::into).collect()
}

fn default_true() -> bool {
    true
}

/// What an absent `context` deserialized to before the stateful path parsed through the FFI types.
/// Kept so that callers which omit it see the same behaviour as before.
fn null_context() -> FfiContext {
    FfiContext::from(Value::Null)
}

/// The concrete stateful call. Deliberately not `deny_unknown_fields`, so that a newer Java can add
/// fields without breaking an older native library.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct StatefulAuthCall {
    principal: FfiEntityUid,
    action: FfiEntityUid,
    resource: FfiEntityUid,
    #[serde(default = "null_context")]
    context: FfiContext,
    preparsed_policy_set_id: String,
    preparsed_schema_name: Option<String>,
    #[serde(default = "default_true")]
    validate_request: bool,
    entities: FfiEntities,
}

/// Concrete authorization against a cached policy set and optional cached schema. Returns the same
/// JSON as `AuthorizationOperation`.
pub(crate) fn json_stateful_is_authorized(input: &str) -> serde_json::Result<String> {
    let call: StatefulAuthCall = serde_json::from_str(input)?;
    serde_json::to_string(&stateful_is_authorized(call))
}

fn stateful_is_authorized(call: StatefulAuthCall) -> AuthorizationAnswer {
    let failure = |errors: Vec<DetailedError>| AuthorizationAnswer::Failure {
        errors,
        warnings: vec![],
    };
    let Cached { policies, schema } = match lookup(
        &call.preparsed_policy_set_id,
        call.preparsed_schema_name.as_deref(),
    ) {
        Ok(cached) => cached,
        Err(miss) => return failure(vec![cache_miss(miss)]),
    };
    let schema = schema.as_deref();

    // From here on this mirrors `AuthorizationCall::parse` in `cedar_policy::ffi`, minus the schema
    // and policy parsing the cache has already done.
    let mut errs = vec![];
    let principal = call
        .principal
        .parse(Some("principal"))
        .map_err(|e| errs.push(e));
    let action = call.action.parse(Some("action")).map_err(|e| errs.push(e));
    let resource = call
        .resource
        .parse(Some("resource"))
        .map_err(|e| errs.push(e));
    let (Ok(principal), Ok(action), Ok(resource)) = (principal, action, resource) else {
        return failure(details(errs));
    };

    let context = match call.context.parse(schema, Some(&action)) {
        Ok(context) => context,
        Err(e) => return failure(details(vec![e])),
    };

    let validation_schema = if call.validate_request { schema } else { None };
    let request = Request::new(principal, action, resource, context, validation_schema)
        .map_err(|e| errs.push(e.into()));
    let entities = call.entities.parse(schema).map_err(|e| errs.push(e));

    match (request, entities) {
        (Ok(request), Ok(entities)) => AuthorizationAnswer::Success {
            response: AUTHORIZER
                .with(|authorizer| authorizer.is_authorized(&request, &policies, &entities))
                .into(),
            warnings: vec![],
        },
        _ => failure(details(errs)),
    }
}

/// The partial stateful call. An absent principal, action or resource is unknown, as in
/// `AuthorizationPartialOperation`.
#[cfg(feature = "partial-eval")]
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct StatefulPartialAuthCall {
    principal: Option<FfiEntityUid>,
    action: Option<FfiEntityUid>,
    resource: Option<FfiEntityUid>,
    context: FfiContext,
    preparsed_policy_set_id: String,
    preparsed_schema_name: Option<String>,
    #[serde(default = "default_true")]
    validate_request: bool,
    entities: FfiEntities,
}

/// Partial authorization against a cached policy set and optional cached schema. Returns the same
/// JSON as `AuthorizationPartialOperation`.
#[cfg(feature = "partial-eval")]
pub(crate) fn json_stateful_is_authorized_partial(input: &str) -> serde_json::Result<String> {
    let call: StatefulPartialAuthCall = serde_json::from_str(input)?;
    serde_json::to_string(&stateful_is_authorized_partial(call))
}

#[cfg(feature = "partial-eval")]
fn stateful_is_authorized_partial(call: StatefulPartialAuthCall) -> PartialAuthorizationAnswer {
    let failure = |errors: Vec<DetailedError>| PartialAuthorizationAnswer::Failure {
        errors,
        warnings: vec![],
    };
    let Cached { policies, schema } = match lookup(
        &call.preparsed_policy_set_id,
        call.preparsed_schema_name.as_deref(),
    ) {
        Ok(cached) => cached,
        Err(miss) => return failure(vec![cache_miss(miss)]),
    };
    let schema = schema.as_deref();

    // From here on this mirrors `PartialAuthorizationCall::parse` in `cedar_policy::ffi`, minus the
    // schema and policy parsing the cache has already done. Note that it parses the entities before
    // building the request, so their errors come first.
    let mut errs = vec![];
    let principal = call
        .principal
        .map(|uid| uid.parse(Some("principal")))
        .transpose()
        .map_err(|e| errs.push(e));
    let action = call
        .action
        .map(|uid| uid.parse(Some("action")))
        .transpose()
        .map_err(|e| errs.push(e));
    let resource = call
        .resource
        .map(|uid| uid.parse(Some("resource")))
        .transpose()
        .map_err(|e| errs.push(e));
    let (Ok(principal), Ok(action), Ok(resource)) = (principal, action, resource) else {
        return failure(details(errs));
    };

    let context = match call.context.parse(schema, action.as_ref()) {
        Ok(context) => context,
        Err(e) => return failure(details(vec![e])),
    };

    let entities = call.entities.parse(schema).map_err(|e| errs.push(e));

    let mut builder = Request::builder();
    if let Some(p) = principal {
        builder = builder.principal(p);
    }
    if let Some(a) = action {
        builder = builder.action(a);
    }
    if let Some(r) = resource {
        builder = builder.resource(r);
    }
    builder = builder.context(context);
    let request = match schema {
        Some(schema) if call.validate_request => builder
            .schema(schema)
            .build()
            .map_err(|e| errs.push(e.into())),
        _ => Ok(builder.build()),
    };

    let (Ok(request), Ok(entities)) = (request, entities) else {
        return failure(details(errs));
    };
    let response = AUTHORIZER
        .with(|authorizer| authorizer.is_authorized_partial(&request, &policies, &entities));
    match ResidualResponse::try_from(response) {
        Ok(response) => PartialAuthorizationAnswer::Residuals {
            response: Box::new(response),
            warnings: vec![],
        },
        Err(e) => failure(vec![miette::Report::new_boxed(e).into()]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::interface::call_cedar;
    use cedar_policy::ffi::{PolicySet as FfiPolicySet, Schema as FfiSchema};
    use serde_json::json;

    const SCHEMA: &str = r#"
        namespace NS {
            entity User = { "tenantId": String };
            entity Field = { "tenantId": String };
            action View appliesTo {
                principal: [User],
                resource: [Field],
                context: { "authenticated"?: Bool }
            };
        }
    "#;

    fn policies() -> Value {
        json!({
            "staticPolicies": {
                "p1": "permit(principal, action, resource) when { principal.tenantId == resource.tenantId };",
                "p2": "forbid(principal, action, resource) when { context has authenticated && !context.authenticated };"
            },
            "templates": {},
            "templateLinks": []
        })
    }

    /// Cache the fixture policy set and schema under ids unique to `test`, as `PolicySet.cache()` and
    /// `Schema.cache()` do, and return those ids. Tests run in parallel and share the global caches.
    fn cache(test: &str) -> (String, String) {
        let policy_set_id = format!("stateful-test-policies-{test}");
        let schema_name = format!("stateful-test-schema-{test}");
        let parsed: FfiPolicySet = serde_json::from_value(policies()).unwrap();
        CACHED_POLICY_SETS.insert(policy_set_id.clone(), Arc::new(parsed.parse().unwrap()));
        let (schema, _) = Schema::from_cedarschema_str(SCHEMA).unwrap();
        CACHED_SCHEMAS.insert(schema_name.clone(), Arc::new(schema));
        (policy_set_id, schema_name)
    }

    /// Run `op` on the stateless payload `call`, and `stateful_op` on the same payload with the policy
    /// set and schema replaced by the cache keys. Both answers are returned as JSON values.
    fn both(op: &str, stateful_op: &str, test: &str, call: Value) -> (Value, Value) {
        let (policy_set_id, schema_name) = cache(test);
        let mut stateless = call.clone();
        stateless["policies"] = policies();
        stateless["schema"] = serde_json::to_value(FfiSchema::Cedar(SCHEMA.into())).unwrap();
        let mut stateful = call;
        stateful["preparsedPolicySetId"] = json!(policy_set_id);
        stateful["preparsedSchemaName"] = json!(schema_name);
        let parse = |s: String| serde_json::from_str::<Value>(&s).unwrap();
        (
            parse(call_cedar(op, &stateless.to_string())),
            parse(call_cedar(stateful_op, &stateful.to_string())),
        )
    }

    fn entity(ty: &str, id: &str, attrs: Value) -> Value {
        json!({ "uid": { "type": ty, "id": id }, "attrs": attrs, "parents": [] })
    }

    fn concrete_call(entities: Value, context: Value) -> Value {
        json!({
            "principal": { "type": "NS::User", "id": "u1" },
            "action": { "type": "NS::Action", "id": "View" },
            "resource": { "type": "NS::Field", "id": "f1" },
            "context": context,
            "validateRequest": true,
            "entities": entities,
        })
    }

    fn concrete(test: &str, call: Value) -> (Value, Value) {
        both(
            "AuthorizationOperation",
            "StatefulAuthorizationOperation",
            test,
            call,
        )
    }

    fn errors(answer: &Value) -> &Vec<Value> {
        assert_eq!(answer["type"], "failure", "expected a failure: {answer}");
        answer["errors"].as_array().unwrap()
    }

    #[test]
    fn concrete_success_matches_stateless() {
        let entities = json!([
            entity("NS::User", "u1", json!({ "tenantId": "t1" })),
            entity("NS::Field", "f1", json!({ "tenantId": "t1" })),
        ]);
        let (stateless, stateful) =
            concrete("concrete_success", concrete_call(entities, json!({})));
        assert_eq!(stateless["type"], "success");
        assert_eq!(stateless["response"]["decision"], "allow");
        assert_eq!(stateless, stateful);
    }

    /// Issue #379: the stateful path used to report only `entity does not conform to the schema`.
    #[test]
    fn concrete_entity_errors_match_stateless() {
        let entities = json!([
            entity("NS::User", "u1", json!({ "tenantId": "t1" })),
            entity("NS::Field", "f1", json!({})),
        ]);
        let (stateless, stateful) = concrete("concrete_entity", concrete_call(entities, json!({})));
        let message = errors(&stateful)[0]["message"].as_str().unwrap();
        assert!(
            message.contains("expected entity `NS::Field::\"f1\"` to have attribute `tenantId`"),
            "the cause was dropped: {message}"
        );
        assert_eq!(stateless, stateful);
    }

    #[test]
    fn concrete_context_errors_match_stateless() {
        let (stateless, stateful) = concrete(
            "concrete_context",
            concrete_call(json!([]), json!({ "authenticated": "yes" })),
        );
        errors(&stateful);
        assert_eq!(stateless, stateful);
    }

    #[test]
    fn concrete_request_and_entity_errors_are_all_reported() {
        let mut call = concrete_call(json!([entity("NS::Field", "f1", json!({}))]), json!({}));
        call["principal"] = json!({ "type": "NS::Field", "id": "f2" });
        let (stateless, stateful) = concrete("concrete_many", call);
        assert_eq!(errors(&stateful).len(), 2, "{stateful}");
        assert_eq!(stateless, stateful);
    }

    #[test]
    fn concrete_uid_errors_match_stateless() {
        let mut call = concrete_call(json!([]), json!({}));
        call["principal"] = json!({ "type": "NS::User" });
        call["resource"] = json!({ "id": "f1" });
        let (stateless, stateful) = concrete("concrete_uid", call);
        assert_eq!(errors(&stateful).len(), 2, "{stateful}");
        assert_eq!(stateless, stateful);
    }

    #[test]
    fn concrete_cache_miss() {
        let call = json!({
            "principal": { "type": "NS::User", "id": "u1" },
            "action": { "type": "NS::Action", "id": "View" },
            "resource": { "type": "NS::Field", "id": "f1" },
            "context": {},
            "preparsedPolicySetId": "stateful-test-never-cached",
            "entities": [],
        });
        let answer: Value = serde_json::from_str(&call_cedar(
            "StatefulAuthorizationOperation",
            &call.to_string(),
        ))
        .unwrap();
        assert_eq!(
            errors(&answer)[0]["message"],
            "preparsed policy set 'stateful-test-never-cached' not found"
        );
    }

    #[cfg(feature = "partial-eval")]
    mod partial {
        use super::*;

        fn partial(test: &str, call: Value) -> (Value, Value) {
            both(
                "AuthorizationPartialOperation",
                "StatefulAuthorizationPartialOperation",
                test,
                call,
            )
        }

        fn partial_call(entities: Value) -> Value {
            json!({
                "principal": { "type": "NS::User", "id": "u1" },
                "action": { "type": "NS::Action", "id": "View" },
                "context": {},
                "validateRequest": true,
                "entities": entities,
            })
        }

        #[test]
        fn unknown_resource_residuals_match_stateless() {
            let entities = json!([entity("NS::User", "u1", json!({ "tenantId": "t1" }))]);
            let (stateless, stateful) = partial("partial_residuals", partial_call(entities));
            assert_eq!(stateless["type"], "residuals", "{stateless}");
            assert!(stateless["response"]["decision"].is_null());
            assert_eq!(
                stateless["response"]["nontrivialResiduals"],
                json!(["p1"]),
                "{stateless}"
            );
            assert_eq!(stateless, stateful);
        }

        #[test]
        fn concrete_decision_matches_stateless() {
            let entities = json!([
                entity("NS::User", "u1", json!({ "tenantId": "t1" })),
                entity("NS::Field", "f1", json!({ "tenantId": "t1" })),
            ]);
            let mut call = partial_call(entities);
            call["resource"] = json!({ "type": "NS::Field", "id": "f1" });
            let (stateless, stateful) = partial("partial_decision", call);
            assert_eq!(stateless["response"]["decision"], "allow", "{stateless}");
            assert_eq!(stateless, stateful);
        }

        #[test]
        fn entity_errors_match_stateless() {
            let entities = json!([entity("NS::User", "u1", json!({}))]);
            let (stateless, stateful) = partial("partial_entity", partial_call(entities));
            let message = errors(&stateful)[0]["message"].as_str().unwrap();
            assert!(
                message.contains("to have attribute `tenantId`"),
                "the cause was dropped: {message}"
            );
            assert_eq!(stateless, stateful);
        }

        #[test]
        fn entity_and_request_errors_match_stateless() {
            let mut call = partial_call(json!([entity("NS::User", "u1", json!({}))]));
            call["principal"] = json!({ "type": "NS::Field", "id": "u1" });
            let (stateless, stateful) = partial("partial_many", call);
            assert_eq!(errors(&stateful).len(), 2, "{stateful}");
            assert_eq!(stateless, stateful);
        }

        #[test]
        fn context_errors_match_stateless() {
            let mut call = partial_call(json!([]));
            call["context"] = json!({ "authenticated": 1 });
            let (stateless, stateful) = partial("partial_context", call);
            errors(&stateful);
            assert_eq!(stateless, stateful);
        }

        #[test]
        fn without_schema() {
            let (policy_set_id, _) = cache("partial_no_schema");
            let call = json!({
                "principal": { "type": "NS::User", "id": "u1" },
                "action": { "type": "NS::Action", "id": "View" },
                "context": {},
                "preparsedPolicySetId": policy_set_id,
                "entities": [entity("NS::User", "u1", json!({ "tenantId": "t1" }))],
            });
            let answer: Value = serde_json::from_str(&call_cedar(
                "StatefulAuthorizationPartialOperation",
                &call.to_string(),
            ))
            .unwrap();
            assert_eq!(answer["type"], "residuals", "{answer}");
        }

        #[test]
        fn cache_miss() {
            let (policy_set_id, _) = cache("partial_miss");
            let call = json!({
                "context": {},
                "preparsedPolicySetId": policy_set_id,
                "preparsedSchemaName": "stateful-test-never-cached",
                "entities": [],
            });
            let answer: Value = serde_json::from_str(&call_cedar(
                "StatefulAuthorizationPartialOperation",
                &call.to_string(),
            ))
            .unwrap();
            assert_eq!(
                errors(&answer)[0]["message"],
                "preparsed schema 'stateful-test-never-cached' not found"
            );
        }
    }
}
