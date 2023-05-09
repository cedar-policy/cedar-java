package cedarpolicy.pbt;

import cedarpolicy.AuthorizationEngine;
import cedarpolicy.WrapperAuthorizationEngine;
import cedarpolicy.model.AuthorizationQuery;
import cedarpolicy.model.AuthorizationResult;
import cedarpolicy.model.slice.BasicSlice;
import cedarpolicy.model.slice.Entity;
import cedarpolicy.model.slice.Policy;
import cedarpolicy.model.slice.Slice;
import cedarpolicy.value.Value;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Assertions;

/** Property based tests for Rust Parser. */
public class ParserTest {
    /*
     * Single policy (Universal permit) Tests
     * policy: permit(principal, action, resource);
     * Query: random principal, random action, random resource)
     * Result: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testUniversalPermit(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();
        /*
         *  Generate a random principal
         */
        String principal = "User::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(principal, principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        String action = "Action::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        entities.add(new Entity(action, actionAttributes, actionParents));

        /*
         *  Generate a random Resource
         */
        String resource = "Resource::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(resource, resourceAttributes, resourceParents));
        /*
         *  Generate a universal permit policy
         */
        Policy policy = new Policy("permit(principal\n,action\n,resource\n);", ids);
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

    /**
     * Single policy tests
     *
     * <p>policy: permit(principal=x, action=y, resource=z); Query: x, y, z Result: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testSinglePolicy(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();

        /*
         *  Generate a random principal
         */
        String principal = "User::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(principal, principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        String action = "Action::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        entities.add(new Entity(action, actionAttributes, actionParents));

        /*
         *  Generate a random Resource
         */
        String resource = "Resource::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(resource, resourceAttributes, resourceParents));

        /*
         *  Generate a universal permit policy
         */
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
                        + ");";
        Policy policy = new Policy(p, ids);
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

    /*
     * Single policy tests
     *
     * policy: permit(principal=x, action in [..., y,...], resource=z);
     * Query: x, y, z
     * Result: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testActionIn(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();

        /*
         *  Generate a random principal
         */
        String principal = "User::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(principal, principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        List<Entity> actions = ActionGen.getEntities();
        entities.addAll(actions);
        String action = actions.get(0).uid;
        /*
         *  Generate a random Resource
         */
        String resource = "Resource::" + "\"" + Utils.strings() + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(resource, resourceAttributes, resourceParents));
        /*
         *  Generate a universal permit policy
         */
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
                        + ");";
        Policy policy = new Policy(p, ids);
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
        String actionList =
                "[" + actions.stream().map(x -> x.uid).collect(Collectors.joining(",")) + "]";
        String p2 =
                "permit(\n"
                        + "principal=="
                        + principal
                        + ",\n"
                        + "action in"
                        + actionList
                        + ",\n"
                        + "resource=="
                        + resource
                        + "\n"
                        + ");";

        Policy policy2 = new Policy(p2, ids);
        policies = new HashSet<>();
        policies.add(policy2);
        Slice slice2 = new BasicSlice(policies, entities);
        Map<String, Value> currentContext2 = new HashMap<>();
        int index = Arbitraries.integers().between(0, actions.size() - 1).sample();
        action = actions.get(index).uid;
        AuthorizationQuery query2 =
                new AuthorizationQuery(
                        principal, action, resource, currentContext2, Optional.empty());
        AuthorizationResult result2 =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query2, slice2));
        Assertions.assertTrue(result2.isAllowed());
    }
}
