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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.Objects;

/** Information passed to Cedar for validation. */
public final class ValidationQuery {
    private final Schema schema;
    private final Map<String, String> policySet;

    /**
     * Construct a validation query.
     *
     * @param schema Schema for the query
     * @param policySet Map of Policy ID to policy.
     */
    @SuppressFBWarnings
    public ValidationQuery(Schema schema, Map<String, String> policySet) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policySet == null) {
            throw new NullPointerException("policySet");
        }

        this.schema = schema;
        this.policySet = policySet;
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
        return this.policySet;
    }

    /** Test equality. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ValidationQuery)) {
            return false;
        }

        final ValidationQuery other = (ValidationQuery) o;
        return schema.equals(other.schema) && policySet.equals(other.policySet);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(schema, policySet);
    }

    /** Get readable string representation. */
    public String toString() {
        return "ValidationQuery(schema=" + schema + ", policySet=" + policySet + ")";
    }
}
