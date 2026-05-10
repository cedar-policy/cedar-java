package com.cedarpolicy.model;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
public class PartialAuthorizationResponse {
    /**
     * Is this a success or a failure response
     */
    @JsonProperty("type")
    public final SuccessOrFailure type;
    /**
     * This will be present if and only if `type` is `Success`.
     */
    @JsonProperty("response")
    public final Optional<PartialAuthorizationSuccessResponse> success;
    /**
     * This will be present if and only if `type` is `Failure`.
     */
    @JsonProperty("errors")
    public final Optional<List<DetailedError>> errors;
    /**
     * Warnings can be produced regardless of whether we have a `Success` or `Failure`.
     */
    @JsonProperty("warnings")
    public final List<String> warnings;

    /**
     * If `type` is `Success`, `success` should be present and `errors` empty.
     * If `type` is `Failure`, `errors` should be present and `success` empty.
     */
    @JsonCreator
    public PartialAuthorizationResponse(
        @JsonProperty("type") SuccessOrFailure type,
        @JsonProperty("response") Optional<PartialAuthorizationSuccessResponse> success,
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
        final String warningsString = warnings.isEmpty() ? "" : "\nwith warnings: " + warnings;
        if (type == SuccessOrFailure.Success) {
            return "SUCCESS: " + success.get() + warningsString;
        } else {
            return "FAILURE: " + errors.get() + warningsString;
        }
    }

    public enum SuccessOrFailure {
        @JsonProperty("residuals")
        Success,
        @JsonProperty("failure")
        Failure,
    }
}
