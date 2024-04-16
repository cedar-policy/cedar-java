package com.cedarpolicy;

import java.util.HashMap;
import java.util.HashSet;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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
            assertNotNull(response.success);
            assertTrue(response.success.isAllowed());
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
        assertDoesNotThrow(
            assumePartialEvaluation(
                () -> {
                    var response = auth.isAuthorizedPartial(q, slice);
                    assertEquals(Decision.Allow, response.getDecision());
                    assertEquals(response.getMustBeDetermining().iterator().next(), "p0");
                    assertTrue(response.getNontrivialResiduals().isEmpty());
                }
            ), "Should not throw AuthException");
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
        assertDoesNotThrow(
            assumePartialEvaluation(
                 () -> {
                     var response = auth.isAuthorizedPartial(q, slice);
                     assertTrue(response.getDecision() == null);
                     assertEquals("p0", response.getResiduals().entrySet().iterator().next().getKey());
                }
            ), "Should not throw AuthException");
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
