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

import java.util.Set;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.EntityValidationRequest;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationRequest;
import com.cedarpolicy.model.TypeAwarePartialAuthorizationResponse;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.PartialEntities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.LevelValidationRequest;

/**
 * Implementations of the AuthorizationEngine interface invoke Cedar to respond to an authorization
 * or validation request. For authorization, the input includes the relevant policies and entities for
 * Cedar to consider. Clients can provide these inputs in the form of Java objects constructed by the
 * API, which will be converted to JSON internally. It is the client’s responsibility to ensure that
 * all relevant policy information is given.
 *
 * <p>Note that Cedar does not have intrinsic limits on the sizes / number of policies. We could not
 * set such a limit as well as you, the user of the Cedar library. As such, it is your
 * responsibility to choose and enforce these limits.
 */
public interface AuthorizationEngine {
    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the <code>policySet</code> and
     * <code>entities</code> hierarchy given.
     *
     * @param request The request to evaluate
     * @param policySet The policy set to evaluate against
     * @param entities The entities to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    AuthorizationResponse isAuthorized(AuthorizationRequest request, PolicySet policySet, Set<Entity> entities) throws AuthException;

    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the <code>policySet</code> and
     * <code>entities</code> hierarchy given. Overloaded method to accept Entities object.
     *
     * @param request The request to evaluate
     * @param policySet The policy set to evaluate against
     * @param entities The entities to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    AuthorizationResponse isAuthorized(AuthorizationRequest request, PolicySet policySet, Entities entities) throws AuthException;

    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the <code>policySet</code> and
     * <code>entities</code> given. If information required to answer is missing, residual policies are returned.
     *
     * @param request The request to evaluate
     * @param policySet The policy set to evaluate against
     * @param entities The entities to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    @Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
    PartialAuthorizationResponse isAuthorizedPartial(PartialAuthorizationRequest request,
                                                     PolicySet policySet, Set<Entity> entities) throws AuthException;

    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the <code>policySet</code> and
     * <code>entities</code> given. If information required to answer is missing, residual policies are returned.
     * Overloaded method to accept Entities object.
     *
     * @param request The request to evaluate
     * @param policySet The policy set to evaluate against
     * @param entities The entities to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    @Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
    PartialAuthorizationResponse isAuthorizedPartial(PartialAuthorizationRequest request,
                                                     PolicySet policySet, Entities entities) throws AuthException;

    /**
     * Asks what the given {@link TypeAwarePartialAuthorizationRequest} evaluates to against the {@code policySet} and
     * the partially known {@code entities}, returning residual policies for whatever could not be decided.
     *
     * <p>The <em>policy set</em> is validated against the request's schema, which plain authorization does not do, so a
     * policy set that authorizes fine can fail here — an unconstrained scope means every condition must typecheck for
     * every entity type the action accepts, and an optional attribute needs a {@code has} guard.
     *
     * @param request   The request to evaluate.
     * @param policySet The policy set to evaluate against.
     * @param entities  The partially known entities to evaluate against.
     * @return Every residual, plus a concrete decision if one could be reached.
     * @throws AuthException On failure to make the authorization request.
     * @throws UnsupportedOperationException If the implementation does not support type-aware partial evaluation.
     */
    @Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
    default TypeAwarePartialAuthorizationResponse isAuthorizedTypeAwarePartial(
            TypeAwarePartialAuthorizationRequest request, PolicySet policySet,
            PartialEntities entities) throws AuthException {
        throw new UnsupportedOperationException(
                "isAuthorizedTypeAwarePartial is not implemented by " + getClass().getName());
    }

    /**
     * Overload for entities that are all fully known; they are lifted using the schema the request already carries.
     *
     * @param request   The request to evaluate.
     * @param policySet The policy set to evaluate against.
     * @param entities  The concrete entities to evaluate against.
     * @return Every residual, plus a concrete decision if one could be reached.
     * @throws AuthException On failure to make the authorization request, or if the entities do not check out against
     *                       the request's schema.
     */
    @Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
    default TypeAwarePartialAuthorizationResponse isAuthorizedTypeAwarePartial(
            TypeAwarePartialAuthorizationRequest request, PolicySet policySet,
            Entities entities) throws AuthException {
        return isAuthorizedTypeAwarePartial(request, policySet,
                new PartialEntities(entities, request.schema));
    }

    /**
     * Completes a type-aware partial authorization now that the previously unknown parts of the request and entities
     * are known, returning the decision the original policies would have reached.
     *
     * <p>Prefer {@link com.cedarpolicy.model.TypeAwarePartialAuthorizationSuccessResponse#reauthorize}, which supplies
     * the first three arguments from the response itself so they cannot disagree with the residuals you inspected. This
     * form exists for that method to delegate to, and for a caller who has the partial inputs but not the response.
     *
     * @param request         The partial request the residuals were computed from.
     * @param policySet       The policy set the residuals were computed from.
     * @param partialEntities The partially known entities the residuals were computed from.
     * @param concreteRequest The same request with its unknowns filled in.
     * @param entities        The same entities with their unknowns filled in. Cedar validates both against the partial
     *     inputs and rejects a mismatch; see
     *     {@link com.cedarpolicy.model.TypeAwarePartialAuthorizationSuccessResponse#reauthorize} for what it compares.
     * @return The authorization decision, or a failure response if the completed inputs are inconsistent with the
     *     partial ones.
     * @throws AuthException On failure to make the authorization request.
     */
    @Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
    default AuthorizationResponse reauthorize(TypeAwarePartialAuthorizationRequest request, PolicySet policySet,
            PartialEntities partialEntities, AuthorizationRequest concreteRequest, Entities entities)
            throws AuthException {
        throw new UnsupportedOperationException("reauthorize is not implemented by " + getClass().getName());
    }

    /**
     * Asks whether the policies in the given {@link ValidationRequest} <code>q</code> are correct
     * when validated against the schema it describes.
     *
     * @param request The request containing the policies to validate and the schema to validate them
     *     against.
     * @return A {@link ValidationResponse} describing any validation errors found in the policies.
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException if any internal errors occurred while validating the policies.
     */
    ValidationResponse validate(ValidationRequest request) throws AuthException;

    /**
     * Asks whether the policies in the given {@link LevelValidationRequest} <code>q</code> are correct
     * when validated against the schema it describes. If validation passes, run level validation (RFC 76)
     *
     * @param request The request containing the policies to validate, the schema to validate them
     *     against and maximum dereferencing level.
     * @return A {@link ValidationResponse} describing any validation errors found in the policies.
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException if any internal errors occurred while validating the policies.
     */
    ValidationResponse validateWithLevel(LevelValidationRequest request) throws AuthException;

    /**
     * Asks whether the entities in the given {@link EntityValidationRequest} <code>q</code> are correct
     * when validated against the schema it describes.
     *
     * @param request The request containing the entities to validate and the schema to validate them
     *    against.
     * @throws BadRequestException if any errors were found in the syntax of the entities.
     * @throws AuthException if any internal errors occurred while validating the entities.
     */
    void validateEntities(EntityValidationRequest request) throws AuthException;

    /**
     * Get the Cedar language major version (e.g., "1.2") used by this CedarJava library.
     *
     * @return The Cedar language major version supported
     */
    static String getCedarLangVersion() {
        return "4.0";
    }
}
