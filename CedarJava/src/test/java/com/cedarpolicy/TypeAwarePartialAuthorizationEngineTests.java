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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse.SuccessOrFailure;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationRequest;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationResponse;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.entity.PartialEntities;
import com.cedarpolicy.model.entity.PartialEntity;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.LinkValue;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.policy.TemplateLink;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Type-aware partial evaluation through {@link BasicAuthorizationEngine}, end to end over the JNI bridge.
 *
 * <p>Separate from {@link AuthTests} so that a failure loading the TPE schema fixture cannot take the plain
 * authorization tests down with it through a static initializer.
 */
public class TypeAwarePartialAuthorizationEngineTests {

    private static final Schema TPE_SCHEMA = TestUtil.loadSchemaResource("/tpe_schema.json");
    private static final EntityUID TPE_VIEW = new EntityUID(EntityTypeName.parse("Action").get(), "view");
    private static final EntityUID TPE_DOOR = new EntityUID(EntityTypeName.parse("Photo").get(), "door");
    private static final EntityUID TPE_ALICE = new EntityUID(EntityTypeName.parse("User").get(), "alice");

    /** Decides only once {@code principal.department} is known, so it residualizes while the principal is not. */
    private static final String TPE_ENG_POLICY = "permit(principal, action == Action::\"view\", resource)\n"
            + "when { principal has department && principal.department == \"eng\" };";

    /** Always errors at evaluation time: `i64::MAX + 1` overflows. Validates against any schema. */
    private static final String TPE_OVERFLOW_POLICY = "permit(principal, action == Action::\"view\", resource)\n"
            + "when { 9223372036854775807 + 1 > 0 };";

    private static PolicySet tpePolicies(String src, String id) {
        return new PolicySet(Set.of(new Policy(src, id)));
    }

    @Test
    public void tpeConcreteRequestReachesADecision() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var response = auth.isAuthorizedTypeAwarePartial(q,
                    tpePolicies("permit(principal, action, resource);", "p0"), PartialEntities.empty());
            var success = response.getSuccess().orElseThrow();
            assertEquals(Decision.Allow, success.getDecision());
            // A concrete Allow still carries the permit that decided it, and that permit is trivial.
            assertEquals(Set.of("p0"), TestUtil.ids(success.getResiduals()));
            assertTrue(success.getNontrivialResiduals().isEmpty());
        });
    }

    @Test
    public void tpeUnknownPrincipalIdYieldsAResidual() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(EntityTypeName.parse("User").get()).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var response = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"),
                    PartialEntities.empty());
            var success = response.getSuccess().orElseThrow();
            assertNull(success.getDecision(), "no decision is reachable while the principal is unknown");
            assertEquals(Set.of("p0"), TestUtil.ids(success.getResiduals()));
            assertEquals(Set.of("p0"), TestUtil.ids(success.getNontrivialResiduals()));
            // The principal id is unknown, so `principal` survives as a variable.
            assertEquals("permit(principal, action, resource) when "
                    + "{ (principal has department) && ((principal.department) == \"eng\") };",
                    success.getResiduals().iterator().next().getSource());
        });
    }

    /**
     * The residual's <em>content</em>, not just its id. Cedar rewrites a residual thoroughly: the satisfied scope
     * constraints are folded away, and every reference to {@code principal} is replaced by the concrete entity the
     * request named.
     *
     * <p>That substitution is the sharpest reason a residual must never be authorized against a different request. The
     * text below does not merely lose {@code principal == User::"alice"} — it hard-codes {@code User::"alice"} into the
     * condition, so evaluating it for another principal still reads <em>alice's</em> attributes and can grant that
     * principal access alice alone had.
     */
    @Test
    public void tpeResidualTextShowsTheFoldedCondition() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            // Constrained to alice, and alice is who we asked about — so the constraint is known true.
            var policies = tpePolicies("@owner(\"team-a\")\n"
                    + "permit(principal == User::\"alice\", action == Action::\"view\", resource)\n"
                    + "when { principal has department && principal.department == \"eng\" };", "p0");
            var entities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()), Optional.of(Map.of()),
                            TPE_SCHEMA)),
                    TPE_SCHEMA);

            var success = auth.isAuthorizedTypeAwarePartial(q, policies, entities).getSuccess().orElseThrow();
            var residual = success.getResiduals().iterator().next();

            assertEquals("p0", residual.getID());
            assertEquals("@owner(\"team-a\")\npermit(principal, action, resource) when "
                    + "{ (User::\"alice\" has department) && ((User::\"alice\".department) == \"eng\") };",
                    residual.getSource());
            // The annotation survives, and the residual is still a usable Policy because nothing errored.
            assertEquals(Map.of("owner", "team-a"), residual.getAnnotations());
            assertDoesNotThrow(residual::effect);
            assertTrue(success.getErroredResidualIds().isEmpty());
        });
    }

    @Test
    public void tpeUnknownContextYieldsAResidual() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            // No context(...) call at all, so the whole context is unknown.
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR).schema(TPE_SCHEMA).build();
            var response = auth.isAuthorizedTypeAwarePartial(q,
                    tpePolicies("permit(principal, action == Action::\"view\", resource)\n"
                            + "when { context has authenticated && context.authenticated };", "p0"),
                    PartialEntities.empty());
            var success = response.getSuccess().orElseThrow();
            assertNull(success.getDecision());
            assertEquals(Set.of("p0"), TestUtil.ids(success.getNontrivialResiduals()));
        });
    }

    @Test
    public void tpeConcreteEntitiesAreLifted() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var alice = new Entity(TPE_ALICE, Map.of("department", new PrimString("eng")), Set.of(), Map.of());

            var success = auth
                    .isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"), new Entities(Set.of(alice)))
                    .getSuccess().orElseThrow();
            // The policy reads an attribute, so a decision is only reachable if the lift carried alice's attributes
            // over rather than marking them unknown.
            assertEquals(Decision.Allow, success.getDecision());
            assertTrue(success.getNontrivialResiduals().isEmpty());
        });
    }

    @Test
    public void tpeUnknownAttrsYieldsAResidual() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            // alice is known to exist, but her attributes are not known, so the condition cannot be resolved.
            var entities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()), Optional.of(Map.of()),
                            TPE_SCHEMA)),
                    TPE_SCHEMA);
            var response = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"), entities);
            var success = response.getSuccess().orElseThrow();
            assertNull(success.getDecision());
            assertEquals(Set.of("p0"), TestUtil.ids(success.getNontrivialResiduals()));
            // The principal id is known here, unlike the test above, so `principal` is substituted with the
            // entity — which is why a residual must not be evaluated for a different principal.
            assertEquals("permit(principal, action, resource) when "
                    + "{ (User::\"alice\" has department) && ((User::\"alice\".department) == \"eng\") };",
                    success.getResiduals().iterator().next().getSource());
        });
    }

    /**
     * The reauthorization path, which runs natively: the residuals never cross back. The decision must track the data
     * filled in — {@code "eng"} satisfies the condition and {@code "sales"} does not — and must agree with authorizing
     * the original policies directly. A single policy, so neither outcome can be masked by another permit.
     */
    @Test
    public void tpeReauthorizeCompletesAuthorization() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var policies = tpePolicies(TPE_ENG_POLICY, "conditional");
            var partialRequest = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var partialEntities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()), Optional.of(Map.of()),
                            TPE_SCHEMA)),
                    TPE_SCHEMA);

            var success = auth.isAuthorizedTypeAwarePartial(partialRequest, policies, partialEntities)
                    .getSuccess().orElseThrow();
            assertNull(success.getDecision(), "the attributes are unknown, so nothing is decided yet");

            for (var expected : Map.of("eng", Decision.Allow, "sales", Decision.Deny).entrySet()) {
                var department = expected.getKey();
                var alice = new Entity(TPE_ALICE, Map.of("department", new PrimString(department)), Set.of(), Map.of());
                var concreteRequest = new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR,
                        new HashMap<String, Value>());
                var entities = new Entities(Set.of(alice));

                var viaReauthorize = success.reauthorize(concreteRequest, entities)
                        .success.orElseThrow().getDecision();
                var viaOriginal = auth.isAuthorized(concreteRequest, policies, entities)
                        .success.orElseThrow().getDecision();

                assertEquals(expected.getValue(), viaReauthorize, "department " + department);
                assertEquals(viaOriginal, viaReauthorize,
                        "reauthorizing must agree with authorizing the original policies, department " + department);
            }
        });
    }

    /**
     * Reauthorizing a <em>different</em> request is rejected rather than answered. Authorizing the residuals directly
     * cannot detect this and would grant access the original policies denied.
     */
    @Test
    public void tpeReauthorizeRejectsAMismatchedRequest() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var bob = new EntityUID(EntityTypeName.parse("User").get(), "bob");
            var policies = tpePolicies("permit(principal == User::\"alice\", action == Action::\"view\", resource);",
                    "alice_only");
            var partialRequest = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .schema(TPE_SCHEMA).build();

            var success = auth.isAuthorizedTypeAwarePartial(partialRequest, policies, PartialEntities.empty())
                    .getSuccess().orElseThrow();
            var response = success.reauthorize(new AuthorizationRequest(bob, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    new Entities(Set.of()));

            assertEquals(SuccessOrFailure.Failure, response.type);
            assertTrue(response.success.isEmpty());
            var message = response.errors.orElseThrow().get(0).message;
            assertTrue(message.contains("alice") && message.contains("bob"),
                    "expected the error to name both principals but was: " + message);
        });
    }

    /**
     * A residual whose condition errors is reported by id, and carries Cedar's own rendering rather than being dropped
     * or rewritten. Its {@code effect()} throws because Cedar cannot parse the marker back.
     */
    @Test
    public void tpeErroredResidualIsReportedAndSourceOnly() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var alice = new Entity(TPE_ALICE, Map.of(), Set.of(), Map.of());
            var success = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_OVERFLOW_POLICY, "boom"),
                    new PartialEntities(new Entities(Set.of(alice)), TPE_SCHEMA))
                    .getSuccess().orElseThrow();

            assertEquals(Set.of("boom"), success.getErroredResidualIds());
            assertEquals(Set.of("boom"), TestUtil.ids(success.getErroredResiduals()));
            // Cedar still reaches a decision: an erroring permit grants nothing, so Deny. And it classifies the
            // residual as trivial — it reached a concrete outcome, that outcome just happens to be an error.
            assertEquals(Decision.Deny, success.getDecision());
            assertEquals(Set.of("boom"), TestUtil.ids(success.getTrivialResiduals()));
            assertTrue(success.getNontrivialResiduals().isEmpty());
            var errored = success.getErroredResiduals().iterator().next();
            assertTrue(errored.getSource().contains("error()"),
                    "the residual should carry Cedar's rendering but was: " + errored.getSource());
            assertThrows(InternalException.class, errored::effect,
                    "Cedar cannot parse the error marker back, so effect() must fail rather than mislead");
        });
    }

    /**
     * The counterpart to the errored-residual case: a residual with no error node is a fully usable {@link Policy}.
     * {@link Policy#toJson()} re-parses the source natively, so this also shows the residual Cedar rendered is valid
     * Cedar — and the EST round-trips back to the same source, which is exactly what an errored residual cannot do.
     */
    @Test
    public void tpeCleanResidualConvertsToJsonAndBack() throws Exception {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(EntityTypeName.parse("User").get()).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var success = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"),
                    PartialEntities.empty()).getSuccess().orElseThrow();

            assertTrue(success.getErroredResidualIds().isEmpty(), "this residual must not contain an error node");
            var residual = success.getResiduals().iterator().next();

            var json = assertDoesNotThrow(residual::toJson);
            assertTrue(json.contains("\"effect\"") && json.contains("\"permit\""),
                    "expected an EST policy but got: " + json);
            // Round trip: the EST parses back to a policy with the same source Cedar rendered.
            assertEquals(residual.getSource(), Policy.fromJson("p0", json).getSource());
            // The other re-parsing accessors work too, unlike on an errored residual.
            assertDoesNotThrow(residual::effect);
            assertDoesNotThrow(residual::getAnnotations);
        });
    }

    /**
     * A residual containing a known-error subexpression cannot be serialized, but reauthorization never serializes it —
     * it evaluates the residual natively, so the erroring policy is reported in the diagnostics and the decision still
     * comes back.
     */
    @Test
    public void tpeReauthorizeWorksWhenAResidualErrors() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var policies = tpePolicies(TPE_OVERFLOW_POLICY, "boom");
            var partialRequest = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var alice = new Entity(TPE_ALICE, Map.of(), Set.of(), Map.of());
            var entities = new Entities(Set.of(alice));

            // The residual really does error, otherwise this test proves nothing.
            var success = auth.isAuthorizedTypeAwarePartial(partialRequest, policies,
                    new PartialEntities(entities, TPE_SCHEMA)).getSuccess().orElseThrow();
            assertEquals(Set.of("boom"), success.getErroredResidualIds());

            var response = success.reauthorize(new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    entities);

            // The permit errors, so it grants nothing and the decision defaults to Deny — but a decision is reached,
            // which is the point: serializing this residual could not have produced one.
            var reauthorized = response.success.orElseThrow();
            assertEquals(Decision.Deny, reauthorized.getDecision());
            assertEquals(1, reauthorized.getErrors().size(),
                    "the erroring policy must be reported in the diagnostics, not silently dropped");
        });
    }

    /**
     * {@code getErroredResiduals()} must filter, not return everything. One residual errors, one does not.
     */
    @Test
    public void tpeErroredResidualsIsAFilterNotEverything() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(EntityTypeName.parse("User").get()).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = new PolicySet(Set.of(
                    new Policy(TPE_OVERFLOW_POLICY, "boom"),
                    new Policy(TPE_ENG_POLICY, "clean")));

            var success = auth.isAuthorizedTypeAwarePartial(q, policies, PartialEntities.empty())
                    .getSuccess().orElseThrow();

            assertEquals(Set.of("boom", "clean"), TestUtil.ids(success.getResiduals()));
            assertEquals(Set.of("boom"), success.getErroredResidualIds());
            assertEquals(Set.of("boom"), TestUtil.ids(success.getErroredResiduals()));
            // The errored set cuts across the trivial/non-trivial split rather than being a third partition.
            assertEquals(Set.of("boom"), TestUtil.ids(success.getTrivialResiduals()));
            assertEquals(Set.of("clean"), TestUtil.ids(success.getNontrivialResiduals()));
            // The clean one is a usable Policy; the errored one is source-only.
            var clean = success.getResiduals().stream().filter(p -> p.getID().equals("clean")).findFirst()
                    .orElseThrow();
            assertDoesNotThrow(clean::effect);
        });
    }

    @Test
    public void tpeForbidReachesADeny() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = new PolicySet(Set.of(
                    new Policy("permit(principal, action, resource);", "p0"),
                    new Policy("forbid(principal, action, resource);", "f0")));

            var success = auth.isAuthorizedTypeAwarePartial(q, policies, PartialEntities.empty())
                    .getSuccess().orElseThrow();
            assertEquals(Decision.Deny, success.getDecision());
        });
    }

    /**
     * Type-aware partial evaluation validates the policy set against the schema, which plain authorization does not, so
     * a policy that parses but does not type check comes back as a failure response rather than as residuals.
     */
    @Test
    public void tpePolicyFailingSchemaValidationIsAFailure() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var response = auth.isAuthorizedTypeAwarePartial(q,
                    tpePolicies("permit(principal, action, resource) when { principal.nosuchattr };", "p0"),
                    PartialEntities.empty());

            assertEquals(TypeAwarePartialAuthorizationResponse.SuccessOrFailure.Failure, response.getType());
            assertTrue(response.getSuccess().isEmpty());
            assertEquals(List.of("policy failed to validate against the schema"),
                    response.getErrors().orElseThrow().stream().map(e -> e.message)
                            .collect(Collectors.toList()));
        });
    }

    /**
     * A template-linked policy residualizes as a static policy keyed by the link id, so a caller inspecting residuals
     * sees the link, not the template.
     */
    @Test
    public void tpeTemplateLinkResidualizesUnderItsLinkId() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var policies = new PolicySet(Set.of(),
                    Set.of(new Policy("permit(principal == ?principal, action == Action::\"view\", resource)\n"
                            + "when { principal has department && principal.department == \"eng\" };", "tmpl")),
                    List.of(new TemplateLink("tmpl", "linked",
                            List.of(new LinkValue("?principal", TPE_ALICE)))));

            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(EntityTypeName.parse("User").get()).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var success = auth.isAuthorizedTypeAwarePartial(q, policies, PartialEntities.empty())
                    .getSuccess().orElseThrow();

            assertEquals(Set.of("linked"), TestUtil.ids(success.getResiduals()));
            assertTrue(success.getErroredResidualIds().isEmpty());
        });
    }
    /**
     * The three-state distinction the whole partial-entity model rests on, asserted against Cedar's own behaviour: a
     * field <em>omitted</em> is unknown and leaves a residual, while the same field present-but-empty is <em>known</em>
     * to be empty and folds the condition to a constant. Getting these confused would silently turn an undecided
     * request into a Deny.
     */
    @Test
    public void tpeOmittedFieldIsUnknownWhileEmptyFieldIsKnown() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = tpePolicies(
                    "permit(principal, action == Action::\"view\", resource) when { principal has department };", "p0");

            // attrs omitted -> unknown
            var unknownAttrs = auth.isAuthorizedTypeAwarePartial(q, policies, new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA)).getSuccess().orElseThrow();
            assertNull(unknownAttrs.getDecision());
            assertEquals(Set.of("p0"), TestUtil.ids(unknownAttrs.getNontrivialResiduals()));
            assertEquals("permit(principal, action, resource) when { User::\"alice\" has department };",
                    unknownAttrs.getResiduals().iterator().next().getSource());

            // attrs present but empty -> known absent, so `has` folds to false and Cedar decides
            var knownEmptyAttrs = auth.isAuthorizedTypeAwarePartial(q, policies, new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.of(Map.of()), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA)).getSuccess().orElseThrow();
            assertEquals(Decision.Deny, knownEmptyAttrs.getDecision());
            assertTrue(knownEmptyAttrs.getNontrivialResiduals().isEmpty());
            assertEquals("permit(principal, action, resource) when { false };",
                    knownEmptyAttrs.getResiduals().iterator().next().getSource());
        });
    }

    /** Tags are a separate namespace from attributes and follow the same omitted-versus-empty rule. */
    @Test
    public void tpeOmittedTagsAreUnknownWhileEmptyTagsAreKnown() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = tpePolicies("permit(principal, action == Action::\"view\", resource)\n"
                    + "when { principal.hasTag(\"clearance\") && principal.getTag(\"clearance\") == \"high\" };", "p0");

            var unknownTags = auth.isAuthorizedTypeAwarePartial(q, policies, new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.of(Map.of()), Optional.of(Set.of()),
                            Optional.empty(), TPE_SCHEMA)),
                    TPE_SCHEMA)).getSuccess().orElseThrow();
            assertNull(unknownTags.getDecision());
            assertEquals(Set.of("p0"), TestUtil.ids(unknownTags.getNontrivialResiduals()));

            var knownEmptyTags = auth.isAuthorizedTypeAwarePartial(q, policies, new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.of(Map.of()), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA)).getSuccess().orElseThrow();
            assertEquals(Decision.Deny, knownEmptyTags.getDecision());
            assertEquals("permit(principal, action, resource) when { false };",
                    knownEmptyTags.getResiduals().iterator().next().getSource());
        });
    }

    /**
     * An entity absent from the store is treated as one whose every field is unknown — not as an error. Worth pinning
     * before anyone "hardens" the serializer into rejecting entities the request refers to but does not supply.
     */
    @Test
    public void tpeAbsentEntityBehavesLikeAFullyUnknownOne() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = tpePolicies(
                    "permit(principal, action == Action::\"view\", resource) when { principal has department };", "p0");

            var absent = auth.isAuthorizedTypeAwarePartial(q, policies, PartialEntities.empty())
                    .getSuccess().orElseThrow();
            var unknown = auth.isAuthorizedTypeAwarePartial(q, policies, new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA)).getSuccess().orElseThrow();

            assertNull(absent.getDecision());
            assertEquals(unknown.getResiduals().iterator().next().getSource(),
                    absent.getResiduals().iterator().next().getSource());
        });
    }

    /**
     * An error nested inside a still-live condition is reported as errored <em>and</em> non-trivial. This is the case
     * Cedar's own {@code error_permits()} bucket misses — it lists only policies that reduced entirely to an error —
     * and the reason the errored ids are derived from the PST instead.
     */
    @Test
    public void tpeNestedErrorIsBothNontrivialAndErrored() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            // The left disjunct stays unknown, so the `||` cannot short-circuit and the error survives nested.
            var policies = tpePolicies("permit(principal, action == Action::\"view\", resource)\n"
                    + "when { (principal has department) || (9223372036854775807 + 1 > 0) };", "p0");
            var entities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA);

            var success = auth.isAuthorizedTypeAwarePartial(q, policies, entities).getSuccess().orElseThrow();

            assertNull(success.getDecision());
            assertEquals(Set.of("p0"), TestUtil.ids(success.getNontrivialResiduals()));
            assertEquals(Set.of("p0"), success.getErroredResidualIds(),
                    "a nested error must still be reported, which error_permits() alone would not do");
            assertEquals("permit(principal, action, resource) when "
                    + "{ (User::\"alice\" has department) || (error()) };",
                    success.getResiduals().iterator().next().getSource());
        });
    }

    /**
     * Every dimension Cedar checks when completing a partial request, one deliberate mismatch each. This is the safety
     * property {@code reauthorize} exists for: a residual has the original request's values folded in, so completing a
     * <em>different</em> request against it could grant access the original policies denied.
     *
     * <p>Expected messages are Cedar's own, taken from running the same completions through Cedar Rust directly. Note
     * entities are checked before the request, so each completion here varies exactly one thing — vary two and you get
     * whichever check runs first.
     */
    @Test
    public void tpeReauthorizeRejectsEveryKindOfMismatch() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var bob = new EntityUID(EntityTypeName.parse("User").get(), "bob");
            var window = new EntityUID(EntityTypeName.parse("Photo").get(), "window");
            var edit = new EntityUID(EntityTypeName.parse("Action").get(), "edit");
            var group = new EntityUID(EntityTypeName.parse("Group").get(), "eng");

            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = tpePolicies(TPE_ENG_POLICY, "p0");
            // alice is known to exist with known parents and tags; only her attributes are unknown.
            var partialEntities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()),
                            Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA);
            var success = auth.isAuthorizedTypeAwarePartial(q, policies, partialEntities)
                    .getSuccess().orElseThrow();

            var aliceEng = new Entity(TPE_ALICE, Map.of("department", new PrimString("eng")), Set.of(), Map.of());

            // A matching completion is accepted, so the rejections below are not the check refusing everything.
            assertEquals(Decision.Allow, success.reauthorize(
                    new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    new Entities(Set.of(aliceEng))).success.orElseThrow().getDecision());

            record Mismatch(String label, AuthorizationRequest request, Entities entities, String expected) { }
            var mismatches = List.of(
                    new Mismatch("principal id",
                            new AuthorizationRequest(bob, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                            new Entities(Set.of(aliceEng,
                                    new Entity(bob, Map.of("department", new PrimString("eng")), Set.of(), Map.of()))),
                            "Principal eid `alice` and `bob` do not match"),
                    new Mismatch("resource id",
                            new AuthorizationRequest(TPE_ALICE, TPE_VIEW, window, new HashMap<String, Value>()),
                            new Entities(Set.of(aliceEng)),
                            "Resource eid `door` and `window` do not match"),
                    new Mismatch("action",
                            new AuthorizationRequest(TPE_ALICE, edit, TPE_DOOR, new HashMap<String, Value>()),
                            new Entities(Set.of(aliceEng)),
                            "Actions `Action::\"view\"` and `Action::\"edit\"` do not match"),
                    new Mismatch("context",
                            new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR,
                                    new HashMap<>(Map.of("authenticated", new PrimBool(true)))),
                            new Entities(Set.of(aliceEng)),
                            "Contexts are inconsistent"),
                    new Mismatch("entity missing from the concrete store",
                            new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                            new Entities(Set.of()),
                            "Concrete entities does not include `User::\"alice\"`"),
                    new Mismatch("parents",
                            new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                            new Entities(Set.of(new Entity(TPE_ALICE,
                                    Map.of("department", new PrimString("eng")), Set.of(group), Map.of()))),
                            "Entity `User::\"alice\"`'s ancestors do not match"),
                    new Mismatch("tags",
                            new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                            new Entities(Set.of(new Entity(TPE_ALICE, Map.of("department", new PrimString("eng")),
                                    Set.of(), Map.of("clearance", new PrimString("high"))))),
                            "Entity `User::\"alice\"`'s tags do not match"));

            for (var m : mismatches) {
                var response = success.reauthorize(m.request(), m.entities());
                assertEquals(SuccessOrFailure.Failure, response.type,
                        "completing a different " + m.label() + " must be rejected, not answered");
                assertTrue(response.success.isEmpty(), m.label());
                var message = response.errors.orElseThrow().get(0).message;
                assertTrue(message.contains(m.expected()),
                        m.label() + ": expected a message containing\n  " + m.expected() + "\nbut got\n  " + message);
            }
        });
    }

    /**
     * A known attribute value must be unchanged by the completion. The mismatch test above leaves attrs unknown, so
     * this is the dimension it cannot reach — Cedar compares the attribute map as a whole and rejects any difference.
     */
    @Test
    public void tpeReauthorizeRejectsAChangedAttributeValue() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            // department is KNOWN here, unlike the other reauthorize fixtures.
            var partialEntities = new PartialEntities(
                    Set.of(new PartialEntity(TPE_ALICE, Optional.of(Map.of("department", new PrimString("eng"))),
                            Optional.of(Set.of()), Optional.of(Map.of()), TPE_SCHEMA)),
                    TPE_SCHEMA);
            var success = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"), partialEntities)
                    .getSuccess().orElseThrow();
            var request = new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>());

            var same = success.reauthorize(request, new Entities(Set.of(
                    new Entity(TPE_ALICE, Map.of("department", new PrimString("eng")), Set.of(), Map.of()))));
            assertEquals(Decision.Allow, same.success.orElseThrow().getDecision());

            var changed = success.reauthorize(request, new Entities(Set.of(
                    new Entity(TPE_ALICE, Map.of("department", new PrimString("sales")), Set.of(), Map.of()))));
            assertEquals(SuccessOrFailure.Failure, changed.type);
            assertTrue(changed.errors.orElseThrow().get(0).message.contains(
                    "Entity `User::\"alice\"`'s attributes do not match"),
                    "unexpected message: " + changed.errors.orElseThrow().get(0).message);
        });
    }

    /**
     * An unknown entity <em>id</em> still pins the entity <em>type</em>. Easy to assume otherwise: the action below
     * accepts both principal types, so completing with an {@code Admin} is a legal request in its own right — Cedar
     * rejects it because the residuals were computed for a {@code User}.
     */
    @Test
    public void tpeReauthorizeRejectsAChangedPrincipalTypeWhenTheIdWasUnknown() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            // Its own schema: the shared fixture declares only one principal type, so a type mismatch there would be
            // rejected as an invalid request rather than as an inconsistent completion.
            var schema = Schema.parse(Schema.JsonOrCedar.Cedar,
                    "entity User { department?: String } tags String;\n"
                    + "entity Admin { department?: String } tags String;\n"
                    + "entity Photo;\n"
                    + "action view appliesTo { principal: [User, Admin], resource: [Photo], context: {} };");
            var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
            var root = new EntityUID(EntityTypeName.parse("Admin").get(), "root");

            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(EntityTypeName.parse("User").get())
                    .action(TPE_VIEW).resource(TPE_DOOR).emptyContext().schema(schema).build();
            var success = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"),
                    PartialEntities.empty()).getSuccess().orElseThrow();

            var asUser = success.reauthorize(
                    new AuthorizationRequest(alice, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    new Entities(Set.of(
                            new Entity(alice, Map.of("department", new PrimString("eng")), Set.of(), Map.of()))));
            assertEquals(Decision.Allow, asUser.success.orElseThrow().getDecision());

            var asAdmin = success.reauthorize(
                    new AuthorizationRequest(root, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    new Entities(Set.of(
                            new Entity(root, Map.of("department", new PrimString("eng")), Set.of(), Map.of()))));
            assertEquals(SuccessOrFailure.Failure, asAdmin.type);
            assertTrue(asAdmin.errors.orElseThrow().get(0).message.contains(
                    "Principal types `User` and `Admin` do not match"),
                    "unexpected message: " + asAdmin.errors.orElseThrow().get(0).message);
        });
    }

    /**
     * Cedar checks only that every entity it already knew about is unchanged — an entity <em>added</em> to the concrete
     * store is never examined, because the consistency check iterates the partial store and looks each entity up in the
     * concrete one. Pinned because it is the half of the check that is easy to assume works the other way.
     */
    @Test
    public void tpeReauthorizeAcceptsAnEntityAbsentFromThePartialStore() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var success = auth.isAuthorizedTypeAwarePartial(q, tpePolicies(TPE_ENG_POLICY, "p0"),
                    new PartialEntities(
                            Set.of(new PartialEntity(TPE_ALICE, Optional.empty(), Optional.of(Set.of()),
                                    Optional.of(Map.of()), TPE_SCHEMA)),
                            TPE_SCHEMA)).getSuccess().orElseThrow();

            var alice = new Entity(TPE_ALICE, Map.of("department", new PrimString("eng")), Set.of(), Map.of());
            var stranger = new Entity(new EntityUID(EntityTypeName.parse("User").get(), "carol"),
                    Map.of("department", new PrimString("sales")), Set.of(), Map.of());
            var request = new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>());

            assertEquals(Decision.Allow,
                    success.reauthorize(request, new Entities(Set.of(alice))).success.orElseThrow().getDecision());
            assertEquals(Decision.Allow,
                    success.reauthorize(request, new Entities(Set.of(alice, stranger)))
                            .success.orElseThrow().getDecision(),
                    "an entity the partial store never mentioned is not checked");
        });
    }

    /**
     * An erroring <em>forbid</em> is skipped, so a trivially-true permit still wins: the decision is Allow while the
     * diagnostics carry the evaluation error. A non-zero error count alongside an Allow is an easy shape to assert
     * wrongly, so it is pinned here.
     */
    @Test
    public void tpeReauthorizeAllowsDespiteAnErroringForbid() {
        var auth = new BasicAuthorizationEngine();
        TestUtil.assumeExperimentalFeature(() -> {
            var q = TypeAwarePartialAuthorizationRequest.builder()
                    .principal(TPE_ALICE).action(TPE_VIEW).resource(TPE_DOOR)
                    .emptyContext().schema(TPE_SCHEMA).build();
            var policies = new PolicySet(Set.of(
                    new Policy("permit(principal, action == Action::\"view\", resource);", "allowAll"),
                    new Policy("forbid(principal, action == Action::\"view\", resource)\n"
                            + "when { 9223372036854775807 + 1 > 0 };", "erroringForbid")));
            var alice = new Entity(TPE_ALICE, Map.of(), Set.of(), Map.of());
            var entities = new Entities(Set.of(alice));

            var success = auth.isAuthorizedTypeAwarePartial(q, policies,
                    new PartialEntities(entities, TPE_SCHEMA)).getSuccess().orElseThrow();
            assertEquals(Set.of("erroringForbid"), success.getErroredResidualIds());

            var reauthorized = success.reauthorize(
                    new AuthorizationRequest(TPE_ALICE, TPE_VIEW, TPE_DOOR, new HashMap<String, Value>()),
                    entities).success.orElseThrow();

            assertEquals(Decision.Allow, reauthorized.getDecision(),
                    "an erroring forbid is skipped, so the permit still decides");
            assertEquals(1, reauthorized.getErrors().size(),
                    "and the error is still reported, alongside the Allow");
        });
    }

}
