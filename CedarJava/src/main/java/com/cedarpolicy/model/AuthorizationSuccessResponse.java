/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
    /** The three possible results of request evaluation. */
    public enum Decision {
        /** Represents an authorization request that is allowed. */
        @JsonProperty("Allow")
        Allow,
        /** Represents an authorization request that is denied. */
        @JsonProperty("Deny")
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
        private ImmutableList<String> errors;

        /**
         * Read the reasons and errors from a JSON object.
         *
         * @param reason Reasons (e.g., matching policies)
         * @param errors Errors encountered checking the request
         */
        @SuppressFBWarnings
        public Diagnostics(
                @JsonProperty("reason") Set<String> reason,
                @JsonProperty("errors") List<String> errors) {
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
        public List<String> getErrors() {
            return this.errors;
        }
    }

    /** Internal representation of the response from a query evaluation. */
    public static class InterfaceResponse {

        private final Decision decision;

        private final Diagnostics diagnostics;

        /**
         * Read the response from a JSON object.
         *
         * @param decision authorization decision for the given request
         * @param diagnostics a collection of policies that contributed to the result and any errors
         *     that might have happened during evaluation
         */
        @SuppressFBWarnings
        public InterfaceResponse(
            @JsonProperty("decision") Decision decision,
            @JsonProperty("diagnostics") Diagnostics diagnostics) {
            this.decision = decision;
            this.diagnostics = diagnostics;
        }
    }

    /**
     * Construct an authorization response.
     *
     * @param response response returned by the authorization engine
     */
    @JsonCreator
    public AuthorizationSuccessResponse(@JsonProperty("response") InterfaceResponse response) {
        this.decision = response.decision;
        this.diagnostics = response.diagnostics;
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
    public Set<String> getReasons() {
        return diagnostics.reason;
    }

    /**
     * Set of errors and warnings returned by Cedar.
     *
     * @return list with errors that happened for a given Request
     */
    public List<String> getErrors() {
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
}

/**
 * Failure authorization response
 */
