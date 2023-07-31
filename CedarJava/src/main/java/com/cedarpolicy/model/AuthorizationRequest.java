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
 * An authorization query consists of a principal, action, and resource as well as a context mapping
 * strings to Cedar values. When evaluating the query against a slice, the authorization engine
 * determines if the policies allow for the given principal to perform the given action against the
 * given resource.
 *
 * <p>An optional schema can be provided, but will not be used for validation unless you call
 * validate(). The schema is provided to allow parsing Entities from JSON without escape sequences
 * (in general, you don't need to worry about this if you construct your entities via the EntityUID
 * class).
 */
public class AuthorizationRequest {
    /** EUID of the principal in the query. */
    @JsonProperty("principal")
    public final Optional<String> principalEUID;
    /** EUID of the action in the query. */
    @JsonProperty("action")
    public final String actionEUID;
    /** EUID of the resource in the query. */
    @JsonProperty("resource")
    public final Optional<String> resourceEUID;

    /** Key/Value map representing the context of the query. */
    public final Optional<Map<String, Value>> context;

    /** JSON object representing the Schema. */
    public final Optional<Schema> schema;

    /**
     * Create an authorization query from the EUIDs and Context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     */
    public AuthorizationRequest(
            Optional<String> principalEUID,
            String actionEUID,
            Optional<String> resourceEUID,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema) {
        this.principalEUID = principalEUID;
        this.actionEUID = actionEUID;
        this.resourceEUID = resourceEUID;
        if (!context.isPresent() || context.get() == null) {
            this.context = Optional.empty();
        } else {
            this.context = Optional.of(new HashMap<>(context.get()));
        }
        this.schema = schema;
    }

    /**
     * Create a query in the empty context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(String principalEUID, String actionEUID, String resourceEUID, Map<String, Value> context) {
        this(
                Optional.of(principalEUID),
                actionEUID,
                Optional.of(resourceEUID),
                Optional.of(context),
                Optional.empty());
    }

    /** Readable string representation. */
    @Override
    public String toString() {
        return "Query(" + principalEUID + ",\t" + actionEUID + ",\t" + resourceEUID + ")";
    }
}
