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

import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationResponse;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationResponse.SuccessOrFailure;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.Effect;
import com.cedarpolicy.model.policy.Policy;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static com.cedarpolicy.CedarJson.objectReader;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Tests for {@link TypeAwarePartialAuthorizationResponse} and its success response. */
public class TypeAwarePartialAuthorizationResponseTests {

    /**
     * A concrete `allow` still carries the permit that decided it. Cedar reaches a concrete `Allow` only when some
     * permit reduced to concretely true, and `TpeResponse::policies` — which fills `residuals` — includes concretely
     * true residuals, so `residuals` cannot be empty here. It is `nontrivialResiduals` that is empty, because the
     * deciding permit is trivial. Keeping the fixture faithful matters: an empty `residuals` alongside a concrete
     * decision would suggest the handler may skip residuals once the decision is known, and reauthorizing against that
     * empty set would Deny what the full engine Allows.
     */
    @Test
    public void testTPEConcreteResponse() {
        final String trivialPermit = "permit(principal, action, resource) when { true };";
        String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": \"allow\", \"residuals\": {\"trivial\": \""
                + trivialPermit + "\" }, \"nontrivialResiduals\": [] }, \"warnings\": [] }";
        try {
            TypeAwarePartialAuthorizationResponse r =
                    objectReader().forType(TypeAwarePartialAuthorizationResponse.class).readValue(src);
            assertEquals(SuccessOrFailure.Success, r.getType());
            var success = r.getSuccess().orElseThrow();
            assertEquals(Decision.Allow, success.getDecision());
            assertEquals(Set.of("trivial"), ids(success.getResiduals()));
            assertTrue(success.getNontrivialResiduals().isEmpty());
            assertTrue(r.getWarnings().isEmpty());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testTPEResidualResponse() {
        final String policy = "permit(principal, action, resource) when { principal.department == \\\"eng\\\" };";
        final String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": {\"p0\": \"" + policy + "\" } }, \"warnings\": [] }";
        try {
            TypeAwarePartialAuthorizationResponse r =
                    objectReader().forType(TypeAwarePartialAuthorizationResponse.class).readValue(src);
            var success = r.getSuccess().orElseThrow();
            assertNull(success.getDecision());
            var residuals = success.getResiduals();
            assertEquals(1, residuals.size());
            Policy residual = residuals.iterator().next();
            assertEquals("p0", residual.getID());
            // The residual carries the Cedar source the FFI sent, verbatim.
            assertEquals("permit(principal, action, resource) when { principal.department == \"eng\" };",
                    residual.getSource());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testTPEFailureResponse() {
        String src = "{ \"type\": \"failure\", \"errors\": [{ \"message\": \"failed to type check the request\" }], \"warnings\": [\"policy p0 is impossible\"] }";
        try {
            TypeAwarePartialAuthorizationResponse r =
                    objectReader().forType(TypeAwarePartialAuthorizationResponse.class).readValue(src);
            assertEquals(SuccessOrFailure.Failure, r.getType());
            assertTrue(r.getSuccess().isEmpty());
            var errors = r.getErrors().orElseThrow();
            assertEquals(1, errors.size());
            assertEquals("failed to type check the request", errors.get(0).message);
            assertEquals(1, r.getWarnings().size());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testTPEResponseOmittedErrorsBindsToEmptyOptional() {
        String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": {} }, \"warnings\": [] }";
        try {
            TypeAwarePartialAuthorizationResponse r =
                    objectReader().forType(TypeAwarePartialAuthorizationResponse.class).readValue(src);
            assertTrue(r.getErrors().isEmpty());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testTPEResponseToleratesUnknownFields() {
        String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"reason\": [], \"errored\": [], \"residuals\": {}, \"mayBePermits\": [\"p0\"] }, \"warnings\": [], \"aFieldFromTheFuture\": 7 }";
        TypeAwarePartialAuthorizationResponse r = assertDoesNotThrow(
                () -> objectReader().forType(TypeAwarePartialAuthorizationResponse.class).readValue(src));
        assertTrue(r.getSuccess().isPresent());
    }

    /**
     * Pins the meaning of the three residual accessors against Cedar's main line, which CedarJava's main line builds
     * against. `getResiduals` is every residual; `getNontrivialResiduals` and `getTrivialResiduals`
     * partition it for inspection.
     *
     * <p>No accessor here is named after a Cedar method whose meaning changed between 4.11 and 4.12, so these
     * assertions hold in both versions. The version delta lives in the producer, which has to derive
     * `nontrivialResiduals` from whichever Cedar accessor returns the non-trivial subset.
     */
    @Test
    public void testResidualAccessorsSplitTrivialFromNontrivial() throws InternalException, JsonProcessingException {
        // p0 reduced to a concrete true; p1 still has a condition. The ids are deliberately neutral:
        // the split comes from the `nontrivialResiduals` field below, not from the policy bodies.
        final String reducedToTrue = "permit(principal, action, resource) when { true };";
        final String stillConditional = "@owner(\\\"team-a\\\") @audit(\\\"\\\") "
                + "forbid(principal, action, resource) when { principal.department == \\\"eng\\\" };";
        final String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": {\"p0\": \""
                + reducedToTrue + "\", \"p1\": \"" + stillConditional + "\" }, \"nontrivialResiduals\": [\"p1\"] },"
                + " \"warnings\": [] }";
        var success = objectReader().forType(TypeAwarePartialAuthorizationResponse.class)
                .<TypeAwarePartialAuthorizationResponse>readValue(src).getSuccess().orElseThrow();

        assertEquals(Set.of("p0", "p1"), ids(success.getResiduals()));
        assertEquals(Set.of("p1"), ids(success.getNontrivialResiduals()));
        assertEquals(Set.of("p0"), ids(success.getTrivialResiduals()));

        // The residual is a usable Policy, which is the point of returning these rather than raw JSON.
        Policy p1 = success.getNontrivialResiduals().iterator().next();
        assertEquals("p1", p1.getID());
        assertEquals(Effect.FORBID, p1.effect());
        // Annotations survive EST -> source -> Policy. An annotation with no value reads as "".
        assertEquals(Map.of("owner", "team-a", "audit", ""), p1.getAnnotations());
        assertEquals("team-a", p1.getAnnotation("owner"));
        assertNull(p1.getAnnotation("nosuch"));
        // The two partitions are disjoint and together account for every residual.
        var nontrivial = ids(success.getNontrivialResiduals());
        var trivial = ids(success.getTrivialResiduals());
        assertTrue(Collections.disjoint(nontrivial, trivial));
        var union = new HashSet<String>(nontrivial);
        union.addAll(trivial);
        assertEquals(ids(success.getResiduals()), union);
    }

    /** An omitted `nontrivialResiduals` means every residual is trivial, not that there are none. */
    @Test
    public void testOmittedNontrivialResidualsMeansAllTrivial()
            throws InternalException, JsonProcessingException {
        final String permitAll = "permit(principal, action, resource);";
        final String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": {\"myPermit\": \""
                + permitAll + "\" } }, \"warnings\": [] }";
        var success = objectReader().forType(TypeAwarePartialAuthorizationResponse.class)
                .<TypeAwarePartialAuthorizationResponse>readValue(src).getSuccess().orElseThrow();
        assertTrue(success.getNontrivialResiduals().isEmpty());
        assertEquals(Set.of("myPermit"), ids(success.getTrivialResiduals()));
        assertEquals(Set.of("myPermit"), ids(success.getResiduals()));
    }

    @Test
    public void testTPESuccessResponseReadsEveryWireField() throws InternalException, JsonProcessingException {
        final String permitAll = "permit(principal, action, resource);";
        final String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": {\"myPermit\": \""
                + permitAll + "\" }, \"nontrivialResiduals\": [\"myPermit\"] }, \"warnings\": [] }";
        var success = objectReader().forType(TypeAwarePartialAuthorizationResponse.class)
                .<TypeAwarePartialAuthorizationResponse>readValue(src)
                .getSuccess()
                .orElseThrow();

        assertNull(success.getDecision());
        assertEquals(Set.of("myPermit"), success.getNontrivialResidualIds());
        assertEquals(Set.of("myPermit"), ids(success.getResiduals()));
        assertEquals(Set.of("myPermit"), ids(success.getNontrivialResiduals()));
        assertTrue(success.getTrivialResiduals().isEmpty());
    }

    /**
     * A response deserialized directly, rather than obtained from an engine, has nothing to reauthorize against — so
     * the attempt fails with an explanation rather than a NullPointerException.
     */
    @Test
    public void testDeserializedResponseCannotBeReauthorized() throws JsonProcessingException {
        final String src = "{ \"type\": \"residuals\", \"response\": { \"decision\": null, \"residuals\": "
                + "{\"p0\": \"permit(principal, action, resource);\" } }, \"warnings\": [] }";
        var success = objectReader().forType(TypeAwarePartialAuthorizationResponse.class)
                .<TypeAwarePartialAuthorizationResponse>readValue(src).getSuccess().orElseThrow();

        var e = assertThrows(AuthException.class, () -> success.reauthorize(null, null));
        assertTrue(e.getMessage().contains("carries nothing to reauthorize"), "unexpected message: " + e.getMessage());
    }

    /** Policy carries its own id, so the accessors return sets rather than maps. */
    private static Set<String> ids(Set<Policy> policies) {
        return policies.stream().map(Policy::getID).collect(Collectors.toSet());
    }
}
