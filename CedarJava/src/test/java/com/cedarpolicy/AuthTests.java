package com.cedarpolicy;

import java.util.HashSet;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.EntityTypeName;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthTests {

    @Test
    public void simple() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = new AuthorizationRequest(alice, view, alice, new HashMap<>());
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal,action,resource);", "p0"));
        var slice = new BasicSlice(policies, new HashSet<>());
        assertDoesNotThrow(() -> {
            var response = auth.isAuthorized(q, slice);
            assertTrue(response.isAllowed());
        }, "Should not throw AuthException");

    }
}
