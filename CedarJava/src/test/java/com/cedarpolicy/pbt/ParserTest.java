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
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.EntityIdentifier;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Assertions;

/** Property based tests for Rust Parser. */
public class ParserTest {

    private final EntityTypeName user;
    private final EntityTypeName actionType;
    private final EntityTypeName resourceType;

    public ParserTest() {
        user = EntityTypeName.parse("User").get();
        actionType = EntityTypeName.parse("Action").get();
        resourceType = EntityTypeName.parse("Resource").get();
    }


    /*
     * Single policy (Universal permit) Tests
     * policy: permit(principal, action, resource);
     * Request: random principal, random action, random resource)
     * Response: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testUniversalPermit(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();
        /*
         *  Generate a random principal
         */
        EntityIdentifier principalId = new EntityIdentifier(Utils.strings());
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();

        var principal = new Entity(user.of(principalId), principalAttributes, principalParents);
        entities.add(principal);

        /*
         *  Generate a random Action
         */
        String actionId = Utils.strings();
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        var action = new Entity(actionType.of(actionId), actionAttributes, actionParents);
        entities.add(action);

        /*
         *  Generate a random Resource
         */
        String resourceId = Utils.strings();
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(resourceType.of(resourceId), resourceAttributes, resourceParents);
        entities.add(resource);
        /*
         *  Generate a universal permit policy
         */
        Policy policy = new Policy("permit(principal\n,action\n,resource\n);", ids);
        Set<Policy> policies = new HashSet<>();
        policies.add(policy);
        Slice slice = new BasicSlice(policies, entities);
        Map<String, Value> currentContext = new HashMap<>();
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse response =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(request, slice));
        assertNotNull(response.success);
        Assertions.assertTrue(response.success.isAllowed());
    }

    /**
     * Single policy tests
     *
     * <p>policy: permit(principal=x, action=y, resource=z); Request: x, y, z Response: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testSinglePolicy(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();

        /*
         *  Generate a random principal
         */
        String principalId = Utils.strings();
        Map<String, Value> principalAttributes = new HashMap<>();
        Set<EntityUID> principalParents = new HashSet<>();
        var principal = new Entity(user.of(principalId), principalAttributes, principalParents);
        entities.add(principal);

        /*
         *  Generate a random Action
         */
        String actionId = Utils.strings();
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<EntityUID> actionParents = new HashSet<>();
        var action = new Entity(actionType.of(actionId), actionAttributes, actionParents);
        entities.add(action);

        /*
         *  Generate a random Resource
         */
        String resourceId = Utils.strings();
        Map<String, Value> resourceAttributes = new HashMap<>();
        Set<EntityUID> resourceParents = new HashSet<>();
        var resource = new Entity(resourceType.of(resourceId), resourceAttributes, resourceParents);
        entities.add(resource);

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
        AuthorizationRequest request =
                new AuthorizationRequest(
                        principal, action, resource, currentContext);
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse response =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(request, slice));
        assertNotNull(response.success);
        Assertions.assertTrue(response.success.isAllowed());
    }

    /*
     * Single policy tests
     *
     * policy: permit(principal=x, action in [..., y,...], resource=z);
     * Request: x, y, z
     * Response: Permit
     *
     * @param ids
     */
    @Property(tries = 100)
    void testActionIn(@ForAll String ids) {
        Set<Entity> entities = new HashSet<>();

        /*
         *  Generate a random principal
         */
        var principal = new EntityGen(user).arbitraryEntity();
        entities.add(principal);

        /*
         *  Generate a random Action
         */
        var gen = new EntityGen(actionType);
        List<Entity> actions = gen.arbitraryEntities();
        entities.addAll(actions);
        var action = actions.get(0);
        /*
         *  Generate a random Resource
         */
        var resource = new EntityGen(resourceType).arbitraryEntity();
        entities.add(resource);
        /*
         *  Generate a universal permit policy
         */
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
                        + ");";
        Policy policy = new Policy(p, ids);
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
        AuthorizationEngine authEngine = new BasicAuthorizationEngine();
        AuthorizationResponse response =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(request, slice));
        assertNotNull(response.success);
        Assertions.assertTrue(response.success.isAllowed());

        String actionList =
                "[" + actions.stream().map(a -> a.getEUID().toString()).collect(Collectors.joining(",")) + "]";
        String p2 =
                "permit(\n"
                        + "principal=="
                        + principal.getEUID()
                        + ",\n"
                        + "action in"
                        + actionList
                        + ",\n"
                        + "resource=="
                        + resource.getEUID()
                        + "\n"
                        + ");";

        Policy policy2 = new Policy(p2, ids);
        policies = new HashSet<>();
        policies.add(policy2);
        Slice slice2 = new BasicSlice(policies, entities);
        Map<String, Value> currentContext2 = new HashMap<>();
        int index = Arbitraries.integers().between(0, actions.size() - 1).sample();
        action = actions.get(index);
        AuthorizationRequest request2 =
                new AuthorizationRequest(
                        principal, action, resource, currentContext2);
        AuthorizationResponse response2 =
                Assertions.assertDoesNotThrow(() -> authEngine.isAuthorized(request2, slice2));
        assertNotNull(response2.success);
        Assertions.assertTrue(response2.success.isAllowed());
    }
}
