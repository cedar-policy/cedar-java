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

package com.cedarpolicy.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.entity.PartialEntities;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Successful type-aware partial authorization response. Residuals are for <em>inspection</em>; to finish an
 * authorization once the unknowns are known call {@link #reauthorize} rather than authorizing them yourself — see
 * {@link #getResiduals()} for why.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TypeAwarePartialAuthorizationSuccessResponse {
    private final AuthorizationSuccessResponse.Decision decision;
    private final Set<String> nontrivialResidualIds;
    private final Set<String> erroredResidualIds;
    private final Set<Policy> residuals;
    private final Set<Policy> nontrivialResiduals;
    private final Set<Policy> trivialResiduals;
    private final Set<Policy> erroredResiduals;
    private final TypeAwarePartialAuthorizationRequest evaluatedRequest;
    private final PolicySet evaluatedPolicies;
    private final PartialEntities evaluatedEntities;
    private final AuthorizationEngine engine;

    @JsonCreator
    TypeAwarePartialAuthorizationSuccessResponse(
        @JsonProperty("decision") AuthorizationSuccessResponse.Decision decision,
        @JsonProperty("residuals") Map<String, String> residuals,
        @JsonProperty("nontrivialResiduals") Set<String> nontrivialResiduals,
        @JsonProperty("erroredResiduals") Set<String> erroredResiduals) {
        this.decision = decision;
        this.nontrivialResidualIds =
            nontrivialResiduals == null ? Set.of() : Set.copyOf(nontrivialResiduals);
        this.erroredResidualIds =
            erroredResiduals == null ? Set.of() : Set.copyOf(erroredResiduals);
        this.residuals = Set.copyOf((residuals == null ? Map.<String, String>of() : residuals).entrySet().stream()
            .map(e -> new Policy(e.getValue(), e.getKey()))
            .collect(Collectors.toSet()));
        this.nontrivialResiduals = Set.copyOf(select(this.residuals, this.nontrivialResidualIds, true));
        this.trivialResiduals = Set.copyOf(select(this.residuals, this.nontrivialResidualIds, false));
        this.erroredResiduals = Set.copyOf(select(this.residuals, this.erroredResidualIds, true));
        this.evaluatedRequest = null;
        this.evaluatedPolicies = null;
        this.evaluatedEntities = null;
        this.engine = null;
    }

    /** Copy that remembers what the residuals were computed from, so {@link #reauthorize} needs only what is new. */
    TypeAwarePartialAuthorizationSuccessResponse(TypeAwarePartialAuthorizationSuccessResponse other,
            TypeAwarePartialAuthorizationRequest request, PolicySet policies, PartialEntities entities,
            AuthorizationEngine engine) {
        this.decision = other.decision;
        // Already immutable; copied again so SpotBugs can see every assignment path produce an immutable set.
        this.nontrivialResidualIds = Set.copyOf(other.nontrivialResidualIds);
        this.erroredResidualIds = Set.copyOf(other.erroredResidualIds);
        this.residuals = Set.copyOf(other.residuals);
        this.nontrivialResiduals = Set.copyOf(other.nontrivialResiduals);
        this.trivialResiduals = Set.copyOf(other.trivialResiduals);
        this.erroredResiduals = Set.copyOf(other.erroredResiduals);
        this.evaluatedRequest = request;
        // Snapshotted, not aliased: PolicySet's collections are public and non-final and its constructors do not copy,
        // so keeping the caller's instance would let them mutate the set after inspecting the residuals and have
        // reauthorize decide from a policy that appears in none of them.
        this.evaluatedPolicies = new PolicySet(Set.copyOf(policies.policies), Set.copyOf(policies.templates),
                List.copyOf(policies.templateLinks));
        this.evaluatedEntities = entities;
        this.engine = engine;
    }

    /**
     * Completes this authorization now that the previously unknown parts of the request and entities are known,
     * returning the decision the original policies would have reached. Prefer this over authorizing
     * {@link #getResiduals()} yourself: a residual carries the original request's values folded in, so authorizing it
     * for a different request silently grants access the original policies denied.
     *
     * <p>This response carries the partial request, policy set and entities the residuals came from, so they cannot
     * disagree with what you inspected. Cedar validates {@code request} and {@code entities} against them and rejects a
     * mismatch: it examines only the entities the partial set knew about, but compares each one's whole attribute map,
     * whole tag map and ancestor <em>closure</em>, so identical declared {@code parents} can still be rejected if the
     * partial set lacked an entity further up the chain.
     *
     * @param request  The same request these residuals were computed from, with its unknowns filled in.
     * @param entities The same entities, with their unknowns filled in.
     * @return The authorization decision, or a failure response if the completed inputs are inconsistent with the
     *     partial ones.
     * @throws AuthException On failure to make the authorization request, or if this response was deserialized directly
     *     rather than obtained from an {@link AuthorizationEngine} and so has nothing to reauthorize.
     */
    @Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
    public AuthorizationResponse reauthorize(AuthorizationRequest request,
            com.cedarpolicy.model.entity.Entities entities) throws AuthException {
        if (this.engine == null) {
            throw new AuthException("this response was not produced by an authorization engine, "
                    + "so it carries nothing to reauthorize");
        }
        return this.engine.reauthorize(this.evaluatedRequest, this.evaluatedPolicies, this.evaluatedEntities,
                request, entities);
    }

    /**
     * The decision, or null if type-aware partial evaluation could not reach one.
     *
     * @return the decision, nullable
     */
    @JsonIgnore
    public AuthorizationSuccessResponse.Decision getDecision() {
        return this.decision;
    }

    /**
     * The ids of the non-trivial residuals.
     *
     * @return ids of the residuals that were not reduced to a concrete true, false, or error
     */
    @JsonIgnore
    public Set<String> getNontrivialResidualIds() {
        return this.nontrivialResidualIds;
    }

    /**
     * Every residual. Each keeps the id and the annotations of the policy it came from.
     *
     * <p><b>These are not policies you can authorize against.</b> A residual has the original request's values folded
     * in and its scope constraints rewritten into the condition, so one computed for {@code User::"alice"} carries no
     * principal constraint: authorizing it for {@code User::"bob"} <em>grants Bob access that only Alice had</em>,
     * silently and without any error. And a residual containing a known-error subexpression cannot be parsed by Cedar
     * at all, so a policy set containing one fails to parse, taking every other residual with it — see
     * {@link #getErroredResidualIds()}, which also lists the methods such a residual does not support.
     *
     * <p>Use {@link #reauthorize} instead: it checks the completed request against the partial one, which authorizing
     * these policies cannot do.
     *
     * @return every residual
     */
    @JsonIgnore
    public Set<Policy> getResiduals() {
        return this.residuals;
    }

    /**
     * The residuals that were not reduced to a concrete true, false, or error, so the ones whose conditions are worth
     * inspecting. Together with {@link #getTrivialResiduals()} this partitions {@link #getResiduals()};
     * {@link #getErroredResidualIds()} cuts across that split, and a residual named there is source-only.
     *
     * @return the non-trivial residuals
     */
    @JsonIgnore
    public Set<Policy> getNontrivialResiduals() {
        return this.nontrivialResiduals;
    }

    /**
     * The residuals that were reduced to a concrete true, false, or error. Together with
     * {@link #getNontrivialResiduals()} this partitions {@link #getResiduals()}; a residual named by
     * {@link #getErroredResidualIds()} may appear here too, in which case it is source-only.
     *
     * @return the trivial residuals
     */
    @JsonIgnore
    public Set<Policy> getTrivialResiduals() {
        return this.trivialResiduals;
    }

    /**
     * The ids of the residuals that <em>contain</em> a subexpression Cedar already knows would error — an integer
     * overflow, for instance. Cedar marks it with a node it can render but not parse back, so these residuals are
     * source-only: {@link Policy#getSource()}, {@link Policy#getID()} and {@code toString()} work, while
     * {@link Policy#effect()}, {@link Policy#toJson()}, {@link Policy#getAnnotations()} and
     * {@link Policy#getAnnotation(String)} each re-parse the source and throw
     * {@link com.cedarpolicy.model.exception.InternalException}.
     *
     * <p>Containing an error node does <em>not</em> mean the policy errors: in {@code somethingUnknown || error} whose
     * left side turns out true, the policy is satisfied and decides the authorization normally. So this is not a set of
     * inert policies, and it cuts across the {@link #getNontrivialResiduals()} / {@link #getTrivialResiduals()} split
     * rather than being a third partition — a residual that reduced entirely to an error is trivial, one with an error
     * nested inside a live condition is not. {@link #reauthorize} is unaffected either way: it evaluates the real
     * residual natively, never Cedar's rendering of it.
     *
     * @return ids of the residuals containing a known-error subexpression, empty if none do
     */
    @JsonIgnore
    public Set<String> getErroredResidualIds() {
        return this.erroredResidualIds;
    }

    /**
     * The residuals containing a known-error subexpression. See {@link #getErroredResidualIds()} for which of their
     * methods are usable, and why such a policy may still decide an authorization.
     *
     * @return the residuals containing a known-error subexpression, empty if none do
     */
    @JsonIgnore
    public Set<Policy> getErroredResiduals() {
        return this.erroredResiduals;
    }

    @Override
    public String toString() {
        return "decision: " + this.decision + "\nresiduals: " + this.residuals
                + (this.erroredResidualIds.isEmpty() ? "" : "\nerrored: " + this.erroredResidualIds);
    }

    private static Set<Policy> select(Set<Policy> residuals, Set<String> ids, boolean present) {
        return residuals.stream()
            .filter(p -> ids.contains(p.getID()) == present)
            .collect(Collectors.toSet());
    }
}
