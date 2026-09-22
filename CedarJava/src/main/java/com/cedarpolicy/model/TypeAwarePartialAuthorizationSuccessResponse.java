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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Successful type-aware partial authorization response.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TypeAwarePartialAuthorizationSuccessResponse {
    private final AuthorizationSuccessResponse.Decision decision;
    private final Set<String> nontrivialResidualIds;
    private final Set<Policy> residuals;
    private final Set<Policy> nontrivialResiduals;
    private final Set<Policy> trivialResiduals;

    /**
     * Reads a successful response, parsing every residual into a {@link Policy} up front.
     *
     * @throws InternalException if a residual is not a valid Cedar policy
     */
    @JsonCreator
    TypeAwarePartialAuthorizationSuccessResponse(
        @JsonProperty("decision") AuthorizationSuccessResponse.Decision decision,
        @JsonProperty("residuals") Map<String, JsonNode> residuals,
        @JsonProperty("nontrivialResiduals") Set<String> nontrivialResiduals) throws InternalException {
        this.decision = decision;
        this.nontrivialResidualIds =
            nontrivialResiduals == null ? Set.of() : Set.copyOf(nontrivialResiduals);
        // One native call parses every residual; the partitions are filters over the result.
        this.residuals = Set.copyOf(
            toPolicySet(residuals == null ? Map.of() : residuals).policies);
        this.nontrivialResiduals = Set.copyOf(partition(this.residuals, this.nontrivialResidualIds, true));
        this.trivialResiduals = Set.copyOf(partition(this.residuals, this.nontrivialResidualIds, false));
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
     * The ids of the non-trivial residuals, which is the wire field the trivial/non-trivial split is derived from.
     *
     * @return ids of the residuals that were not reduced to a concrete true, false, or error
     */
    @JsonIgnore
    public Set<String> getNontrivialResidualIds() {
        return this.nontrivialResidualIds;
    }

    /**
     * Every residual. Each keeps the policy id and annotations of the policy it came from, has an unconstrained scope,
     * and carries the residual expression in a single {@code when} clause. Call {@link Policy#toJson()} for the JSON
     * form.
     *
     * @return every residual
     */
    @JsonIgnore
    public Set<Policy> getResiduals() {
        return this.residuals;
    }

    /**
     * The residuals that were not reduced to a concrete true, false, or error, so the ones whose conditions are worth
     * inspecting.
     *
     * @return the non-trivial residuals
     */
    @JsonIgnore
    public Set<Policy> getNontrivialResiduals() {
        return this.nontrivialResiduals;
    }

    /**
     * The residuals that were reduced to a concrete true, false, or error.
     *
     * @return the trivial residuals
     */
    @JsonIgnore
    public Set<Policy> getTrivialResiduals() {
        return this.trivialResiduals;
    }

    /**
     * Every residual as a policy set, which is the form {@link com.cedarpolicy.AuthorizationEngine} accepts and so
     * what to reauthorize against once the unknowns are filled in. Counterpart of {@code TpeResponse::policy_set}.
     *
     * <p>Trivial residuals are included deliberately: dropping a trivially true permit would turn an Allow into a
     * Deny.
     *
     * <p>Residuals have values from the original request and entities already folded in, and scope constraints
     * rewritten into the condition, so this set is only valid for reauthorizing that same request with its unknowns
     * resolved — not for a different request.
     *
     * <p>The set holds only static policies. That is Cedar's behaviour, not a CedarJava simplification: type-aware
     * partial evaluation substitutes a template link's slots and builds a fresh policy from the result, so the residual
     * of a linked policy reports no template even to a Rust caller. It keeps the original policy id, which is the only
     * way to trace it back to the template it came from.
     *
     * @return policy set of every residual
     */
    @JsonIgnore
    public PolicySet getPolicySet() {
        return new PolicySet(Set.copyOf(this.residuals));
    }

    private static Set<Policy> partition(Set<Policy> residuals, Set<String> nontrivialIds, boolean nontrivial) {
        return residuals.stream()
            .filter(p -> nontrivialIds.contains(p.getID()) == nontrivial)
            .collect(Collectors.toSet());
    }

    private static PolicySet toPolicySet(Map<String, JsonNode> policies) throws InternalException {
        final ObjectNode staticPolicies = JsonNodeFactory.instance.objectNode();
        // Residuals always resolve to static policies
        staticPolicies.setAll(policies);
        final ObjectNode policySet = JsonNodeFactory.instance.objectNode();
        policySet.set("staticPolicies", staticPolicies);
        policySet.set("templates", JsonNodeFactory.instance.objectNode());
        policySet.set("templateLinks", JsonNodeFactory.instance.arrayNode());
        return PolicySet.parsePoliciesJson(policySet.toString());
    }
}
