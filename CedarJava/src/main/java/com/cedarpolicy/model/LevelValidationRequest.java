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

/** Information passed to Cedar for level validation. */
public final class LevelValidationRequest {
    private final Schema schema;
    private final PolicySet policies;
    private final long maxDerefLevel; // Must be non-negative (>=0)  

    /**
     * Construct a validation request.
     *
     * @param schema        Schema for the request
     * @param policies      Map of Policy ID to policy
     * @param maxDerefLevel Maximum level of dereferencing allowed for validation. Must be non-negative (>=0)
     */
    @SuppressFBWarnings
    public LevelValidationRequest(Schema schema, PolicySet policies, long maxDerefLevel) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policies == null) {
            throw new NullPointerException("policies");
        }

        if (maxDerefLevel < 0) {
            throw new IllegalArgumentException("maxDerefLevel must be non-negative");
        }

        this.schema = schema;
        this.policies = policies;
        this.maxDerefLevel = maxDerefLevel;
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
    @JsonProperty("policies")
    public PolicySet getPolicySet() {
        return this.policies;
    }

    /**
     * Get the maximum deref level.
     * 
     * @return The maximum deref level value for validation
     */
    public long getMaxDerefLevel() {
        return this.maxDerefLevel;
    }

    /** Test equality. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof LevelValidationRequest)) {
            return false;
        }

        final LevelValidationRequest other = (LevelValidationRequest) o;
        return schema.equals(other.schema) && policies.equals(other.policies) && maxDerefLevel == other.maxDerefLevel;
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(schema, policies, maxDerefLevel);
    }

    /** Get readable string representation. */
    public String toString() {
        return "ValidationRequest(schema=" + schema + ", policies=" + policies + ", maxDerefLevel=" + maxDerefLevel
                + ")";
    }
}
