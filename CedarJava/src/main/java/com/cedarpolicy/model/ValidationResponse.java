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
import java.util.Objects;

/** Result of a validation request. */
public final class ValidationResponse {
    private final List<ValidationError> errors;
    private final List<ValidationWarning> warnings;

    /**
     * Construct a validation response.
     *
     * @param errors Errors.
     */
    @JsonCreator
    @SuppressFBWarnings
    public ValidationResponse(@JsonProperty("validation_errors") List<ValidationError> errors, @JsonProperty("validation_warnings") List<ValidationWarning> warnings) {
        if (errors == null) {
            throw new NullPointerException("`errors` is null");
        }
        if (warnings == null) {
            throw new NullPointerException("`warnings` is null");
        }

        this.errors = errors;
        this.warnings = warnings;
    }

    /**
     * Get errors from a validation response.
     *
     * @return The errors.
     */
    @SuppressFBWarnings
    public List<ValidationError> getErrors() {
        return this.errors;
    }

    /** Test equals. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ValidationResponse)) {
            return false;
        } else {
            return errors.equals(((ValidationResponse) o).errors);
        }
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(errors);
    }

    /** Readable string representation. */
    public String toString() {
        return "ValidationResponse(validation_errors=" + this.getErrors() + ")";
    }

    /** Error for a specific policy. */
    public static final class ValidationError {
        private final String policyId;
        private final String error;

        /**
         * Create error from JSON.
         *
         * @param policyId Policy ID to which error applies.
         * @param error The Error.
         */
        @JsonCreator
        public ValidationError(@JsonProperty("policyId") String policyId, @JsonProperty("error") String error) {
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
        private final String warning;

        /**
         * Create warning from JSON.
         *
         * @param policyId Policy ID to which warning applies.
         * @param warning The Warning.
         */
        @JsonCreator
        public ValidationWarning(@JsonProperty("policyId") String policyId, @JsonProperty("warning") String warning) {
            this.policyId = policyId;
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
