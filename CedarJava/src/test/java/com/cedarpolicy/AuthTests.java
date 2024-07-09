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
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

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
                assertEquals(Decision.Allow, response.getDecision());
                assertEquals(response.getMustBeDetermining().iterator().next(), "p0");
                assertTrue(response.getNontrivialResiduals().isEmpty());
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
                assertTrue(response.getDecision() == null);
                assertEquals("p0", response.getResiduals().entrySet().iterator().next().getKey());
            } catch (Exception e) {
                fail("error: " + e.toString());
            }
        });
    }

    private Executable assumePartialEvaluation(Executable executable) {
        return () -> {
            try {
                executable.execute();
            } catch (MissingExperimentalFeatureException e) {
                System.err.println("Skipping assertions: " + e.getMessage());
            }
        };
    }
}
