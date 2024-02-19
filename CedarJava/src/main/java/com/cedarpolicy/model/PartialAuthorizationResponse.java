package com.cedarpolicy.model;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.AuthorizationResponse.Decision;
import com.cedarpolicy.model.AuthorizationResponse.Diagnostics;
import com.cedarpolicy.model.slice.Policy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.stream.Collectors;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
public abstract class PartialAuthorizationResponse {
    private final Diagnostics diagnostics;

    /**
     * Deserializer factory method for PartialAuthorizationResponse.
     * @param nested Deserialized object for nested JSON object.
     * @param decision Deserialized decision attribute of nested JSON object.
     * @param residuals Deserialized residual attribute of nested JSON object.
     * @param diagnostics Deserialized diagnostics attribute of nested JSON object.
     * @return
     */
    @JsonCreator
    public static PartialAuthorizationResponse createPartialAuthorizationResponse(
            @JsonProperty("response") PartialAuthorizationResponse nested,
            @JsonProperty("decision") Decision decision,
            @JsonProperty("residuals") Map<String, JsonNode> residuals,
            @JsonProperty("diagnostics") Diagnostics diagnostics) {
        if (nested != null) {
            // bubble the nested deserialized object for the root JSON object
            return nested;
        }
        else if (decision != null && diagnostics != null) {
            return new ConcretePartialAuthorizationResponse(decision, diagnostics);
        }
        else if (residuals != null && diagnostics != null) {
            return new ResidualPartialAuthorizationResponse(residuals, diagnostics);
        }
        else {
            return null;
        }
    }

    protected PartialAuthorizationResponse(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Diagnostics getDiagnostics() {
        return this.diagnostics;
    }

    /**
     * Set of policyID's that caused the decision. For example, when a policy evaluates to Deny, all
     * deny policies that evaluated to True will appear in Reasons.
     *
     * @return set of policy ids that contributed to the decision
     */
    public Set<String> getReasons() {
        return this.diagnostics.getReasons();
    }

    /**
     * Set of errors and warnings returned by Cedar.
     *
     * @return list with errors that happened for a given Request
     */
    public List<String> getErrors() {
        return this.diagnostics.getErrors();
    }

    /**
     * Check if the evaluator was able to reach a decision.
     *
     * @return true if the request evaluated to either Allow or Deny.
     */
    public abstract boolean reachedDecision();

    public static final class ConcretePartialAuthorizationResponse extends PartialAuthorizationResponse {
        private final Decision decision;

        private ConcretePartialAuthorizationResponse(Decision decision, Diagnostics diagnostics) {
            super(diagnostics);
            this.decision = decision;
        }

        public Decision getDecision() {
            return this.decision;
        }

        /**
         * Check authorization decision.
         *
         * @return true if the request evaluated to Allow.
         */
        public boolean isAllowed() {
            return this.decision == Decision.Allow;
        }

        @Override
        public boolean reachedDecision() {
            return this.decision != Decision.NoDecision;
        }
    }

    public static final class ResidualPartialAuthorizationResponse extends PartialAuthorizationResponse {
        private final Set<Policy> residuals;

        public ResidualPartialAuthorizationResponse(Map<String, JsonNode> residuals, Diagnostics diagnostics) {
            super(diagnostics);
            this.residuals = residuals.entrySet().stream()
                    .map(e -> new Policy(e.getValue().toString(), e.getKey()))
                    .collect(Collectors.toUnmodifiableSet());
        }

        public Set<Policy> getResiduals() {
            return Collections.unmodifiableSet(this.residuals);
        }

        @Override
        public boolean reachedDecision() {
            return false;
        }
    }
}
