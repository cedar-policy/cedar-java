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

package com.cedarpolicy;

import static com.cedarpolicy.CedarJson.objectReader;
import static com.cedarpolicy.CedarJson.objectWriter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.EntityValidationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.policy.PolicySet;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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

    /**
     * Overloaded method to accept Entities object
     */
    @Override
    public AuthorizationResponse isAuthorized(com.cedarpolicy.model.AuthorizationRequest q,
                                              PolicySet policySet, Entities entities) throws AuthException {
        return isAuthorized(q, policySet, entities.getEntities());
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

    /**
     * Overloaded method to accept Entities object
     */
    @Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
    @Override
    public PartialAuthorizationResponse isAuthorizedPartial(com.cedarpolicy.model.PartialAuthorizationRequest q,
                                                            PolicySet policySet, Entities entities) throws AuthException {
        return isAuthorizedPartial(q, policySet, entities.getEntities());
    }

    @Override
    public ValidationResponse validate(ValidationRequest q) throws AuthException {
        return call("ValidateOperation", ValidationResponse.class, q);
    }

    @Override
    public void validateEntities(EntityValidationRequest q) throws AuthException {
        EntityValidationResponse entityValidationResponse = call("ValidateEntities", EntityValidationResponse.class, q);
        if (!entityValidationResponse.success) {
            if (entityValidationResponse.isInternal) {
                throw new InternalException(entityValidationResponse.errors.toArray(new String[0]));
            } else {
                throw new BadRequestException(entityValidationResponse.errors.toArray(new String[0]));
            }
        }
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

    /**
     * The result of processing an EntityValidationRequest.
     */
    @JsonIgnoreProperties({"result"})  // Ignore only the 'result' field
    private static final class EntityValidationResponse {

        /** A string that indicates if the operation was successful.*/
        private final boolean success;

        /** A boolean flag that indicates whether the error is internal.*/
        private final boolean isInternal;

        /** A list of error messages encountered during the operation.*/
        private final List<String> errors;

        /**
         * Parameterized constructor for initializing all fields of EntityValidationResponse.
         *
         * @param success    A boolean indicating success status.
         * @param isInternal A boolean indicating if the error is internal.
         * @param errors     A list of error messages.
         */
        @JsonCreator
        @SuppressFBWarnings
        EntityValidationResponse(
                @JsonProperty("success") boolean success,
                @JsonProperty("isInternal") boolean isInternal,
                @JsonProperty("errors") List<String> errors) {
            this.success = success;
            this.isInternal = isInternal;
            this.errors = errors;
        }
    }

    private static class AuthorizationRequest extends com.cedarpolicy.model.AuthorizationRequest {
        @JsonProperty private final PolicySet policies;
        @JsonProperty private final Set<Entity> entities;

        AuthorizationRequest(com.cedarpolicy.model.AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
            super(
                    request.principalEUID,
                    request.actionEUID,
                    request.resourceEUID,
                    request.context,
                    request.schema,
                    request.enableRequestValidation);
            this.policies = policySet;
            this.entities = entities;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private static final class PartialAuthorizationRequest extends com.cedarpolicy.model.PartialAuthorizationRequest {
        @JsonProperty private final PolicySet policies;
        @JsonProperty private final Set<Entity> entities;

        PartialAuthorizationRequest(com.cedarpolicy.model.PartialAuthorizationRequest request, PolicySet policySet, Set<Entity> entities) {
            super(
                request.principal,
                request.action,
                request.resource,
                request.context,
                request.schema,
                request.enableRequestValidation);
            this.policies = policySet;
            this.entities = entities;
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
