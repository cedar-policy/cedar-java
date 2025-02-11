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

import java.util.HashMap;
import java.util.HashSet;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.AuthorizationResponse.SuccessOrFailure;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Unknown;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.PrimBool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

public class AuthTests {

    private void assertAllowed(AuthorizationRequest q, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            final var response = new BasicAuthorizationEngine().isAuthorized(q, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertTrue(success.isAllowed());
        });
    }

    @Test
    public void simple() {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = new AuthorizationRequest(alice, view, alice, new HashMap<>());
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal,action,resource);", "p0"));
        var policySet = new PolicySet(policies);
        assertAllowed(q, policySet, new HashSet<>());
    }

    private Set<Entity> buildEntitiesForContextTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName albumResourceType = EntityTypeName.parse("Album").get();
        EntityTypeName photoResourceType = EntityTypeName.parse("Photo").get();

        Set<EntityUID> parents = new HashSet<>();
        Entity album = new Entity(albumResourceType.of("Vacation"), new HashMap<>(), new HashSet<>());
        parents.add(album.getEUID());
        Entity photo = new Entity(photoResourceType.of("pic01"), new HashMap<>(), parents);

        Set<Entity> entities = new HashSet<>();
        entities.add(photo);
        entities.add(album);
        entities.add(new Entity(principalType.of("Alice"), new HashMap<>(), new HashSet<>()));
        entities.add(new Entity(actionType.of("View_Photo"), new HashMap<>(), new HashSet<>()));

        return entities;
    }

    private PolicySet buildPolicySetForContextTests() {
        Set<Policy> ps = new HashSet<>();
        String fullPolicy =
                "permit(principal == User::\"Alice\", action == Action::\"View_Photo\", resource in Album::\"Vacation\")"
                + "when {context.authenticated == true};";

        Policy newPolicy = new Policy(fullPolicy, "p1");
        ps.add(newPolicy);
        return new PolicySet(ps);
    }

    @Test
    public void authWithBackwardCompatibleContext() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "Alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "View_Photo");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Photo").get(), "pic01");
        Set<Entity> entities = buildEntitiesForContextTests();
        PolicySet policySet = buildPolicySetForContextTests();
        Map<String, Value> context = new HashMap<>();
        context.put("authenticated", new PrimBool(true));
        AuthorizationRequest r = new AuthorizationRequest(principal, action, resource, context);

        assertAllowed(r, policySet, entities);
    }

    @Test
    public void authWithContextObject() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "Alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "View_Photo");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Photo").get(), "pic01");
        Set<Entity> entities = buildEntitiesForContextTests();
        PolicySet policySet = buildPolicySetForContextTests();
        Map<String, Value> contextMap = new HashMap<>();
        contextMap.put("authenticated", new PrimBool(true));
        Context context = new Context(contextMap);
        AuthorizationRequest r = new AuthorizationRequest(principal, action, resource, context);

        assertAllowed(r, policySet, entities);
    }

    @Test
    public void concrete() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = PartialAuthorizationRequest.builder().principal(alice).action(view).resource(alice).context(new HashMap<>()).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource);", "p0"));
        var policySet = new PolicySet(policies);
        assumePartialEvaluation(() -> {
            try {
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new HashSet<>());
                assertEquals(Decision.Allow, response.success.orElseThrow().getDecision());
                assertEquals(response.success.orElseThrow().getMustBeDetermining().iterator().next(), "p0");
                assertTrue(response.success.orElseThrow().getNontrivialResiduals().isEmpty());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    @Test
    public void partialAuthConcreteWithBackwardCompatibleContext() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");

        Map<String, Value> context = new HashMap<>();
        context.put("authenticated", new PrimBool(true));

        var q = PartialAuthorizationRequest.builder().principal(alice).action(view).resource(alice).context(context).build();

        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource) when {context.authenticated == true};", "p0"));
        var policySet = new PolicySet(policies);

        assumePartialEvaluation(() -> {
            try {
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new HashSet<>());
                assertEquals(Decision.Allow, response.success.orElseThrow().getDecision());
                assertEquals(response.success.orElseThrow().getMustBeDetermining().iterator().next(), "p0");
                assertTrue(response.success.orElseThrow().getNontrivialResiduals().isEmpty());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    @Test
    public void partialAuthConcreteWithContextObject() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        Map<String, Value> contextMap = new HashMap<>();
        contextMap.put("authenticated", new PrimBool(true));
        Context context = new Context(contextMap);
        var q = PartialAuthorizationRequest.builder().principal(alice).action(view).resource(alice).context(context).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource) when {context.authenticated == true};", "p0"));
        var policySet = new PolicySet(policies);
        assumePartialEvaluation(() -> {
            try {
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new HashSet<>());
                assertEquals(Decision.Allow, response.success.orElseThrow().getDecision());
                assertEquals(response.success.orElseThrow().getMustBeDetermining().iterator().next(), "p0");
                assertTrue(response.success.orElseThrow().getNontrivialResiduals().isEmpty());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    @Test
    public void residual() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = PartialAuthorizationRequest.builder().action(view).resource(alice).context(new HashMap<>()).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource);", "p0"));
        var policySet = new PolicySet(policies);
        assumePartialEvaluation(() -> {
            try {
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new HashSet<>());
                assertTrue(response.success.orElseThrow().getDecision() == null);
                assertEquals("p0", response.success.orElseThrow().getResiduals().entrySet().iterator().next().getKey());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    @Test
    public void residualWithUnknownValue() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        Map<String, Value> context = Map.of("authenticated", new Unknown("AuthenticatedIsUnknown"));
        var q = PartialAuthorizationRequest.builder().principal(alice).action(view).resource(alice).context(context).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource) when{context.authenticated};", "p0"));
        var policySet = new PolicySet(policies);
        assumePartialEvaluation(() -> {
            try {
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new HashSet<>());
                assertNull(response.success.orElseThrow().getDecision());
                assertEquals("p0", response.success.orElseThrow().getResiduals().entrySet().iterator().next().getKey());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    private void assumePartialEvaluation(Executable executable) {
        try {
            executable.execute();
        } catch (MissingExperimentalFeatureException e) {
            System.err.println("Skipping assertions: " + e.getMessage());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
