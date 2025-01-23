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

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PolicySetTests {
    private static final String TEST_RESOURCES_DIR = "src/test/resources/";

    @Test
    public void parsePoliciesTests() throws InternalException, IOException {
        PolicySet policySet = PolicySet.parsePolicies(Path.of(TEST_RESOURCES_DIR + "policies.cedar"));
        for (Policy p: policySet.policies) {
            assertNotNull(p.policySrc);
        }
        // Make sure the policy IDs are unique as Policies are made
        assertEquals(2, policySet.policies.stream().map(p -> p.policyID).distinct().count());
        assertEquals(2, policySet.policies.size());
        assertEquals(0, policySet.templates.size());
    }

    @Test
    public void parsePoliciesStringTests() throws InternalException {
        PolicySet policySet = PolicySet.parsePolicies("permit(principal, action, resource);");
        PolicySet policySet2 = PolicySet.parsePolicies("permit(principal, action, resource) when { principal has x && principal.x == 5};");
        for (Policy p: policySet.policies) {
            assertNotNull(p.policySrc);
        }
        assertEquals(1, policySet.policies.size());
        assertEquals(0, policySet.templates.size());
        for (Policy p: policySet2.policies) {
            assertNotNull(p.policySrc);
        }
        assertEquals(1, policySet2.policies.size());
        assertEquals(0, policySet2.templates.size());
    }

    @Test
    public void parseTemplatesTests() throws InternalException, IOException {
        PolicySet policySet = PolicySet.parsePolicies(Path.of(TEST_RESOURCES_DIR + "template.cedar"));
        for (Policy p: policySet.policies) {
            assertNotNull(p.policySrc);
        }
        assertEquals(2, policySet.policies.size());

        for (Policy p: policySet.templates) {
            assertNotNull(p.policySrc);
        }
        assertEquals(1, policySet.templates.size());
    }

    @Test
    public void parsePoliciesExceptionTests() throws InternalException, IOException {
        assertThrows(IOException.class, () -> {
            PolicySet.parsePolicies(Path.of("nonExistentFilePath.cedar"));
        });
        assertThrows(InternalException.class, () -> {
            PolicySet.parsePolicies(Path.of(TEST_RESOURCES_DIR + "malformed_policy_set.cedar"));
        });
    }

    @Test
    public void getNumTests() throws InternalException, IOException {
        // Null policy set
        PolicySet nullPolicySet = new PolicySet(null, null, null);
        assertEquals(0, nullPolicySet.getNumPolicies());
        assertEquals(0, nullPolicySet.getNumTemplates());

        // Empty policy set
        PolicySet emptyPolicySet = new PolicySet();
        assertEquals(0, emptyPolicySet.getNumPolicies());
        assertEquals(0, emptyPolicySet.getNumTemplates());

        // Non-empty policy set
        PolicySet policySet = PolicySet.parsePolicies(Path.of(TEST_RESOURCES_DIR + "template.cedar"));
        assertEquals(2, policySet.getNumPolicies());
        assertEquals(1, policySet.getNumTemplates());
    }
}
