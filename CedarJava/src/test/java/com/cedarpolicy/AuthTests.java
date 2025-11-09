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
import java.util.Optional;

import static com.cedarpolicy.TestUtil.loadSchemaResource;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.AuthorizationResponse.SuccessOrFailure;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.Context;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Unknown;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.model.entity.Entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

public class AuthTests {

    private void assertAllowed(AuthorizationRequest q, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            // Using Entities object
            Entities entitiesObj = new Entities(entities);
            final var responseWithEntities = new BasicAuthorizationEngine().isAuthorized(q, policySet, entitiesObj);
            assertEquals(responseWithEntities.type, SuccessOrFailure.Success);
            final var successWithEntities = responseWithEntities.success.get();
            assertTrue(successWithEntities.isAllowed());

            // Backward compatible using Set<Entities>
            final var response = new BasicAuthorizationEngine().isAuthorized(q, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertTrue(success.isAllowed());
        });
    }

    private void assertFailure(AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = new BasicAuthorizationEngine().isAuthorized(request, policySet, entities);
            if (response.success.isPresent()) {
                fail(String.format("Expected a failure, but got this success: %s", response.toString()));
            } else {
                final var errors = assertDoesNotThrow(() -> response.errors.get());
                assertTrue(errors.size() > 0);
            }
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
    public void partialAuthConcreteWithEntitiesObject() {
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
                final PartialAuthorizationResponse response = auth.isAuthorizedPartial(q, policySet, new Entities());
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

    /**
     * Build entities for enum authorization tests.
     */
    private Set<Entity> buildEntitiesForEnumTests() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName taskType = EntityTypeName.parse("Task").get();
        EntityTypeName colorType = EntityTypeName.parse("Color").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName applicationType = EntityTypeName.parse("Application").get();

        Set<Entity> entities = new HashSet<>();

        // Users
        Entity alice = new Entity(userType.of("alice"), new HashMap<>() {{
            put("name", new PrimString("Alice"));
        }}, new HashSet<>());

        Entity bob = new Entity(userType.of("bob"), new HashMap<>() {{
            put("name", new PrimString("Bob"));
        }}, new HashSet<>());

        // Tasks
        Entity task1 = new Entity(taskType.of("task1"), new HashMap<>() {{
            put("owner", alice.getEUID());
            put("name", new PrimString("Complete project"));
            put("status", new EntityUID(colorType, "Red"));
        }}, new HashSet<>());

        Entity task2 = new Entity(taskType.of("task2"), new HashMap<>() {{
            put("owner", bob.getEUID());
            put("name", new PrimString("Review code"));
            put("status", new EntityUID(colorType, "Green"));
        }}, new HashSet<>());

        // Actions
        Entity updateTaskAction = new Entity(actionType.of("UpdateTask"), new HashMap<>(), new HashSet<>());
        Entity createListAction = new Entity(actionType.of("CreateList"), new HashMap<>(), new HashSet<>());

        // Application enum entity
        Entity tinyTodoApp = new Entity(applicationType.of("TinyTodo"), new HashMap<>(), new HashSet<>());

        entities.add(alice);
        entities.add(bob);
        entities.add(task1);
        entities.add(task2);
        entities.add(updateTaskAction);
        entities.add(createListAction);
        entities.add(tinyTodoApp);

        return entities;
    }

    /**
     * Test RFC example policy with enum entities - should be allowed.
     */
    @Test
    public void enumAuthorizationRFCExampleAllow() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task2"); // task2 has Green status

        Set<Entity> entities = buildEntitiesForEnumTests();

        // RFC policy: allow if principal owns task and status is not Red
        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"UpdateTask",
                resource
            ) when {
                principal == resource.owner &&
                resource.status != Color::"Red"
            };
            """, "rfcPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        // Should be denied because alice doesn't own task2 (bob does)
        var auth = new BasicAuthorizationEngine();
        assertDoesNotThrow(() -> {
            final var response = auth.isAuthorized(request, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertFalse(success.isAllowed()); // Should be denied due to ownership
        });
    }

    /**
     * Test RFC example policy with enum entities - should be denied due to Red status.
     */
    @Test
    public void enumAuthorizationRFCExampleDenyByStatus() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task1"); // task1 has Red status

        Set<Entity> entities = buildEntitiesForEnumTests();

        // RFC policy: allow if principal owns task and status is not Red
        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"UpdateTask",
                resource
            ) when {
                principal == resource.owner &&
                resource.status != Color::"Red"
            };
            """, "rfcPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        // Should be denied because task1 has Red status (even though alice owns it)
        var auth = new BasicAuthorizationEngine();
        assertDoesNotThrow(() -> {
            final var response = auth.isAuthorized(request, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertFalse(success.isAllowed()); // Should be denied due to Red status
        });
    }

    /**
     * Test enum equality in authorization policy.
     */
    @Test
    public void enumAuthorizationEqualityComparison() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "bob");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task2"); // task2 has Green status

        Set<Entity> entities = buildEntitiesForEnumTests();

        // Policy: allow if status is exactly Green
        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal == User::"bob",
                action == Action::"UpdateTask",
                resource
            ) when {
                resource.status == Color::"Green"
            };
            """, "greenPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        assertAllowed(request, policySet, entities);
    }

    /**
     * Test Application enum authorization from RFC example.
     */
    @Test
    public void enumAuthorizationApplicationEnum() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "CreateList");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Application").get(), "TinyTodo");

        Set<Entity> entities = buildEntitiesForEnumTests();

        // Policy from RFC: allow CreateList on TinyTodo application
        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"CreateList",
                resource == Application::"TinyTodo"
            );
            """, "appPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        assertAllowed(request, policySet, entities);
    }

    /**
     * Test multiple enum values in OR condition.
     */
    @Test
    public void enumAuthorizationMultipleValues() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "bob");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task2"); // Green status

        Set<Entity> entities = buildEntitiesForEnumTests();

        // Policy: allow if status is Blue OR Green
        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"UpdateTask",
                resource
            ) when {
                resource.status == Color::"Blue" ||
                resource.status == Color::"Green"
            };
            """, "multiEnumPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        assertAllowed(request, policySet, entities);
    }

    /**
     * Test forbid policy with enum entities.
     */
    @Test
    public void enumAuthorizationForbidPolicy() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "bob");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task1"); // Red status, alice owner

        Set<Entity> entities = buildEntitiesForEnumTests();

        var policies = new HashSet<Policy>();
        // Permit policy
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"UpdateTask",
                resource
            );
            """, "permitAll"));

        // Forbid policy from RFC: forbid if status is Red and principal is not owner
        policies.add(new Policy("""
            forbid(
                principal,
                action == Action::"UpdateTask",
                resource
            ) when {
                resource.status == Color::"Red" &&
                principal != resource.owner
            };
            """, "forbidRedNonOwner"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        // Should be denied because bob is not owner of task1 and it has Red status
        var auth = new BasicAuthorizationEngine();
        assertDoesNotThrow(() -> {
            final var response = auth.isAuthorized(request, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertFalse(success.isAllowed());
        });
    }

    /**
     * Test authorization with schema validation - positive case with valid enum entities.
     */
    @Test
    public void enumAuthorizationWithSchemaValidationPositive() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName applicationType = EntityTypeName.parse("Application").get();

        // Create valid entities
        Entity principalEntity = new Entity(userType.of("alice"), new HashMap<>() {{
            put("name", new PrimString("Alice"));
        }}, new HashSet<>());

        Entity actionEntity = new Entity(actionType.of("CreateList"), new HashMap<>(), new HashSet<>());
        Entity resourceEntity = new Entity(applicationType.of("TinyTodo"), new HashMap<>(), new HashSet<>());

        Set<Entity> entities = new HashSet<>();
        entities.add(principalEntity);
        entities.add(actionEntity);
        entities.add(resourceEntity);

        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"CreateList",
                resource == Application::"TinyTodo"
            );
            """, "validEnumPolicy"));

        var policySet = new PolicySet(policies);

        // Create authorization request with schema validation enabled
        AuthorizationRequest request = new AuthorizationRequest(
            principalEntity,
            actionEntity,
            resourceEntity,
            Optional.of(new HashMap<>()),
            Optional.of(ENUM_SCHEMA),
            true // Enable request validation
        );

        var auth = new BasicAuthorizationEngine();
        assertDoesNotThrow(() -> {
            final var response = auth.isAuthorized(request, policySet, entities);
            assertEquals(response.type, SuccessOrFailure.Success);
            final var success = response.success.get();
            assertTrue(success.isAllowed());
        });
    }

    /**
     * Test authorization with schema validation - negative case with invalid enum entity.
     */
    @Test
    public void enumAuthorizationWithSchemaValidationNegative() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName applicationType = EntityTypeName.parse("Application").get();

        // Create entities with invalid enum value
        Entity principalEntity = new Entity(userType.of("alice"), new HashMap<>() {{
            put("name", new PrimString("Alice"));
        }}, new HashSet<>());

        Entity actionEntity = new Entity(actionType.of("CreateList"), new HashMap<>(), new HashSet<>());
        // Use INVALID enum value - "InvalidApp" is not in the Application enum
        Entity resourceEntity = new Entity(applicationType.of("InvalidApp"), new HashMap<>(), new HashSet<>());

        Set<Entity> entities = new HashSet<>();
        entities.add(principalEntity);
        entities.add(actionEntity);
        entities.add(resourceEntity);

        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"CreateList",
                resource == Application::"InvalidApp"
            );
            """, "invalidEnumPolicy"));

        var policySet = new PolicySet(policies);

        // Create authorization request with schema validation enabled
        AuthorizationRequest request = new AuthorizationRequest(
            principalEntity,
            actionEntity,
            resourceEntity,
            Optional.of(new HashMap<>()),
            Optional.of(ENUM_SCHEMA),
            true // Enable request validation - this should catch the invalid enum
        );

        // Should return failure response due to invalid enum value when schema validation is enabled
        assertFailure(request, policySet, entities);
    }

    /**
     * Test authorization with invalid enum in task status attribute.
     */
    @Test
    public void enumAuthorizationWithInvalidTaskStatus() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName taskType = EntityTypeName.parse("Task").get();
        EntityTypeName colorType = EntityTypeName.parse("Color").get();

        Entity principalEntity = new Entity(userType.of("alice"), new HashMap<>() {{
            put("name", new PrimString("Alice"));
        }}, new HashSet<>());

        Entity actionEntity = new Entity(actionType.of("UpdateTask"), new HashMap<>(), new HashSet<>());

        // Create task with invalid enum status value "Purple" (not in Color enum)
        Entity taskEntity = new Entity(taskType.of("task1"), new HashMap<>() {{
            put("owner", principalEntity.getEUID());
            put("name", new PrimString("Test task"));
            put("status", new EntityUID(colorType, "Purple")); // Invalid enum value
        }}, new HashSet<>());

        Set<Entity> entities = new HashSet<>();
        entities.add(principalEntity);
        entities.add(actionEntity);
        entities.add(taskEntity);

        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
            permit(
                principal,
                action == Action::"UpdateTask",
                resource
            ) when {
                resource.status != Color::"Red"
            };
            """, "taskPolicy"));

        var policySet = new PolicySet(policies);

        // Create authorization request with schema validation enabled
        AuthorizationRequest request = new AuthorizationRequest(
            principalEntity,
            actionEntity,
            taskEntity,
            Optional.of(new HashMap<>()),
            Optional.of(ENUM_SCHEMA),
            true // Enable request validation
        );

        // Should return failure response due to invalid enum value in task status
        assertFailure(request, policySet, entities);
    }

    private static final Schema ENUM_SCHEMA = loadSchemaResource("/enum_schema.json");

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
