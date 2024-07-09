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

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.policy.PolicySet;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Objects;

/** Information passed to Cedar for validation. */
public final class ValidationRequest {
    private final Schema schema;
    @JsonProperty("policies")
    private final PolicySet policies;

    /**
     * Construct a validation request.
     *
     * @param schema Schema for the request
     * @param policies Map of Policy ID to policy.
     */
    @SuppressFBWarnings
    public ValidationRequest(Schema schema, PolicySet policies) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policies == null) {
            throw new NullPointerException("policies");
        }

        this.schema = schema;
        this.policies = policies;
    }

    /**
     * Get the schema.
     *
     * @return The schema.
     */
    public Schema getSchema() {
        return this.schema;
    }

    /**
     * Get the policy set.
     *
     * @return A `PolicySet` object
     */
    @SuppressFBWarnings
    public PolicySet getPolicySet() {
        return this.policies;
    }

    /** Test equality. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ValidationRequest)) {
            return false;
        }

        final ValidationRequest other = (ValidationRequest) o;
        return schema.equals(other.schema) && policies.equals(other.policies);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(schema, policies);
    }

    /** Get readable string representation. */
    public String toString() {
        return "ValidationRequest(schema=" + schema + ", policies=" + policies + ")";
    }
}
