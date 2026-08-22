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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The result of processing an AuthorizationRequest.
 */
public final class AuthorizationResponse {
    /** Is this a success or a failure response */
    @JsonProperty("type")
    public final SuccessOrFailure type;
    /** This will be present if and only if `type` is `Success`. */
    @JsonProperty("response")
    public final Optional<AuthorizationSuccessResponse> success;
    /** This will be present if and only if `type` is `Failure`. */
    @JsonProperty("errors")
    public final Optional<List<DetailedError>> errors;
    /** Warnings can be produced regardless of whether we have a `Success` or `Failure`. */
    @JsonProperty("warnings")
    public final List<String> warnings;

    /**
     * If `type` is `Success`, `success` should be present and `errors` empty.
     * If `type` is `Failure`, `errors` should be present and `success` empty.
     */
    @JsonCreator
    public AuthorizationResponse(
        @JsonProperty("type") SuccessOrFailure type,
        @JsonProperty("response") Optional<AuthorizationSuccessResponse> success,
        @JsonProperty("errors") Optional<ArrayList<DetailedError>> errors,
        @JsonProperty("warnings") ArrayList<String> warnings
    ) {
        this.type = type;
        this.success = success;
        this.errors = errors.map((list) -> List.copyOf(list));
        if (warnings == null) {
            this.warnings = List.of(); // empty
        } else {
            this.warnings = List.copyOf(warnings);
        }
    }

    @Override
    public String toString() {
        final String warningsString = warnings.isEmpty() ? "" : "\nwith warnings: " + warnings.toString();
        if (type == SuccessOrFailure.Success) {
            return "SUCCESS: " + success.get().toString() + warningsString;
        } else {
            return "FAILURE: " + errors.get().toString() + warningsString;
        }
    }

    public enum SuccessOrFailure {
        @JsonProperty("success")
        Success,
        @JsonProperty("failure")
        Failure,
    }
}
