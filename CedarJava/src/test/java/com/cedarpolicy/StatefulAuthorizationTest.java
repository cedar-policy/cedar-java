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

package com.cedarpolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.DetailedError;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.PartialAuthorizationSuccessResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;

import org.junit.jupiter.api.Test;

/**
 * The stateful (cached) authorization paths must answer exactly as the stateless ones do: same decision, same residuals
 * and same errors, down to the cause chain, help text and source locations. Every test runs the same request against a
 * cached policy set and schema, and against uncached copies of them.
 */
public class StatefulAuthorizationTest {

    private static final String SCHEMA = """
            namespace NS {
                entity User = { "tenantId": String };
                entity Field = { "tenantId": String };
                action View appliesTo {
                    principal: [User],
                    resource: [Field],
                    context: { "authenticated"?: Bool }
                };
            }
            """;

    private static final EntityUID PRINCIPAL = EntityUID.parse("NS::User::\"u1\"").orElseThrow();
    private static final EntityUID ACTION = EntityUID.parse("NS::Action::\"View\"").orElseThrow();
    private static final EntityUID RESOURCE = EntityUID.parse("NS::Field::\"f1\"").orElseThrow();

    private final BasicAuthorizationEngine engine = new BasicAuthorizationEngine();

    private static PolicySet policies() {
        return new PolicySet(Set.of(
                new Policy("permit(principal, action, resource) when { principal.tenantId == resource.tenantId };",
                        "p1"),
                new Policy("forbid(principal, action, resource) when "
                        + "{ context has authenticated && !context.authenticated };", "p2")));
    }

    private static Schema schema() throws Exception {
        return Schema.parse(JsonOrCedar.Cedar, SCHEMA);
    }

    /** A policy set and schema that are both cached, so the engine takes the stateful path. */
    private record Cached(PolicySet policies, Schema schema) {
        static Cached create() throws Exception {
            PolicySet policies = StatefulAuthorizationTest.policies();
            Schema schema = StatefulAuthorizationTest.schema();
            policies.cache();
            schema.cache();
            return new Cached(policies, schema);
        }
    }

    private static Entity user(String tenant) {
        return new Entity(PRINCIPAL, tenant == null ? Map.of() : Map.of("tenantId", new PrimString(tenant)), Set.of());
    }

    private static Entity field(String tenant) {
        return new Entity(RESOURCE, tenant == null ? Map.of() : Map.of("tenantId", new PrimString(tenant)), Set.of());
    }

    // --- Concrete authorization (issue #379) ---

    private AuthorizationResponse[] concrete(Map<String, Value> context, Set<Entity> entities) throws Exception {
        Cached cached = Cached.create();
        AuthorizationResponse stateful = engine.isAuthorized(
                new AuthorizationRequest(PRINCIPAL, ACTION, RESOURCE, Optional.of(context),
                        Optional.of(cached.schema()), true),
                cached.policies(), entities);
        AuthorizationResponse stateless = engine.isAuthorized(
                new AuthorizationRequest(PRINCIPAL, ACTION, RESOURCE, Optional.of(context), Optional.of(schema()),
                        true),
                policies(), entities);
        return new AuthorizationResponse[] {stateless, stateful};
    }

    @Test
    public void concreteEntityErrorKeepsDetail() throws Exception {
        AuthorizationResponse[] responses = concrete(Map.of(), Set.of(user("t1"), field(null)));
        AuthorizationResponse stateless = responses[0];
        AuthorizationResponse stateful = responses[1];

        assertEquals(AuthorizationResponse.SuccessOrFailure.Failure, stateful.type);
        String message = stateful.errors.get().get(0).message;
        assertTrue(message.contains("expected entity `NS::Field::\"f1\"` to have attribute `tenantId`"),
                "the stateful path dropped the cause: " + message);
        assertSameErrors(stateless.errors.get(), stateful.errors.get());
    }

    @Test
    public void concreteContextErrorMatchesStateless() throws Exception {
        AuthorizationResponse[] responses = concrete(Map.of("authenticated", new PrimString("yes")),
                Set.of(user("t1"), field("t1")));
        assertEquals(AuthorizationResponse.SuccessOrFailure.Failure, responses[1].type);
        assertSameErrors(responses[0].errors.get(), responses[1].errors.get());
    }

    @Test
    public void concreteDecisionMatchesStateless() throws Exception {
        AuthorizationResponse[] responses = concrete(Map.of(), Set.of(user("t1"), field("t1")));
        assertTrue(responses[0].success.get().isAllowed());
        assertTrue(responses[1].success.get().isAllowed());
    }

    @Test
    public void concreteFallsBackWhenTheCacheEntryIsGone() throws Exception {
        Cached cached = Cached.create();
        evict(cached.policies());
        AuthorizationResponse response = engine.isAuthorized(
                new AuthorizationRequest(PRINCIPAL, ACTION, RESOURCE, Optional.of(Map.of()),
                        Optional.of(cached.schema()), true),
                cached.policies(), Set.of(user("t1"), field("t1")));
        assertEquals(AuthorizationResponse.SuccessOrFailure.Success, response.type, response.toString());
        assertTrue(response.success.get().isAllowed());
    }

    // --- Partial authorization (issue #380) ---

    private static PartialAuthorizationRequest.Builder partialRequest() {
        return PartialAuthorizationRequest.builder().principal(PRINCIPAL).action(ACTION).emptyContext()
                .enableRequestValidation();
    }

    private PartialAuthorizationResponse[] partial(PartialAuthorizationRequest.Builder request, Set<Entity> entities)
            throws Exception {
        Cached cached = Cached.create();
        PartialAuthorizationResponse stateful = engine.isAuthorizedPartial(
                request.schema(cached.schema()).build(), cached.policies(), entities);
        PartialAuthorizationResponse stateless = engine.isAuthorizedPartial(
                request.schema(schema()).build(), policies(), entities);
        return new PartialAuthorizationResponse[] {stateless, stateful};
    }

    @Test
    public void partialResidualsMatchStateless() {
        TestUtil.assumeExperimentalFeature(() -> {
            PartialAuthorizationResponse[] responses = partial(partialRequest(), Set.of(user("t1")));
            PartialAuthorizationSuccessResponse stateful = responses[1].success.orElseThrow();
            assertEquals(null, stateful.getDecision());
            assertEquals(Set.of("p1"), stateful.getNontrivialResiduals());
            assertSameResponse(responses[0], responses[1]);
        });
    }

    @Test
    public void partialConcreteDecisionMatchesStateless() {
        TestUtil.assumeExperimentalFeature(() -> {
            PartialAuthorizationResponse[] responses = partial(
                    partialRequest().resource(RESOURCE).context(Map.of("authenticated", new PrimBool(false))),
                    Set.of(user("t1"), field("t1")));
            assertEquals(Set.of("p2"), responses[1].success.orElseThrow().getMustBeDetermining());
            assertSameResponse(responses[0], responses[1]);
        });
    }

    @Test
    public void partialEntityErrorKeepsDetail() {
        TestUtil.assumeExperimentalFeature(() -> {
            PartialAuthorizationResponse[] responses = partial(partialRequest(), Set.of(user(null)));
            String message = responses[1].errors.orElseThrow().get(0).message;
            assertTrue(message.contains("to have attribute `tenantId`"),
                    "the stateful path dropped the cause: " + message);
            assertSameResponse(responses[0], responses[1]);
        });
    }

    @Test
    public void partialContextErrorMatchesStateless() {
        TestUtil.assumeExperimentalFeature(() -> {
            PartialAuthorizationResponse[] responses = partial(
                    partialRequest().context(Map.of("authenticated", new PrimString("yes"))), Set.of(user("t1")));
            assertEquals(PartialAuthorizationResponse.SuccessOrFailure.Failure, responses[1].type);
            assertSameResponse(responses[0], responses[1]);
        });
    }

    @Test
    public void partialWithoutSchemaUsesCachedPolicies() {
        TestUtil.assumeExperimentalFeature(() -> {
            PolicySet cached = policies();
            cached.cache();
            PartialAuthorizationRequest request = PartialAuthorizationRequest.builder().principal(PRINCIPAL)
                    .action(ACTION).emptyContext().build();
            assertSameResponse(engine.isAuthorizedPartial(request, policies(), Set.of(user("t1"))),
                    engine.isAuthorizedPartial(request, cached, Set.of(user("t1"))));
        });
    }

    @Test
    public void partialWithUncachedSchemaFallsBack() {
        TestUtil.assumeExperimentalFeature(() -> {
            PolicySet cached = policies();
            cached.cache();
            // Schema is not cached, so the engine must not skip schema-based parsing: the error is still reported.
            PartialAuthorizationResponse response = engine.isAuthorizedPartial(
                    partialRequest().schema(schema()).build(), cached, Set.of(user(null)));
            assertEquals(PartialAuthorizationResponse.SuccessOrFailure.Failure, response.type);
        });
    }

    @Test
    public void partialFallsBackWhenTheCacheEntryIsGone() {
        TestUtil.assumeExperimentalFeature(() -> {
            Cached cached = Cached.create();
            evict(cached.policies());
            PartialAuthorizationResponse response = engine.isAuthorizedPartial(
                    partialRequest().schema(cached.schema()).build(), cached.policies(), Set.of(user("t1")));
            assertEquals(PartialAuthorizationResponse.SuccessOrFailure.Success, response.type, response.toString());
        });
    }

    // --- Helpers ---

    /** Drop the native cache entry behind the policy set's back, as if it had been evicted. */
    private static void evict(PolicySet policySet) throws Exception {
        Method remove = PolicySet.class.getDeclaredMethod("removeCachedPolicySetJni", String.class);
        remove.setAccessible(true);
        remove.invoke(null, policySet.cacheKey().orElseThrow());
    }

    /** {@link DetailedError#toString()} covers every field, including nested `related` errors. */
    private static void assertSameErrors(List<DetailedError> expected, List<DetailedError> actual) {
        assertEquals(expected.toString(), actual.toString());
    }

    private static void assertSameResponse(PartialAuthorizationResponse expected,
            PartialAuthorizationResponse actual) {
        assertEquals(expected.type, actual.type, actual.toString());
        if (expected.type == PartialAuthorizationResponse.SuccessOrFailure.Failure) {
            assertSameErrors(expected.errors.orElseThrow(), actual.errors.orElseThrow());
            return;
        }
        PartialAuthorizationSuccessResponse e = expected.success.orElseThrow();
        PartialAuthorizationSuccessResponse a = actual.success.orElseThrow();
        assertEquals(e.getDecision(), a.getDecision());
        assertEquals(e.getResiduals(), a.getResiduals());
        assertEquals(e.getNontrivialResiduals(), a.getNontrivialResiduals());
        assertEquals(e.getSatisfied(), a.getSatisfied());
        assertEquals(e.getErrored(), a.getErrored());
        assertEquals(e.getMayBeDetermining(), a.getMayBeDetermining());
        assertEquals(e.getMustBeDetermining(), a.getMustBeDetermining());
    }
}
