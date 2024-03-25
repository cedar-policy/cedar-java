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

import com.cedarpolicy.model.*;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.slice.Slice;

/**
 * Implementations of the AuthorizationEngine interface invoke Cedar to respond to an authorization
 * or validation request. For authorization, the input includes the relevant slice of the policy for
 * Cedar to consider. Clients can provide a slice in the form of Java objects constructed by the
 * API, which will be converted to JSON internally. It is the client’s responsibility to ensure that
 * all relevant policy information is within the slice.
 *
 * <p>Note that Cedar does not have intrinsic limits on the sizes / number of policies. We could not
 * set such a limit as well as you, the user of the Cedar library. As such, it is your
 * responsibility to choose and enforce these limits.
 */
public interface AuthorizationEngine {
    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the policies and
     * entity hierarchy given in the <code>slice</code>.
     *
     * @param request The request to evaluate
     * @param slice The slice to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    AuthorizationResponse isAuthorized(AuthorizationRequest request, Slice slice) throws AuthException;

    /**
     * Asks whether the given AuthorizationRequest <code>q</code> is approved by the policies and
     * entity hierarchy given in the <code>slice</code>. If information required to answer is missing residual
     * policies are returned.
     *
     * @param request The request to evaluate
     * @param slice The slice to evaluate against
     * @return The result of the request evaluation
     * @throws BadRequestException if any errors were found in the syntax of the policies.
     * @throws AuthException On failure to make the authorization request. Note that errors inside the
     *     authorization engine are included in the <code>errors</code> field on the
     *     AuthorizationResponse.
     */
    @Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
    PartialAuthorizationResponse isAuthorizedPartial(PartialAuthorizationRequest request, Slice slice) throws AuthException;

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
     * Get the Cedar language major version (e.g., "1.2") used by this CedarJava library.
     *
     * @return The Cedar language major version supported
     */
    static String getCedarLangVersion() {
        return "3.1";
    }
}
