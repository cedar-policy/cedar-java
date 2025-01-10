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
import com.cedarpolicy.model.Effect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PolicyTests {
    @Test
    public void parseStaticPolicyTests() {
        assertDoesNotThrow(() -> {
            var policy1 = Policy.parseStaticPolicy("permit(principal, action, resource);");
            var policy2 = Policy.parseStaticPolicy("permit(principal, action, resource) when { principal has x && principal.x == 5};");
            assertNotEquals(policy1.policyID, policy2.policyID);
        });
        assertThrows(InternalException.class, () -> {
            Policy.parseStaticPolicy("permit();");
        });
        assertThrows(NullPointerException.class, () -> {
            Policy.parseStaticPolicy(null);
        });
    }

    @Test
    public void parsePolicyTemplateTests() {
        // valid template
        assertDoesNotThrow(() -> {
            String tbody = "permit(principal == ?principal, action, resource in ?resource);";
            var template = Policy.parsePolicyTemplate(tbody);
            assertEquals(tbody, template.policySrc);
        });
        // ?resource slot shouldn't be used in the principal scope
        assertThrows(InternalException.class, () -> {
            Policy.parsePolicyTemplate("permit(principal in ?resource, action, resource);");
        });
        // a static policy is not a template
        assertThrows(InternalException.class, () -> {
            Policy.parsePolicyTemplate("permit(principal, action, resource);");
        });
    }

    @Test
    public void staticPolicyToJsonTests() throws InternalException {
        assertThrows(NullPointerException.class, () -> {
            Policy p = new Policy(null, null);
            p.toJson();
        });
        assertThrows(InternalException.class, () -> {
            Policy p = new Policy("permit();", null);
            p.toJson();
        });

        Policy p = Policy.parseStaticPolicy("permit(principal, action, resource);");
        String actualJson = p.toJson();
        String expectedJson = "{\"effect\":\"permit\",\"principal\":{\"op\":\"All\"},\"action\":{\"op\":\"All\"},"
                + "\"resource\":{\"op\":\"All\"},\"conditions\":[]}";
        assertEquals(expectedJson, actualJson);
    }

    @Test
    public void policyTemplateToJsonFailureTests() throws InternalException {
        // conversion to JSON currently only works for static policies
        try {
            String tbody = "permit(principal == ?principal, action, resource in ?resource);";
            Policy template = Policy.parsePolicyTemplate(tbody);
            template.toJson();
            fail("Expected InternalException");
        } catch (InternalException e) {
            assertTrue(e.getMessage().contains("expected a static policy, got a template containing the slot ?resource"));
        }
    }

    @Test
    public void policyFromJsonTest() throws InternalException {
        assertThrows(NullPointerException.class, () -> {
            String nullJson = null;
            Policy.fromJson(null, nullJson);
        });
        assertThrows(InternalException.class, () -> {
            String invalidJson = "effect\":\"permit\",\"principal\":{\"op\":\"All\"},\"action\":{\"op\":\"All\"}";
            Policy.fromJson(null, invalidJson);
        });

        String validJson = "{\"effect\":\"permit\",\"principal\":{\"op\":\"All\"},\"action\":{\"op\":\"All\"},"
                + "\"resource\":{\"op\":\"All\"},\"conditions\":[]}";
        Policy p = Policy.fromJson(null, validJson);
        String actualJson = p.toJson();
        assertEquals(validJson, actualJson);
    }

    @Test void policyEffectTest() throws InternalException {

        assertThrows(NullPointerException.class, () -> {
            Policy p = new Policy(null, null);
            p.effect();
        });

        // For effects not in {permit, forbid}
        assertThrows(InternalException.class, () -> {
            Policy p = new Policy("perm(principal == ?principal, action, resource in ?resource);", null);
            p.effect();
        });

        // Tests for static policies
        Policy permitPolicy = new Policy("permit(principal, action, resource);", null);
        assertEquals(permitPolicy.effect(), Effect.PERMIT);

        Policy forbidPolicy = new Policy("forbid(principal, action, resource);", null);
        assertEquals(forbidPolicy.effect(), Effect.FORBID);

        // Tests for templates
        Policy permitTemplate = new Policy("permit(principal == ?principal, action, resource == ?resource);", null);
        assertEquals(permitTemplate.effect(), Effect.PERMIT);

        Policy forbidTemplate = new Policy("forbid(principal == ?principal, action, resource == ?resource);", null);
        assertEquals(forbidTemplate.effect(), Effect.FORBID);

    }
}
