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

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Result of a type-aware partial authorization request. Unknown properties are ignored, so the native side can start
 * emitting a field before this class reads it — {@code reason} and the permit/forbid breakdown are not read yet.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class TypeAwarePartialAuthorizationResponse {
    private final SuccessOrFailure type;
    private final Optional<TypeAwarePartialAuthorizationSuccessResponse> success;
    private final Optional<List<DetailedError>> errors;
    private final List<String> warnings;

    @JsonCreator
    TypeAwarePartialAuthorizationResponse(
        @JsonProperty("type") SuccessOrFailure type,
        @JsonProperty("response") Optional<TypeAwarePartialAuthorizationSuccessResponse> success,
        @JsonProperty("errors") Optional<ArrayList<DetailedError>> errors,
        @JsonProperty("warnings") ArrayList<String> warnings
    ) {
        this.type = type;
        this.success = success;
        this.errors = errors.<List<DetailedError>>map(List::copyOf);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private TypeAwarePartialAuthorizationResponse(TypeAwarePartialAuthorizationResponse response,
            TypeAwarePartialAuthorizationRequest request, com.cedarpolicy.model.policy.PolicySet policies,
            com.cedarpolicy.model.entity.PartialEntities entities, com.cedarpolicy.AuthorizationEngine engine) {
        this.type = response.type;
        this.success = response.success.map(
                s -> new TypeAwarePartialAuthorizationSuccessResponse(s, request, policies, entities, engine));
        this.errors = response.errors;
        this.warnings = response.warnings;
    }

    /**
     * Returns a copy whose success response, if any, remembers what the residuals were computed from, so that
     * {@link TypeAwarePartialAuthorizationSuccessResponse#reauthorize} does not need them passed again.
     *
     * <p>Public only because the engine that calls it is in another package; <b>callers should not use it.</b> Pass
     * inputs other than the ones the residuals were computed from and the residuals describe one authorization while
     * {@code reauthorize} answers a different one — as a success, because Cedar can only check the completed request
     * against the partial inputs it is handed.
     *
     * @param request  the partial request the residuals were computed from
     * @param policies the policy set the residuals were computed from
     * @param entities the partially known entities the residuals were computed from
     * @param engine   the engine to reauthorize through
     * @return a copy carrying those inputs
     */
    public TypeAwarePartialAuthorizationResponse withInputs(TypeAwarePartialAuthorizationRequest request,
            com.cedarpolicy.model.policy.PolicySet policies,
            com.cedarpolicy.model.entity.PartialEntities entities, com.cedarpolicy.AuthorizationEngine engine) {
        java.util.Objects.requireNonNull(request, "request");
        java.util.Objects.requireNonNull(policies, "policies");
        java.util.Objects.requireNonNull(entities, "entities");
        java.util.Objects.requireNonNull(engine, "engine");
        return new TypeAwarePartialAuthorizationResponse(this, request, policies, entities, engine);
    }

    /**
     * Whether this is a success or a failure response.
     *
     * @return the response kind
     */
    @JsonProperty("type")
    public SuccessOrFailure getType() {
        return this.type;
    }

    /**
     * The residuals, present if and only if {@link #getType()} is {@code Success}.
     *
     * @return the successful response
     */
    @JsonProperty("response")
    public Optional<TypeAwarePartialAuthorizationSuccessResponse> getSuccess() {
        return this.success;
    }

    /**
     * The errors, present if and only if {@link #getType()} is {@code Failure}.
     *
     * @return the errors
     */
    @JsonProperty("errors")
    public Optional<List<DetailedError>> getErrors() {
        return this.errors;
    }

    /**
     * Warnings, always empty for type-aware partial evaluation: Cedar's {@code TpeResponse} exposes none.
     *
     * @return the warnings, empty if there were none
     */
    // The field is assigned from List.copyOf, so it is immutable and cannot be mutated through this
    // reference. SpotBugs does not recognise List.copyOf as establishing that, the way it does
    // Set.copyOf and Map.copyOf elsewhere in this package.
    @SuppressFBWarnings("EI_EXPOSE_REP")
    @JsonProperty("warnings")
    public List<String> getWarnings() {
        return this.warnings;
    }

    @Override
    public String toString() {
        final String warningsString = warnings.isEmpty() ? "" : "\nwith warnings: " + warnings;
        if (type == SuccessOrFailure.Success) {
            return "SUCCESS: " + success.get() + warningsString;
        } else {
            return "FAILURE: " + errors.get() + warningsString;
        }
    }

    /** Whether the response carries residuals or an error. */
    public enum SuccessOrFailure {
        @JsonProperty("residuals")
        Success,
        @JsonProperty("failure")
        Failure,
    }
}
