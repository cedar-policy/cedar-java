package com.cedarpolicy;

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PolicyTests {
    @Test
    public void parseStaticPolicyTests() {
        assertDoesNotThrow(() -> {
            var policy1 = Policy.parseStaticPolicy("permit(principal, action, resource);");
            var policy2 = Policy.parseStaticPolicy("permit(principal, action, resource) when { principal has x && principal.x == 5};");
            assertNotEquals(policy1.policyID.equals(policy2.policyID), true);
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
            assertTrue(template.policySrc.equals(tbody));
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
    public void validateTemplateLinkedPolicySuccessTest() {
        Policy p = new Policy("permit(principal == ?principal, action, resource in ?resource);", null);
        EntityUID principal1 = EntityUID.parse("Library::User::\"Victor\"").get();
        EntityUID resource1 = EntityUID.parse("Library::Book::\"The black Swan\"").get();

        Policy p2 = new Policy("permit(principal, action, resource in ?resource);", null);
        EntityUID resource2 = EntityUID.parse("Library::Book::\"Thinking Fast and Slow\"").get();

        Policy p3 = new Policy("permit(principal == ?principal, action, resource);", null);

        assertDoesNotThrow(() -> {
            assertTrue(Policy.validateTemplateLinkedPolicy(p, principal1, resource1));
            assertTrue(Policy.validateTemplateLinkedPolicy(p2, null, resource2));
            assertTrue(Policy.validateTemplateLinkedPolicy(p3, principal1, null));
        });
    }
    @Test
    public void validateTemplateLinkedPolicyFailsWhenExpected() {
        EntityUID principal = EntityUID.parse("Library::User::\"Victor\"").get();
        EntityUID resource = EntityUID.parse("Library::Book::\"Thinking Fast and Slow\"").get();

        Policy p1 = new Policy("permit(principal, action, resource in ?resource);", null);
        Policy p2 = new Policy("permit(principal == ?principal, action, resource);", null);

        // fails if we fill both slots or the wrong slot in a policy with one slot
        assertThrows(InternalException.class, () -> {
            Policy.validateTemplateLinkedPolicy(p1, principal, null);
        });
        assertThrows(InternalException.class, () -> {
            Policy.validateTemplateLinkedPolicy(p1, principal, resource);
        });

        assertThrows(InternalException.class, () -> {
            Policy.validateTemplateLinkedPolicy(p2, null, resource);
        });
        assertThrows(InternalException.class, () -> {
            Policy.validateTemplateLinkedPolicy(p2, principal, resource);
        });
    }
}
