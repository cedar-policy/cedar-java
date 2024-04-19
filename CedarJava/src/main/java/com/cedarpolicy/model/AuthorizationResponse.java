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
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Optional;

/**
 * The result of processing an AuthorizationRequest.
 */
public final class AuthorizationResponse {
    /** Exactly one of `success` or `errors` should be None. */
    @JsonProperty("response")
    public final Optional<AuthorizationSuccessResponse> success;
    /** Exactly one of `success` or `errors` should be None. */
    @JsonProperty("errors")
    public final Optional<ImmutableList<String>> errors;
    /** Warnings can be produced regardless of whether we have a `success` or `errors`. */
    @JsonProperty("warnings")
    public final ImmutableList<String> warnings;

    /**
     * Exactly one of `success` or `errors` should be None.
     */
    @JsonCreator
    public AuthorizationResponse(
        @JsonProperty("response") Optional<AuthorizationSuccessResponse> success,
        @JsonProperty("errors") Optional<ArrayList<String>> errors,
        @JsonProperty("warnings") ArrayList<String> warnings
    ) {
        this.success = success;
        this.errors = errors.map((list) -> ImmutableList.copyOf(list));
        if (warnings == null) {
            this.warnings = ImmutableList.of(); // empty
        } else {
            this.warnings = ImmutableList.copyOf(warnings);
        }
    }
}
