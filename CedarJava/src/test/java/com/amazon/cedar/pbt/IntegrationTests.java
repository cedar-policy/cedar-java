package cedarpolicy.pbt;

import static cedarpolicy.TestUtil.loadSchemaResource;

import cedarpolicy.AuthorizationEngine;
import cedarpolicy.WrapperAuthorizationEngine;
import cedarpolicy.model.AuthorizationQuery;
import cedarpolicy.model.AuthorizationResult;
import cedarpolicy.model.slice.BasicSlice;
import cedarpolicy.model.slice.Entity;
import cedarpolicy.model.slice.EntityTypeAndId;
import cedarpolicy.model.slice.Instantiation;
import cedarpolicy.model.slice.Policy;
import cedarpolicy.model.slice.Slice;
import cedarpolicy.model.slice.TemplateInstantiation;
import cedarpolicy.value.Decimal;
import cedarpolicy.value.EntityUID;
import cedarpolicy.value.IpAddress;
import cedarpolicy.value.PrimString;
import cedarpolicy.value.Value;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Integration tests. */
public class IntegrationTests {
    /** Tests a single attribute: resource.owner. */
    @Test
    public void testResourceAttribute() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", new EntityUID(principal));
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

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
        Policy policy = new Policy(p, "001");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Tests a randomly generated attribute for resource. */
    @Property(tries = 50)
    public void testRandomResourceAttribute(@ForAll @IntRange(min = 1, max = 50) int count) {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        while (resourceAttributes.size() < count) {
            resourceAttributes.put(Utils.strings(), Utils.primStrings());
        }
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);
        /*
         *      select random attributes
         */
        List<String> attrKeys = new ArrayList<String>(resourceAttributes.keySet());
        int n = Utils.intInRange(0, count - 1);
        StringBuilder attributes = new StringBuilder();
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
                        + attributes.toString()
                        + "\n};";
        attributes = null;
        Policy policy = new Policy(p, "ID" + String.valueOf(count));
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Tests a randomly generated with one bad attribute for resource Result: Deny. */
    @Property(tries = 50)
    public void testRandomResourceAttributeDeny(@ForAll @IntRange(min = 1, max = 200) int count) {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        while (resourceAttributes.size() < count) {
            resourceAttributes.put(Utils.strings(), Utils.primStrings());
        }
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);
        /*
         *      select random attributes
         */
        List<String> attrKeys = new ArrayList<String>(resourceAttributes.keySet());
        int n = Utils.intInRange(0, count - 1);
        StringBuilder attributes = new StringBuilder();
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
                attributes.append(resourceAttributes.get(key));
            }
            attributes.append(" && ");
        }
        attributes.delete(attributes.length() - 3, attributes.length()); // remove last &&
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
                        + attributes.toString()
                        + "};";
        attributes = null;
        Policy policy = new Policy(p, "ID" + String.valueOf(count));
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertFalse(result.isAllowed());
    }

    /** Tests a single attribute: resource.owner. */
    @Test
    public void testUnspecifiedResource() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", new EntityUID(principal));
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

        String p = "permit(principal, action, resource);";
        Policy policy = new Policy(p, "001");
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        Optional.of(principal),
                        action,
                        Optional.empty(),
                        currentContext,
                        Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Test IpAddress extension. */
    @Test
    public void testIpAddressExtension() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("ip", new IpAddress("192.168.0.24"));
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

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
                        + "principal.ip == ip(\"192.168.0.24\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Test Decimal extension. */
    @Test
    public void testDecimalExtension() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("val", new Decimal("1.0000"));
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

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
                        + "principal.val == decimal(\"1.0000\")\n"
                        + "};";
        final String policyId = "ID0";
        Policy policy = new Policy(p, policyId);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Use template slots to tests a single attribute: resource.owner. */
    @Test
    public void testTemplateResourceAttribute() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", new EntityUID(principal));
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

        final String principalSlot = "?principal";

        String p =
                "permit(\n"
                        + "principal=="
                        + principalSlot
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
                        + principalSlot
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
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal, action, resource, currentContext, Optional.empty());
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }

    /** Test using schema parsing. */
    @Test
    public void testSchemaParsingDeny() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", new EntityUID(principal));
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

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

        // Schema says resource.owner is a bool, so we should get a parse failure and a deny.
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal,
                        action,
                        resource,
                        currentContext,
                        Optional.of(loadSchemaResource("/schema_parsing_deny_schema.json")));
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertFalse(result.isAllowed());
    }

    /** Test using schema parsing. */
    @Test
    public void testSchemaParsingAllow() {
        Set<Entity> entities = new HashSet<>();
        String principal = "User::\"alice\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        principalAttributes.put("foo", new PrimString("bar"));
        Set<String> principalParents = new HashSet<>();
        Entity e = new Entity(principal, principalAttributes, principalParents);
        entities.add(e);

        String action = "Action::\"view\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity act = new Entity(action, actionAttributes, actionParents);
        entities.add(act);

        String resource = "Resource::" + "\"" + "photo.jpg" + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        resourceAttributes.put("owner", new EntityUID(principal));
        Set<String> resourceParents = new HashSet<>();
        e = new Entity(resource, resourceAttributes, resourceParents);
        entities.add(e);

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

        // Schema says resource.owner is a User, so we should not get a parse failure.
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationQuery query =
                new AuthorizationQuery(
                        principal,
                        action,
                        resource,
                        currentContext,
                        Optional.of(loadSchemaResource("/schema_parsing_allow_schema.json")));
        AuthorizationEngine authEngine = new WrapperAuthorizationEngine();
        AuthorizationResult result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));
        Assertions.assertTrue(result.isAllowed());
    }
}
