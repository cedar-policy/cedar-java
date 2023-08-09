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

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.model.slice.Slice;
import com.cedarpolicy.serializer.JsonEUID;
import com.cedarpolicy.value.Value;
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
        String principalType = "User";
        String principalId = Utils.strings();
        String principal = principalType+"::\"" + principalId + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(principalType, principalId), principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        String actionType = "Action";
        String actionId = Utils.strings();
        String action = actionType+"::\"" + actionId + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(actionType, actionId), actionAttributes, actionParents));

        /*
         *  Generate a random Resource
         */
        String resourceType = "Resource";
        String resourceId = Utils.strings();
        String resource = resourceType+"::\"" + resourceId + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(resourceType, resourceId), resourceAttributes, resourceParents));
        /*
         *  Generate a universal permit policy
         */
        Policy policy = new Policy("permit(principal\n,action\n,resource\n);", ids);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest query =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse result =
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
        String principalType = "User";
        String principalId = Utils.strings();
        String principal = principalType+"::\"" + principalId + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(principalType, principalId), principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        String actionType = "Action";
        String actionId = Utils.strings();
        String action = actionType+"::\"" + actionId + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(actionType, actionId), actionAttributes, actionParents));

        /*
         *  Generate a random Resource
         */
        String resourceType = "Resource";
        String resourceId = Utils.strings();
        String resource = resourceType+"::\"" + resourceId + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(resourceType, resourceId), resourceAttributes, resourceParents));

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
        AuthorizationRequest query =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse result =
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
        String principalType = "User";
        String principalId = Utils.strings();
        String principal = principalType+"::\"" + principalId + "\"";
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<String> principalParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(principalType, principalId), principalAttributes, principalParents));

        /*
         *  Generate a random Action
         */
        List<Entity> actions = ActionGen.getEntities();
        entities.addAll(actions);
        String action = actions.get(0).getEuid().toCedar();
        /*
         *  Generate a random Resource
         */
        String resourceType = "Resource";
        String resourceId = Utils.strings();
        String resource = resourceType+"::\"" + resourceId + "\"";
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<String> resourceParents = new HashSet<>();
        entities.add(new Entity(new JsonEUID(resourceType, resourceId), resourceAttributes, resourceParents));
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
        AuthorizationRequest query =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse result =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query, slice));

        Assertions.assertTrue(result.isAllowed());
        String actionList =
                "[" + actions.stream().map(x -> x.getEuid().toCedar()).collect(Collectors.joining(",")) + "]";
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
        action = actions.get(index).getEuid().toCedar();
        AuthorizationRequest query2 =
                new AuthorizationRequest(
                        principal, action, resource, currentContext2);
        AuthorizationResponse result2 =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(query2, slice2));
        Assertions.assertTrue(result2.isAllowed());
    }
}
