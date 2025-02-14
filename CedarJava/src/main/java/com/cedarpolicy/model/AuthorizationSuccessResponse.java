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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Successful authorization response
 */
public final class AuthorizationSuccessResponse {
    /** The two possible results of request evaluation. */
    public enum Decision {
        /** Represents an authorization request that is allowed. */
        @JsonProperty("allow")
        Allow,
        /** Represents an authorization request that is denied. */
        @JsonProperty("deny")
        Deny,
    }

    /** The reasons and errors from a request evaluation. */
    public static class Diagnostics {
        /**
         * Set of policyID's that caused the decision. For example, when a policy evaluates to Deny,
         * all forbid policies that evaluated to True will appear in `reason`.
         */
        private ImmutableSet<String> reason;

        /** Set of errors and warnings returned by Cedar. */
        private ImmutableList<AuthorizationError> errors;

        /**
         * Read the reasons and errors from a JSON object.
         *
         * @param reason Reasons (e.g., matching policies)
         * @param errors Errors encountered checking the request
         */
        @SuppressFBWarnings
        public Diagnostics(
                @JsonProperty("reason") Set<String> reason,
                @JsonProperty("errors") List<AuthorizationError> errors) {
            this.errors = ImmutableList.copyOf(errors);
            this.reason = ImmutableSet.copyOf(reason);
        }

        /**
         * Set of policyID's that caused the decision. For example, when a policy evaluates to Deny, all
         * deny policies that evaluated to True will appear in Reasons.
         *
         * @return list with the policy ids that contributed to the decision
         */
        public Set<String> getReasons() {
            return this.reason;
        }

        /**
         * Set of errors and warnings returned by Cedar.
         *
         * @return list with errors that happened for a given Request
         */
        public List<AuthorizationError> getErrors() {
            return this.errors;
        }
    }

    /**
     * Construct a successful authorization response.
     */
    @JsonCreator
    public AuthorizationSuccessResponse(
        @JsonProperty("decision") Decision decision,
        @JsonProperty("diagnostics") Diagnostics diagnostics
    ) {
        this.decision = decision;
        this.diagnostics = diagnostics;
    }

    /** Error (or warning) which occurred in a particular policy during authorization */
    public static final class AuthorizationError {
        /** Id of the policy where the error (or warning) occurred */
        @JsonProperty("policyId")
        private final String policyId;
        /**
         * Error (or warning).
         * You can look at the `severity` field to see whether it is
         * actually an error or a warning.
         */
        @JsonProperty("error")
        private final DetailedError error;

        @JsonCreator
        public AuthorizationError(
            @JsonProperty("policyId") String policyId,
            @JsonProperty("error") DetailedError error
        ) {
            this.policyId = policyId;
            this.error = error;
        }

        public String getPolicyId() {
            return this.policyId;
        }

        public DetailedError getError() {
            return this.error;
        }

        @Override
        public String toString() {
            return String.format("AuthorizationError{policyId=%s, error=%s}", policyId, error);   
        }
    }

    /** Result of request evaluation. */
    private final Decision decision;

    private final Diagnostics diagnostics;

    /**
     * Result of the request evaluation.
     *
     * @return {@link Decision} that contains the result for a given request
     */
    public Decision getDecision() {
        return decision;
    }

    /**
     * Set of policyID's that caused the decision. For example, when a policy evaluates to Deny, all
     * deny policies that evaluated to True will appear in Reasons.
     *
     * @return list with the policy ids that contributed to the decision
     */
    public Set<String> getReason() {
        return diagnostics.reason;
    }

    /**
     * Set of errors and warnings returned by Cedar.
     *
     * @return list with errors that happened for a given Request
     */
    public List<AuthorizationError> getErrors() {
        return diagnostics.errors;
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
    public String toString() {
        return String.format("%s, reason %s, errors %s", decision, diagnostics.reason, diagnostics.errors);
    }
}
