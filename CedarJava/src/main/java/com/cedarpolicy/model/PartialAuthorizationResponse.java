package com.cedarpolicy.model;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
public class PartialAuthorizationResponse {
    private final Decision decision;
    private final ImmutableSet<String> satisfied;
    private final ImmutableSet<String> errored;
    private final ImmutableSet<String> may_be_determining;
    private final ImmutableSet<String> must_be_determining;
    private final ImmutableMap<String, JsonNode> residuals;
    private final ImmutableSet<String> nontrivial_residuals;
    private final ImmutableSet<String> warnings;

    public PartialAuthorizationResponse(Decision decision, Set<String> satisfied, Set<String> errored,
            Set<String> may_be_determining, Set<String> must_be_determining, Map<String, JsonNode> residuals,
            Set<String> nontrivial_residuals, Set<String> warnings) {
        this.decision = decision;
        // note that ImmutableSet.copyOf() attempts to avoid a full copy when possible; see https://github.com/google/guava/wiki/ImmutableCollectionsExplained
        this.satisfied = ImmutableSet.copyOf(satisfied);
        this.errored = ImmutableSet.copyOf(errored);
        this.may_be_determining = ImmutableSet.copyOf(may_be_determining);
        this.must_be_determining = ImmutableSet.copyOf(must_be_determining);
        this.residuals = ImmutableMap.copyOf(residuals);
        this.nontrivial_residuals = ImmutableSet.copyOf(nontrivial_residuals);
        if (warnings == null) {
            this.warnings = ImmutableSet.of(); // empty
        } else {
            this.warnings = ImmutableSet.copyOf(warnings);
        }
    }

    /**
     * The optional decision returned by partial authorization
     *
     * @return a nullable reference to the decision (null means that no conclusive decision can be made)
     */
    public Decision getDecision() {
        return this.decision;
    }

    /**
     * The map from policy ids to residuals
     *
     * @return map of residuals
     */
    public Map<String, JsonNode> getResiduals() {
            return this.residuals;
    }

    /**
     * Set of policies that are satisfied by the partial request
     *
     * @return set of policy ids
     */
    public Set<String> getSatisfied() {
        return this.satisfied;
    }

    /**
     * Set of policies that errored during the partial authorization
     *
     * @return set of policy ids
     */
    public Set<String> getErrored() {
        return this.errored;
    }

    /**
     * Over approximation of policies that determine the auth decision
     *
     * @return set of policy ids
     */
    public Set<String> getMayBeDetermining() {
        return this.may_be_determining;
    }

    /**
     * Under approximation of policies that determine the auth decision
     *
     * @return set of policy ids
     */
    public Set<String> getMustBeDetermining() {
        return this.must_be_determining;
    }

    /**
     * Set of non-trivial residual policies
     *
     * @return set of policy ids
     */
    public Set<String> getNontrivialResiduals() {
        return this.nontrivial_residuals;
    }

    /**
     * Deserializer factory method for PartialAuthorizationResponse.
     * @param nested Deserialized object for nested JSON object.
     * @param decision Deserialized `decision` attribute of nested JSON object.
     * @param satisfied Deserialized `satisfied` attribute of nested JSON object.
     * @param errored Deserialized `errored` attribute of nested JSON object.
     * @param may_be_determining Deserialized `mayBeDetermining` attribute of nested JSON object.
     * @param must_be_determining Deserialized `mustBeDetermining` attribute of nested JSON object.
     * @param residuals Deserialized `residual` attribute of nested JSON object.
     * @param nontrivial_residuals Deserialized `nontrivialResiduals` attribute of nested JSON object.
     * @param warnings Deserialized `warnings` attribute of nested JSON object.
     * @return
     */
    @JsonCreator
    public static PartialAuthorizationResponse createPartialAuthorizationResponse(
        @JsonProperty("response") PartialAuthorizationResponse nested,
        @JsonProperty("decision") Decision decision,
        @JsonProperty("satisfied") Set<String> satisfied,
        @JsonProperty("errored") Set<String> errored,
        @JsonProperty("mayBeDetermining") Set<String> may_be_determining,
        @JsonProperty("mustBeDetermining") Set<String> must_be_determining,
        @JsonProperty("residuals") Map<String, JsonNode> residuals,
        @JsonProperty("nontrivialResiduals") Set<String> nontrivial_residuals,
        @JsonProperty("warnings") Set<String> warnings) {
            if (nested != null) {
                return nested;
            }
            return new PartialAuthorizationResponse(decision, satisfied, errored, may_be_determining, must_be_determining, residuals, nontrivial_residuals, warnings);
    }
}
