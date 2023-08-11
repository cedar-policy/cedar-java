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

package com.cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Policies in the Cedar language. */
public class Policy {
    /** Policy string. */
    public final String policySrc;
    /** Policy id. */
    public final String policyId;

    /**
     * Creates a Cedar policy object.
     *
     * @param policy String containing the source code of a Cedar policy in the Cedar policy
     *     language.
     * @param policyId The id of this policy. Must be unique. Note: We may flip the order of the
     *     arguments here for idiomatic reasons.
     */
    public Policy(
            @JsonProperty("policySrc") String policy, @JsonProperty("policyId") String policyId)
            throws NullPointerException {

        if (policy == null) {
            throw new NullPointerException("Failed to construct policy from null string");
        }
        if (policyId == null) {
            throw new NullPointerException("Failed to construct policy with null id");
        }
        this.policySrc = policy;
        this.policyId = policyId;
    }

    @Override
    public String toString() {
        return "// Policy id: " + policyId + "\n" + policySrc;
    }
}
