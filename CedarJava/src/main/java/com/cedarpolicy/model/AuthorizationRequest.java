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
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An authorization request consists of a principal, action, and resource as well as a context mapping
 * strings to Cedar values. When evaluating the request against a slice, the authorization engine
 * determines if the policies allow for the given principal to perform the given action against the
 * given resource.
 *
 * <p>An optional schema can be provided, but will not be used for validation unless you call
 * validate(). The schema is provided to allow parsing Entities from JSON without escape sequences
 * (in general, you don't need to worry about this if you construct your entities via the EntityUid
 * class).
 */
public class AuthorizationRequest {
    /** euid of the principal in the request. */
    @JsonProperty("principal")
    public final Optional<String> principalEuid;
    /** euid of the action in the request. */
    @JsonProperty("action")
    public final String actionEuid;
    /** euid of the resource in the request. */
    @JsonProperty("resource")
    public final Optional<String> resourceEuid;

    /** Key/Value map representing the context of the request. */
    public final Optional<Map<String, Value>> context;

    /** JSON object representing the Schema. */
    public final Optional<Schema> schema;

    /**
     * Create an authorization request from the euids and Context.
     *
     * @param principalEuid Principal's euid.
     * @param actionEuid Action's euid.
     * @param resourceEuid Resource's euid.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     */
    public AuthorizationRequest(
            Optional<String> principalEuid,
            String actionEuid,
            Optional<String> resourceEuid,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema) {
        this.principalEuid = principalEuid;
        this.actionEuid = actionEuid;
        this.resourceEuid = resourceEuid;
        if (!context.isPresent() || context.get() == null) {
            this.context = Optional.empty();
        } else {
            this.context = Optional.of(new HashMap<>(context.get()));
        }
        this.schema = schema;
    }

    /**
     * Create a request in the empty context.
     *
     * @param principalEuid Principal's euid.
     * @param actionEuid Action's euid.
     * @param resourceEuid Resource's euid.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(String principalEuid, String actionEuid, String resourceEuid, Map<String, Value> context) {
        this(
                Optional.of(principalEuid),
                actionEuid,
                Optional.of(resourceEuid),
                Optional.of(context),
                Optional.empty());
    }

    /** Readable string representation. */
    @Override
    public String toString() {
        return "Request(" + principalEuid + ",\t" + actionEuid + ",\t" + resourceEuid + ")";
    }
}
