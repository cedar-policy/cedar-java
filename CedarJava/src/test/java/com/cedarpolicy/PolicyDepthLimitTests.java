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
import com.cedarpolicy.model.exception.PolicyParseException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The depth-limited parse overloads reject policies nested deeper than the limit. */
public class PolicyDepthLimitTests {

    // `1 + 2` inside a `when` clause has expression depth 4.
    private static final String POLICY = "permit(principal, action, resource) when { 1 + 2 };";
    private static final String TEMPLATE = "permit(principal == ?principal, action, resource) when { 1 + 2 };";
    private static final String POLICY_JSON = "{\"effect\":\"permit\",\"principal\":{\"op\":\"All\"},"
            + "\"action\":{\"op\":\"All\"},\"resource\":{\"op\":\"All\"},\"conditions\":[{\"kind\":\"when\","
            + "\"body\":{\"+\":{\"left\":{\"Value\":1},\"right\":{\"Value\":2}}}}]}";
    private static final String POLICY_SET_JSON =
            "{\"staticPolicies\":{\"p\":" + POLICY_JSON + "},\"templates\":{},\"templateLinks\":[]}";
    private static final String TOO_DEEP = "expression depth 4 exceeds the configured limit of 3";

    @Test
    public void parsePoliciesAcceptsPolicyAtLimit() {
        PolicySet policySet = assertDoesNotThrow(
                () -> PolicySet.parsePolicies(POLICY + " forbid(principal, action, resource);", 4));
        assertEquals(2, policySet.getNumPolicies());
    }

    @Test
    public void parsePoliciesRejectsPolicyOverLimitWithParseDiagnostics() {
        String src = POLICY + " forbid(principal, action, resource);";
        PolicyParseException e = assertThrows(PolicyParseException.class, () -> PolicySet.parsePolicies(src, 3));
        assertEquals(1, e.getDetailedErrors().size());
        assertEquals(TOO_DEEP, e.getDetailedErrors().get(0).message);
    }

    @Test
    public void parsePoliciesRejectsDeepNestingWithoutStackOverflow() {
        int n = 10000;
        String deep = "(".repeat(n) + "1" + ")".repeat(n);
        String src = "permit(principal, action, resource) when { " + deep + " };";
        PolicyParseException e = assertThrows(PolicyParseException.class, () -> PolicySet.parsePolicies(src, 100));
        assertTrue(e.getMessage().contains("exceeds the configured limit of 100"), e.getMessage());
    }

    @Test
    public void parsePoliciesJsonEnforcesLimit() {
        assertEquals(1, assertDoesNotThrow(() -> PolicySet.parsePoliciesJson(POLICY_SET_JSON, 4)).getNumPolicies());
        InternalException e =
                assertThrows(InternalException.class, () -> PolicySet.parsePoliciesJson(POLICY_SET_JSON, 3));
        assertTrue(e.getMessage().contains(TOO_DEEP), e.getMessage());
    }

    @Test
    public void parsePoliciesJsonRejectsManyShallowConditions() {
        // Each condition is shallow JSON, but Cedar joins them into one deep expression.
        String condition = "{\"kind\":\"when\",\"body\":{\"Value\":true}}";
        String conditions = String.join(",", java.util.Collections.nCopies(100_000, condition));
        String policy = "{\"effect\":\"permit\",\"principal\":{\"op\":\"All\"},\"action\":{\"op\":\"All\"},"
                + "\"resource\":{\"op\":\"All\"},\"conditions\":[" + conditions + "]}";
        InternalException e = assertThrows(InternalException.class, () -> Policy.fromJson(null, policy, 1000));
        assertTrue(e.getMessage().contains("exceeds the configured limit of 1000"), e.getMessage());
    }

    @Test
    public void parseStaticPolicyEnforcesLimit() {
        assertDoesNotThrow(() -> Policy.parseStaticPolicy(POLICY, 4));
        InternalException e = assertThrows(InternalException.class, () -> Policy.parseStaticPolicy(POLICY, 3));
        assertTrue(e.getMessage().contains(TOO_DEEP), e.getMessage());
    }

    @Test
    public void parsePolicyTemplateEnforcesLimit() {
        assertDoesNotThrow(() -> Policy.parsePolicyTemplate(TEMPLATE, 4));
        InternalException e = assertThrows(InternalException.class, () -> Policy.parsePolicyTemplate(TEMPLATE, 3));
        assertTrue(e.getMessage().contains(TOO_DEEP), e.getMessage());
    }

    @Test
    public void fromJsonEnforcesLimit() {
        Policy policy = assertDoesNotThrow(() -> Policy.fromJson("p", POLICY_JSON, 4));
        assertEquals("p", policy.policyID);
        InternalException e = assertThrows(InternalException.class, () -> Policy.fromJson("p", POLICY_JSON, 3));
        assertTrue(e.getMessage().contains(TOO_DEEP), e.getMessage());
    }

    @Test
    public void negativeLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PolicySet.parsePolicies(POLICY, -1));
        assertThrows(IllegalArgumentException.class, () -> PolicySet.parsePoliciesJson(POLICY_SET_JSON, -1));
        assertThrows(IllegalArgumentException.class, () -> Policy.parseStaticPolicy(POLICY, -1));
        assertThrows(IllegalArgumentException.class, () -> Policy.parsePolicyTemplate(TEMPLATE, -1));
        assertThrows(IllegalArgumentException.class, () -> Policy.fromJson(null, POLICY_JSON, -1));
    }
}
