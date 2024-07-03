package com.cedarpolicy;

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
}
