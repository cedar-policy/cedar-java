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

package com.cedarpolicy.pbt;

import static com.cedarpolicy.TestUtil.loadSchemaResource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.LinkValue;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.policy.TemplateLink;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.DateTime;
import com.cedarpolicy.value.Decimal;
import com.cedarpolicy.value.Duration;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.IpAddress;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.functions.Offset;
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

    private void assertAllowed(AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, policySet, entities);
            if (response.success.isPresent()) {
                final var success = assertDoesNotThrow(() -> response.success.get());
                assertTrue(success.isAllowed());
            } else {
                fail(String.format("Expected a success response but got %s", response.toString()));
            }
        });
    }

    private void assertNotAllowed(AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, policySet, entities);
            if (response.success.isPresent()) {
                final var success = assertDoesNotThrow(() -> response.success.get());
                assertFalse(success.isAllowed());
            } else {
                fail(String.format("Expected a success response but got %s", response.toString()));
            }
        });
    }

    private void assertFailure(AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
        assertDoesNotThrow(() -> {
            final AuthorizationResponse response = ENGINE.isAuthorized(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal.getEUID(), action.getEUID(), resource.getEUID(), currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertNotAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertNotAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
    }

    /** Test DateTime extension. */
    @Test
    public void testDateTimeExtension() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("DOB", new DateTime("2000-01-01"));
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
                        + "principal.DOB > datetime(\"1999-01-01\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
    }

    /** Test DateTime extension with different timezones. */
    @Test
    public void testDateTimeExtensionWithDifferentTimezones() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("DOB", new DateTime("2023-12-25T10:30:45-0800"));
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
                        + "principal.DOB > datetime(\"2023-12-25T14:30:20-0400\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
    }

    /** Test Duration extension. */
    @Test
    public void testDurationExtension() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("sessionDuration", new Duration("2h30m"));
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
                        + "principal.sessionDuration > duration(\"1h\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
    }

    /** Test Offset extension. */
    @Test
    public void testOffsetExtension() {
        Set<Entity> entities = new HashSet<>();
        String principalId = "alice";
        Map<String, Value> principalAttributes = new HashMap<>();
        DateTime baseDateTime = new DateTime("2023-12-25T10:30:45Z");
        Duration offsetDuration = new Duration("2h");
        principalAttributes.put("appointmentTime", new Offset(baseDateTime, offsetDuration));
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
                        + "principal.appointmentTime == datetime(\"2023-12-25T10:30:45Z\").offset(duration(\"2h\"))\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
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

        LinkValue linkValue = new LinkValue(principalSlot, new EntityUID(principalType, "alice"));

        final String linkId = "ID0_alice";
        TemplateLink templateLink =
                new TemplateLink(
                        policyId,
                        linkId,
                        new ArrayList<LinkValue>(Arrays.asList(linkValue)));

        ArrayList<TemplateLink> templateLinks =
                new ArrayList<TemplateLink>(Arrays.asList(templateLink));

        PolicySet policySet = new PolicySet(policies, templates, templateLinks);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        assertAllowed(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        Optional.of(currentContext),
                        Optional.of(loadSchemaResource("/schema_parsing_deny_schema.json")),
                        true);
        assertFailure(request, policySet, entities);
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
        PolicySet policySet = new PolicySet(policies);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal,
                        action,
                        resource,
                        Optional.of(currentContext),
                        Optional.of(loadSchemaResource("/schema_parsing_allow_schema.json")),
                        true);
        assertAllowed(request, policySet, entities);
    }

    private static final Schema ENUM_SCHEMA = loadSchemaResource("/enum_schema.json");

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
        Entity alice = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

        Entity bob = new Entity(userType.of("bob"), new HashMap<>() {
            {
                put("name", new PrimString("Bob"));
            }
        }, new HashSet<>());

        // Tasks
        Entity task1 = new Entity(taskType.of("task1"), new HashMap<>() {
            {
                put("owner", alice.getEUID());
                put("name", new PrimString("Complete project"));
                put("status", new EntityUID(colorType, "Red"));
            }
        }, new HashSet<>());

        Entity task2 = new Entity(taskType.of("task2"), new HashMap<>() {
            {
                put("owner", bob.getEUID());
                put("name", new PrimString("Review code"));
                put("status", new EntityUID(colorType, "Green"));
            }
        }, new HashSet<>());

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
     * Test authorization requests which results in Deny due to enum type
     */
    @Test
    public void testValidEnumAuthorizationDeny() {
        EntityUID principal = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        EntityUID action = new EntityUID(EntityTypeName.parse("Action").get(), "UpdateTask");
        EntityUID resource = new EntityUID(EntityTypeName.parse("Task").get(), "task2"); // task2 has Green status

        Set<Entity> entities = buildEntitiesForEnumTests();

        var policies = new HashSet<Policy>();
        policies.add(new Policy("""
                permit(
                    principal,
                    action == Action::"UpdateTask",
                    resource
                ) when {
                    resource.status != Color::"Green"
                };
                """, "notGreenPolicy"));

        var policySet = new PolicySet(policies);
        AuthorizationRequest request = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        assertNotAllowed(request, policySet, entities);
    }

    /**
     * Test authorization requests which results in Permit due to enum type
     */
    @Test
    public void testValidEnumAuthorizationAllow() {
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
     * Test authorization requests where policy references multiple enums
     */
    @Test
    public void testEnumAuthorizationMultipleValues() {
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
     * Test authorization with schema validation - positive case with valid enum entities.
     */
    @Test
    public void testValidEnumAuthorizationWithValidation() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName applicationType = EntityTypeName.parse("Application").get();

        // Create valid entities
        Entity principalEntity = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

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
        AuthorizationRequest request = new AuthorizationRequest(principalEntity, actionEntity, resourceEntity,
                Optional.of(new HashMap<>()), Optional.of(ENUM_SCHEMA), true
        );

        assertAllowed(request, policySet, entities);
    }

    /**
     * Test authorization with schema validation - negative case with invalid enum entity.
     */
    @Test
    public void testInvalidEnumAuthorizationWithValidation() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName applicationType = EntityTypeName.parse("Application").get();

        // Create entities with invalid enum value
        Entity principalEntity = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

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
        AuthorizationRequest request = new AuthorizationRequest(principalEntity, actionEntity, resourceEntity,
                Optional.of(new HashMap<>()), Optional.of(ENUM_SCHEMA), true // Enable request validation - this should
                                                                             // catch the invalid enum
        );

        // Should return failure response due to invalid enum value when schema validation is enabled
        assertFailure(request, policySet, entities);
    }
}
