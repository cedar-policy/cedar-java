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
    /** Exactly one of `results` or `errors` should be None. */
    public final Optional<ValidationResults> results;
    /**
     * Exactly one of `results` or `errors` should be None.
     *
     * These errors are not validation errors (those would be
     * reported in `results`), but rather higher-level errors, like
     * a failure to parse or to call the validator.
     */
    public final Optional<ImmutableList<String>> errors;
    /**
     * Other warnings not associated with particular policies.
     * For instance, warnings about your schema itself.
     * These warnings can be produced regardless of whether we have `results` or
     * `errors`.
    */
    public final ImmutableList<String> warnings;

    public static class ValidationResults {
        /** Validation errors associated with particular policies. */
        public final ImmutableList<ValidationError> validation_errors;
        /** Validation warnings associated with particular policies. */
        public final ImmutableList<ValidationWarning> validation_warnings;

        @JsonCreator
        public ValidationResults(
            @JsonProperty("validation_errors") List<ValidationError> validation_errors,
            @JsonProperty("validation_warnings") List<ValidationWarning> validation_warnings) {
            // note that ImmutableSet.copyOf() attempts to avoid a full copy when possible; see https://github.com/google/guava/wiki/ImmutableCollectionsExplained
            this.validation_errors = ImmutableList.copyOf(validation_errors);
            this.validation_warnings = ImmutableList.copyOf(validation_warnings);
        }
    }

    /**
     * Construct a validation response.
     *
     * Either `errors` should be None and `validation_errors` and `validation_warnings` both present,
     * or the other way around.
     */
    @JsonCreator
    public ValidationResponse(
        @JsonProperty("validation_errors") Optional<List<ValidationError>> validation_errors,
        @JsonProperty("validation_warnings") Optional<List<ValidationWarning>> validation_warnings,
        @JsonProperty("errors") Optional<List<String>> errors,
        @JsonProperty("warnings") @JsonAlias("other_warnings") Optional<List<String>> warnings) {
        if (errors.isPresent()) {
            this.errors = Optional.of(ImmutableList.copyOf(errors.get()));
            this.results = Optional.empty();
        } else {
            // if we don't have `errors`, we should have both
            // `validation_errors` and `validation_warnings`
            this.results = Optional.of(new ValidationResults(validation_errors.get(), validation_warnings.get()));
            this.errors = Optional.empty();
        }
        this.warnings = ImmutableList.copyOf(warnings.orElse(new ArrayList<String>()));
    }

    /**
     * Returns `true` if validation completed successfully with no errors (there may be warnings).
     * Returns `false` if validation returned errors, or if there were errors
     * prior to even calling the validator.
     */
    public boolean validationPassed() {
        if (results.isPresent()) {
            return results.get().validation_errors.isEmpty();
        } else {
            // higher-level errors are present
            return false;
        }
    }

    /** Readable string representation. */
    public String toString() {
        if (results.isPresent()) {
            return "ValidationResponse(validation_errors = " + results.get().validation_errors + ", validation_warnings = " + results.get().validation_warnings + ")";
        } else {
            return "ValidationResponse(errors = " + errors.get() + ")";
        }
    }

    /** Error for a specific policy. */
    public static final class ValidationError {
        private final String policyId;
        private final String error;
        private final SourceLocation sourceLocation;

        /**
         * Create error from JSON.
         *
         * @param policyId Policy ID to which error applies.
         * @param sourceLocation location error orginiated from.
         * @param error The Error.
         */
        @JsonCreator
        public ValidationError(@JsonProperty("policyId") String policyId,
                @JsonProperty("sourceLocation") SourceLocation sourceLocation,
                @JsonProperty("error") String error) {
            this.policyId = policyId;
            this.sourceLocation = sourceLocation;
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
        public String getError() {
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

    /** Warning for a specific policy. */
    public static final class ValidationWarning {
        private final String policyId;
        private final Optional<SourceLocation> sourceLocation;
        private final String warning;

        /**
         * Create warning from JSON.
         *
         * @param policyId Policy ID to which warning applies.
         * @param sourceLocation (optional) the location the warning applies to
         * @param warning The Warning.
         */
        @JsonCreator
        public ValidationWarning(@JsonProperty("policyId") String policyId,
                @JsonProperty("sourceLocation") Optional<SourceLocation> sourceLocation,
                @JsonProperty("warning") String warning) {
            this.policyId = policyId;
            this.sourceLocation = sourceLocation;
            this.warning = warning;
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
         * Get the warning.
         *
         * @return The warning.
         */
        public String getWarning() {
            return this.warning;
        }

        /** Equals. */
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof ValidationWarning)) {
                return false;
            }

            final ValidationWarning other = (ValidationWarning) o;
            return policyId.equals(other.policyId) && warning.equals(other.warning);
        }

        /** Hash. */
        @Override
        public int hashCode() {
            return Objects.hash(policyId, warning);
        }

        /** Readable string representation. */
        public String toString() {
            return "Warning(policyId=" + policyId + ", warning=" + warning + ")";
        }
    }
}
