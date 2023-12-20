package com.cedarpolicy;

import java.util.Optional;
import java.util.HashSet;
import java.util.HashMap;

import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import org.junit.jupiter.api.Test;
import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.model.slice.Slice;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.EntityTypeName;

import javax.swing.text.html.Option;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void partial() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = PartialAuthorizationRequest.builder().action(view).resource(alice).context(new HashMap<>()).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource);", "p0"));
        var slice = new BasicSlice(policies, new HashSet<>());
        assertDoesNotThrow(() -> {
            var response = auth.isAuthorizedPartial(q, slice);
            assertEquals(AuthorizationResponse.Decision.NoDecision, response.getDecision());
            assertEquals(1, response.getResidual().size());
        }, "Should not throw AuthException");

    }
}
