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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of a validation request. */
public final class ValidationResponse {
    /** Is this a success or a failure response */
    @JsonProperty("type")
    public final SuccessOrFailure type;
    /** This will be present if and only if `type` is `Success`. */
    public final Optional<ValidationSuccessResponse> success;
    /**
     * This will be present if and only if `type` is `Failure`.
     *
     * These errors are not validation errors (those would be
     * reported in `success`), but rather higher-level errors, like
     * a failure to parse or to call the validator.
     */
    public final Optional<List<DetailedError>> errors;
    /**
     * Other warnings not associated with particular policies.
     * For instance, warnings about your schema itself.
     * These warnings can be produced regardless of whether `type` is
     * `Success` or `Failure`.
    */
    public final List<DetailedError> warnings;

    public static final class ValidationSuccessResponse {
        /** Validation errors associated with particular policies. */
        @JsonProperty("validationErrors")
        public final List<ValidationError> validationErrors;
        /** Validation warnings associated with particular policies. */
        @JsonProperty("validationWarnings")
        public final List<ValidationError> validationWarnings;

        @JsonCreator
        public ValidationSuccessResponse(
            @JsonProperty("validationErrors") Optional<List<ValidationError>> validationErrors,
            @JsonProperty("validationWarnings") Optional<List<ValidationError>> validationWarnings) {
            if (validationErrors.isPresent()) {
                this.validationErrors = List.copyOf(validationErrors.get());
            } else {
                this.validationErrors = List.of(); // empty
            }
            if (validationWarnings.isPresent()) {
                this.validationWarnings = List.copyOf(validationWarnings.get());
            } else {
                this.validationWarnings = List.of(); // empty
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
        @JsonProperty("validationErrors") Optional<List<ValidationError>> validationErrors,
        @JsonProperty("validationWarnings") Optional<List<ValidationError>> validationWarnings,
        @JsonProperty("errors") Optional<List<DetailedError>> errors,
        @JsonProperty("warnings") @JsonAlias("otherWarnings") Optional<List<DetailedError>> warnings) {
        this.type = type;
        this.errors = errors.map((list) -> List.copyOf(list));
        if (type == SuccessOrFailure.Success) {
            this.success = Optional.of(new ValidationSuccessResponse(validationErrors, validationWarnings));
        } else {
            this.success = Optional.empty();
        }
        if (warnings.isPresent()) {
            this.warnings = List.copyOf(warnings.get());
        } else {
            this.warnings = List.of(); // empty
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
        if (success.isPresent()) {
            return success.get().validationErrors.isEmpty();
        } else {
            // higher-level errors are present
            return false;
        }
    }

    /** Readable string representation. */
    public String toString() {
        if (success.isPresent()) {
            return "ValidationResponse(validationErrors = " + success.get().validationErrors + ", validationWarnings = "
                    + success.get().validationWarnings + ")";
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
