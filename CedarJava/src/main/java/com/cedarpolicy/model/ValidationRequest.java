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

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.policy.Policy;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Information passed to Cedar for validation. */
public final class ValidationRequest {
    private final Schema schema;
    @JsonProperty("policies")
    private final Map<String, String> policies;

    /**
     * Construct a validation request.
     *
     * @param schema Schema for the request
     * @param policies Map of Policy ID to policy.
     */
    @SuppressFBWarnings
    public ValidationRequest(Schema schema, Map<String, String> policies) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policies == null) {
            throw new NullPointerException("policies");
        }

        this.schema = schema;
        this.policies = policies;
    }

    public ValidationRequest(Schema schema, Set<Policy> policies) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policies == null) {
            throw new NullPointerException("policies");
        }

        this.schema = schema;
        this.policies = new HashMap<>();
        for (Policy p : policies) {
            this.policies.put(p.policyID, p.policySrc);
        }
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
     * @return Map of policy ID to policy.
     */
    @SuppressFBWarnings
    public Map<String, String> getPolicySet() {
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
