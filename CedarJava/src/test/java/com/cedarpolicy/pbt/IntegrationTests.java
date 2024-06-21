/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.cedarpolicy.pbt;

import static com.cedarpolicy.TestUtil.loadSchemaResource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.model.slice.EntityTypeAndId;
import com.cedarpolicy.model.slice.Instantiation;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.model.slice.Slice;
import com.cedarpolicy.model.slice.TemplateInstantiation;
import com.cedarpolicy.value.Decimal;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.IpAddress;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** Integration tests. */
public class IntegrationTests {

    final EntityTypeName principalType;
    final EntityTypeName actionType;
    final EntityTypeName resourceType;

    private static final BasicAuthorizationEngine ENGINE = new BasicAuthorizationEngine();

    public IntegrationTests() {
        principalType = EntityTypeName.parse("User").get();
        actionType = EntityTypeName.parse("Action").get();
        resourceType = EntityTypeName.parse("Resource").get();
    }

    private void assertAllowed(AuthorizationRequest request, Slice slice) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, slice);
            if (response.success.isPresent()) {
                final var success = assertDoesNotThrow(() -> response.success.get());
                assertTrue(success.isAllowed());
            } else {
                fail(String.format("Expected a success response but got %s", response.toString()));
            }
        });
    }

    private void assertNotAllowed(AuthorizationRequest request, Slice slice) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, slice);
            if (response.success.isPresent()) {
                final var success = assertDoesNotThrow(() -> response.success.get());
                assertFalse(success.isAllowed());
            } else {
                fail(String.format("Expected a success response but got %s", response.toString()));
            }
        });
    }

    private void assertFailure(AuthorizationRequest request, Slice slice) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, slice);
            if (response.success.isPresent()) {
                fail(String.format("Expected a failure, but got this success: %s", response.toString()));
            } else {
                final var errors = assertDoesNotThrow(() -> response.errors.get());
                assertTrue(errors.size() > 0);
            }
        });
    }

    /** Tests a single attribute: resource.owner. */
    @Test
    public void testResourceAttribute() {
        Set<Entity> entities = new HashSet<>();
        Map<String, Value> principalAttributes = new HashMap<>();
        var principalParents = new HashSet<EntityUID>();
        Entity principal = new Entity(principalType.of("alice"), principalAttributes, principalParents);
        entities.add(principal);

        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(actionType.of("view"), actionAttributes, actionParents);
        entities.add(action);

        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", principal.getEUID());
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(resourceType.of("photo.jpg"), resourceAttributes, resourceParents);
        entities.add(resource);

        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID()
                        + ",\n"
                        + "action=="
                        + action.getEUID()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "resource.owner=="
                        + principal.getEUID()
                        + "};";
        Policy policy = new Policy(p, "001");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal.getEUID(), action.getEUID(), resource.getEUID(), currentContext);
        assertAllowed(request, slice);
    }

    /** Tests a randomly generated attribute for resource. */
    @Property(tries = 50)
    public void testRandomResourceAttribute(@ForAll @IntRange(min = 1, max = 50) int count) {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        while (resourceAttributes.size() < count) {
            resourceAttributes.put(Utils.strings(), Utils.primStrings());
        }
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);
        /*
         *      select random attributes
         */
        List<String> attrKeys = new ArrayList<String>(resourceAttributes.keySet());
        int n = Utils.intInRange(0, count - 1);
        StringBuffer attributes = new StringBuffer();
        for (int i = 0; i <= n; i++) {
            attributes.append("resource.");
            String key = attrKeys.get(i);
            attributes.append(key);
            attributes.append("==");
            attributes.append("\"" + resourceAttributes.get(key).toString() + "\"");
            attributes.append(" && ");
        }
        if (attributes.toString().endsWith("&& ")) {
            attributes.delete(attributes.length() - 3, attributes.length()); // remove last &&
        }
        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID()
                        + ",\n"
                        + "action=="
                        + action.getEUID()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + attributes.toString()
                        + "\n};";
        attributes = null;
        Policy policy = new Policy(p, "ID" + count);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, slice);
    }

    /** Tests a randomly generated with one bad attribute for resource Response: Deny. */
    @Property(tries = 50)
    public void testRandomResourceAttributeDeny(@ForAll @IntRange(min = 1, max = 50) int count) {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        while (resourceAttributes.size() < count) {
            resourceAttributes.put(Utils.strings(), Utils.primStrings());
        }
        resourceAttributes.put("name", new PrimString("my_photo"));
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);
        /*
         *      select random attributes
         */
        List<String> attrKeys = new ArrayList<String>(resourceAttributes.keySet());
        int n = Utils.intInRange(0, count - 1);
        StringBuffer attributes = new StringBuffer();
        int r = Utils.intInRange(0, n);
        for (int i = 0; i <= n; i++) {
            attributes.append("resource.");
            String key = attrKeys.get(i);
            attributes.append(key);
            attributes.append("==");
            /*
             *   introduce one bad attribute
             */
            if (i == r) {
                attributes.append("\"noise\"");
            } else {
                attributes.append("\"" + resourceAttributes.get(key).toString() + "\"");
            }
            attributes.append(" && ");
        }
        attributes.delete(attributes.length() - 3, attributes.length()); // remove last &&
        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID().toString()
                        + ",\n"
                        + "action=="
                        + action.getEUID().toString()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID().toString()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + attributes.toString()
                        + "};";
        attributes = null;
        Policy policy = new Policy(p, "ID" + count);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertNotAllowed(request, slice);
    }

    /** Tests a long expression that crashes the JNI if Rust doesn't spawn a new thread. */
    @Test
    public void testLongExprRequiresRustThread() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("name", new PrimString("my_photo"));
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p = "permit( principal==User::\"alice\", action==Action::\"view\", resource==Resource::\"photo.jpg\" ) when { resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" && resource.name==\"my_photo\" };";
        Policy policy = new Policy(p, "ID1");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, slice);
    }

     /** Tests a long expression that is denied for nearing the stack overflow limit. */
    @Test
    public void testLongExprStackOverflowDeny() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("name", new PrimString("my_photo"));
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);
        String p = createNested(5000);
        Policy policy = new Policy(p, "ID1");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertNotAllowed(request, slice);
    }

    private String createNested(int repeats) {
        StringBuilder builder = new StringBuilder("permit(principal == User::\"alice\", action == Action::\"view\", resource == Resource::\"photo.jpg\") when { ");
        String phrase = " resource.name == \"my_photo\" && ";
        for (int i = 0; i < repeats; i++) {
           builder.append(phrase);
        }
        builder.append(" resource.name==\"my_photo\" };");
        return builder.toString();
    }

    /** Tests a single attribute: resource.owner. */
    @Test
    public void testUnspecifiedResource() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", principal.getEUID());
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p = "permit(principal, action, resource);";
        Policy policy = new Policy(p, "001");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        currentContext);
        assertAllowed(request, slice);
    }

    /** Test IpAddress extension. */
    @Test
    public void testIpAddressExtension() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("ip", new IpAddress("192.168.0.24"));
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID().toString()
                        + ",\n"
                        + "action=="
                        + action.getEUID().toString()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID().toString()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "principal.ip == ip(\"192.168.0.24\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, slice);
    }

    /** Test Decimal extension. */
    @Test
    public void testDecimalExtension() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("val", new Decimal("1.0000"));
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID().toString()
                        + ",\n"
                        + "action=="
                        + action.getEUID().toString()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID().toString()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "principal.val == decimal(\"1.0000\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, slice);
    }

    /** Use template slots to tests a single attribute: resource.owner. */
    @Test
    public void testTemplateResourceAttribute() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", principal.getEUID());
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        final String principalSlot = "?principal";

        String p =
                "permit(\n"
                        + "principal=="
                        + principalSlot
                        + ",\n"
                        + "action=="
                        + action.getEUID().toString()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID().toString()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "resource.owner==principal"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        Set<Policy> templates = new HashSet<>();
        templates.add(policy);

        Instantiation instantiation =
                new Instantiation(principalSlot, new EntityTypeAndId("User", "alice"));

        final String instantiatedPolicyId = "ID0_alice";
        TemplateInstantiation templateInstantiation =
                new TemplateInstantiation(
                        policyId,
                        instantiatedPolicyId,
                        new ArrayList<Instantiation>(Arrays.asList(instantiation)));

        ArrayList<TemplateInstantiation> templateInstantiations =
                new ArrayList<TemplateInstantiation>(Arrays.asList(templateInstantiation));

        Slice slice = new BasicSlice(policies, entities, templates, templateInstantiations);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, slice);
    }

    /** Test using schema parsing. */
    @Test
    public void testSchemaParsingDeny() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", principal.getEUID());
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p =
                "permit(\n"
                        + "principal=="
                        + principal
                        + ",\n"
                        + "action=="
                        + action
                        + ",\n"
                        + "resource=="
                        + resource
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "resource.owner=="
                        + principal
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);

        // Schema says resource.owner is a bool, so we should get a parse failure, which causes
        // `isAuthorized()` to throw a `BadRequestException`.
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        Optional.of(currentContext),
                        Optional.of(loadSchemaResource("/schema_parsing_deny_schema.json")),
                        true);
        assertFailure(request, slice);
    }

    /** Test using schema parsing. */
    @Test
    public void testSchemaParsingAllow() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("foo", new PrimString("bar"));
        Set<EntityUID> principalParents = new HashSet<>();
        Entity principal = new Entity(new EntityUID(principalType, principalId), principalAttributes, principalParents);
        entities.add(principal);

        String actionId = "view";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        Entity action = new Entity(new EntityUID(actionType, actionId), actionAttributes, actionParents);
        entities.add(action);

        String resourceId = "photo.jpg";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", principal.getEUID());
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(new EntityUID(resourceType, resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

        String p =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID().toString()
                        + ",\n"
                        + "action=="
                        + action.getEUID().toString()
                        + ",\n"
                        + "resource=="
                        + resource.getEUID().toString()
                        + "\n"
                        + ")\n"
                        + " when {\n"
                        + "resource.owner=="
                        + principal.getEUID().toString()
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);

        // Schema says resource.owner is a User, so we should not get a parse failure.
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        Optional.of(currentContext),
                        Optional.of(loadSchemaResource("/schema_parsing_allow_schema.json")),
                        true);
        assertAllowed(request, slice);
    }
}
