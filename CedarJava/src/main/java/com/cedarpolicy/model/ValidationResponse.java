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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of a validation request. */
public final class ValidationResponse {
    /** Is this a success or a failure response */
    @JsonProperty("type")
    public final SuccessOrFailure type;
    /** This will be present if and only if `type` is `Success`. */
    public final Optional<ValidationResults> results;
    /**
     * This will be present if and only if `type` is `Failure`.
     *
     * These errors are not validation errors (those would be
     * reported in `results`), but rather higher-level errors, like
     * a failure to parse or to call the validator.
     */
    public final Optional<ImmutableList<DetailedError>> errors;
    /**
     * Other warnings not associated with particular policies.
     * For instance, warnings about your schema itself.
     * These warnings can be produced regardless of whether we have `results` or
     * `errors`.
    */
    public final ImmutableList<DetailedError> warnings;

    public static final class ValidationResults {
        /** Validation errors associated with particular policies. */
        @JsonProperty("validation_errors")
        public final ImmutableList<ValidationError> validationErrors;
        /** Validation warnings associated with particular policies. */
        @JsonProperty("validation_warnings")
        public final ImmutableList<ValidationError> validationWarnings;

        @JsonCreator
        public ValidationResults(
            @JsonProperty("validation_errors") Optional<List<ValidationError>> validationErrors,
            @JsonProperty("validation_warnings") Optional<List<ValidationError>> validationWarnings) {
            // note that ImmutableSet.copyOf() attempts to avoid a full copy when possible; see https://github.com/google/guava/wiki/ImmutableCollectionsExplained
            if (validationErrors.isPresent()) {
                this.validationErrors = ImmutableList.copyOf(validationErrors.get());
            } else {
                this.validationErrors = ImmutableList.of(); // empty
            }
            if (validationWarnings.isPresent()) {
                this.validationWarnings = ImmutableList.copyOf(validationWarnings.get());
            } else {
                this.validationWarnings = ImmutableList.of(); // empty
            }
        }
    }

    /**
     * Construct a validation response.
     *
     * Either `errors` should be None and `validationErrors` and `validationWarnings` both present,
     * or the other way around.
     * If `type` is `Success`, `validationErrors` and `validationWarnings` should be present and `errors` empty.
     * If `type` is `Failure`, `errors` should be present and `validationErrors` and `validationWarnings` empty.
     */
    @JsonCreator
    public ValidationResponse(
        @JsonProperty("type") SuccessOrFailure type,
        @JsonProperty("validation_errors") Optional<List<ValidationError>> validationErrors,
        @JsonProperty("validation_warnings") Optional<List<ValidationError>> validationWarnings,
        @JsonProperty("errors") Optional<List<DetailedError>> errors,
        @JsonProperty("warnings") @JsonAlias("other_warnings") Optional<List<DetailedError>> warnings) {
        this.type = type;
        this.errors = errors.map((list) -> ImmutableList.copyOf(list));
        if (type == SuccessOrFailure.Success) {
            this.results = Optional.of(new ValidationResults(validationErrors, validationWarnings));
        } else {
            this.results = Optional.empty();
        }
        if (warnings.isPresent()) {
            this.warnings = ImmutableList.copyOf(warnings.get());
        } else {
            this.warnings = ImmutableList.of(); // empty
        }
    }

    public enum SuccessOrFailure {
        @JsonProperty("success")
        Success,
        @JsonProperty("failure")
        Failure,
    }

    /**
     * Returns `true` if validation completed successfully with no errors (there may be warnings).
     * Returns `false` if validation returned errors, or if there were errors
     * prior to even calling the validator.
     */
    public boolean validationPassed() {
        if (results.isPresent()) {
            return results.get().validationErrors.isEmpty();
        } else {
            // higher-level errors are present
            return false;
        }
    }

    /** Readable string representation. */
    public String toString() {
        if (results.isPresent()) {
            return "ValidationResponse(validationErrors = " + results.get().validationErrors + ", validationWarnings = " + results.get().validationWarnings + ")";
        } else {
            return "ValidationResponse(errors = " + errors.get() + ")";
        }
    }

    /** Error (or warning) for a specific policy after validation */
    public static final class ValidationError {
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

        /**
         * Create error (or warning) from JSON.
         *
         * @param policyId Policy ID to which error applies.
         * @param error The error or warning.
         */
        @JsonCreator
        public ValidationError(
            @JsonProperty("policyId") String policyId,
            @JsonProperty("error") DetailedError error) {
            this.policyId = policyId;
            this.error = error;
        }

        /**
         * Get the policy ID.
         *
         * @return The policy ID.
         */
        public String getPolicyId() {
            return this.policyId;
        }

        /**
         * Get the error.
         *
         * @return The error.
         */
        public DetailedError getError() {
            return this.error;
        }

        /** Equals. */
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof ValidationError)) {
                return false;
            }

            final ValidationError other = (ValidationError) o;
            return policyId.equals(other.policyId) && error.equals(other.error);
        }

        /** Hash. */
        @Override
        public int hashCode() {
            return Objects.hash(policyId, error);
        }

        /** Readable string representation. */
        public String toString() {
            return "Error(policyId=" + policyId + ", error=" + error + ")";
        }
    }
}
