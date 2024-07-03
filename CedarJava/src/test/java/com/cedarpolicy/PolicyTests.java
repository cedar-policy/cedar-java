package com.cedarpolicy;

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.Policy;
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
        assertDoesNotThrow(() -> {
            String tbody = "permit(principal == ?principal, action, resource in ?resource);";
            var template = Policy.parsePolicyTemplate(tbody);
            assertEquals(tbody, template.policySrc);
        });
        assertThrows(InternalException.class, () -> {
            Policy.parsePolicyTemplate("permit(principal in ?resource, action, resource);");
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
        try {
            String tbody = "permit(principal == ?principal, action, resource in ?resource);";
            Policy template = Policy.parsePolicyTemplate(tbody);
            template.toJson();
            fail("Expected InternalException");
        } catch (InternalException e) {
            assertTrue(e.getMessage().contains("expected a static policy, got a template containing the slot ?resource"));
        }
    }
}
