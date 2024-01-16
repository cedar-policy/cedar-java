package com.cedarpolicy;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;

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
    public void concrete() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = PartialAuthorizationRequest.builder().principal(alice).action(view).resource(alice).context(new HashMap<>()).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource);", "p0"));
        var slice = new BasicSlice(policies, new HashSet<>());
        assertDoesNotThrow(() -> {
            var response = auth.isAuthorizedPartial(q, slice);
            assertTrue(response.reachedDecision());
            assertEquals(1, response.getDiagnostics().getReasons().size());
            assertEquals("p0", response.getDiagnostics().getReasons().iterator().next());
            assertInstanceOf(PartialAuthorizationResponse.ConcretePartialAuthorizationResponse.class, response);
            var concrete = (PartialAuthorizationResponse.ConcretePartialAuthorizationResponse) response;
            assertEquals(AuthorizationResponse.Decision.Allow, concrete.getDecision());
            assertTrue(concrete.isAllowed());
        }, "Should not throw AuthException");

    }

    @Test
    public void residual() {
        var auth = new BasicAuthorizationEngine();
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var q = PartialAuthorizationRequest.builder().action(view).resource(alice).context(new HashMap<>()).build();
        var policies = new HashSet<Policy>();
        policies.add(new Policy("permit(principal == User::\"alice\",action,resource);", "p0"));
        var slice = new BasicSlice(policies, new HashSet<>());
        assertDoesNotThrow(() -> {
            var response = auth.isAuthorizedPartial(q, slice);
            assertFalse(response.reachedDecision());
            assertInstanceOf(PartialAuthorizationResponse.ResidualPartialAuthorizationResponse.class, response);
            var residual = (PartialAuthorizationResponse.ResidualPartialAuthorizationResponse) response;
            assertEquals(1, residual.getResiduals().size());
            assertEquals("p0", residual.getResiduals().iterator().next().policyID);
        }, "Should not throw AuthException");

    }
}
