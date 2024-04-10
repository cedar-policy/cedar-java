package com.cedarpolicy.model;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.AuthorizationResponse.Decision;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
public class PartialAuthorizationResponse {
    private Decision decision;
    private Set<String> satisfied;
    private Set<String> errored;
    private Set<String> may_be_determining;
    private Set<String> must_be_determining;
    private Map<String, JsonNode> residuals;
    private Set<String> nontrivial_residuals;

    public PartialAuthorizationResponse(Decision decision, Set<String> satisfied, Set<String> errored,
            Set<String> may_be_determining, Set<String> must_be_determining, Map<String, JsonNode> residuals,
            Set<String> nontrivial_residuals) {
        this.decision = decision;
        this.errored = new HashSet<String>(errored);
        this.may_be_determining = new HashSet<String>(may_be_determining);
        this.must_be_determining = new HashSet<String>(must_be_determining);
        this.residuals = new HashMap<String, JsonNode>(residuals);
        this.nontrivial_residuals = new HashSet<String>(nontrivial_residuals);
    }

    public Decision getDecision() {
        return this.decision;
    }

    public Set<String> getResiduals() {
            return this.residuals.keySet();
    }

    /**
     * Deserializer factory method for PartialAuthorizationResponse.
     * @param decision Deserialized decision attribute of nested JSON object.
     * @param residuals Deserialized residual attribute of nested JSON object.
     * @param diagnostics Deserialized diagnostics attribute of nested JSON object.
     * @return
     */
    @JsonCreator
    public static PartialAuthorizationResponse createPartialAuthorizationResponse(
        @JsonProperty("response") PartialAuthorizationResponse nested,
        @JsonProperty("decision") Decision decision,
        @JsonProperty("satisfied") Set<String> satisfied,
        @JsonProperty("errored") Set<String> errored,
        @JsonProperty("may_be_determining") Set<String> may_be_determining,
        @JsonProperty("must_be_determining") Set<String> must_be_determining,
        @JsonProperty("residuals") Map<String, JsonNode> residuals,
        @JsonProperty("nontrivial_residuals") Set<String> nontrivial_residuals) {
            if (nested != null) {
                return nested;
            }
            return new PartialAuthorizationResponse(decision, satisfied, errored, may_be_determining, must_be_determining, residuals, nontrivial_residuals);
    }
}
