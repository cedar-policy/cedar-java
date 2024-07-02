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

package com.cedarpolicy;

import static com.cedarpolicy.CedarJson.objectReader;
import static com.cedarpolicy.CedarJson.objectWriter;

import java.io.IOException;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.*;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.slice.Slice;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/** An authorization engine that is compiled in process. Communicated with via JNI. */
public final class BasicAuthorizationEngine implements AuthorizationEngine {
    static {
        LibraryLoader.loadLibrary();
    }

    /** Construct a basic authorization engine. */
    public BasicAuthorizationEngine() {
    }

    @Override
    public AuthorizationResponse isAuthorized(com.cedarpolicy.model.AuthorizationRequest q,
                                              PolicySet policySet, Set<Entity> entities) throws AuthException {
        final AuthorizationRequest request = new AuthorizationRequest(q, policySet, entities);
        return call("AuthorizationOperation", AuthorizationResponse.class, request);
    }

    @Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
    @Override
    public PartialAuthorizationResponse isAuthorizedPartial(com.cedarpolicy.model.PartialAuthorizationRequest q,
                                                            PolicySet policySet, Set<Entity> entities) throws AuthException {
        try {
            final PartialAuthorizationRequest request = new PartialAuthorizationRequest(q, policySet, entities);
            return call("AuthorizationPartialOperation", PartialAuthorizationResponse.class, request);
        } catch (InternalException e) {
            if (e.getMessage().contains("AuthorizationPartialOperation")) {
                throw new MissingExperimentalFeatureException(ExperimentalFeature.PARTIAL_EVALUATION);
            } else {
                throw e;
            }
        }
    }

    @Override
    public ValidationResponse validate(ValidationRequest q) throws AuthException {
        return call("ValidateOperation", ValidationResponse.class, q);
    }

    private static <REQ, RESP> RESP call(String operation, Class<RESP> responseClass, REQ request)
            throws AuthException {
        try {
            final String cedarJNIVersion = getCedarJNIVersion();
            if (!cedarJNIVersion.equals(AuthorizationEngine.getCedarLangVersion())) {
                throw new AuthException(
                        "Error, Java Cedar Language version is "
                                + AuthorizationEngine.getCedarLangVersion()
                                + " but JNI Cedar Language version is "
                                + cedarJNIVersion);
            }
            // Convert the request POJO to a JSON string
            final String fullRequest = objectWriter().writeValueAsString(request);

            final String response = callCedarJNI(operation, fullRequest);

            final JsonNode responseNode = objectReader().readTree(response);
            return objectReader().readValue(responseNode, responseClass);
        } catch (JsonProcessingException e) {
            throw new AuthException("JSON Serialization Error", e);
        } catch (IllegalArgumentException e) {
            throw new AuthException("Authorization error caused by illegal argument exception.", e);
        } catch (IOException e) {
            throw new AuthException("JSON Deserialization Error", e);
        }
    }

    private static class AuthorizationRequest extends com.cedarpolicy.model.AuthorizationRequest {
        @JsonProperty private final Slice slice;

        AuthorizationRequest(com.cedarpolicy.model.AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
            super(
                    request.principalEUID,
                    request.actionEUID,
                    request.resourceEUID,
                    request.context,
                    request.schema,
                    request.enableRequestValidation);
            this.slice = new BasicSlice(policySet.policies, entities, policySet.templates, policySet.templateInstantiations);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private static final class PartialAuthorizationRequest {
        @JsonProperty public final Slice slice;
        @JsonProperty public final com.cedarpolicy.model.PartialAuthorizationRequest request;

        PartialAuthorizationRequest(com.cedarpolicy.model.PartialAuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
            this.request = request;
            this.slice = new BasicSlice(policySet.policies, entities, policySet.templates, policySet.templateInstantiations);
        }
    }

    /**
     * Call out to the Rust implementation.
     *
     * @param call Call type ("AuthorizationOperation" or "ValidateOperation").
     * @param input Request input in JSON format as a String
     * @return The response (permit / deny for authorization, valid / invalid for validation)
     */
    private static native String callCedarJNI(String call, String input);

    /**
     * Get the Cedar language major version supported by the JNI (e.g., "1.2")
     *
     * @return The Cedar language version supported by the JNI
     */
    private static native String getCedarJNIVersion();
}
