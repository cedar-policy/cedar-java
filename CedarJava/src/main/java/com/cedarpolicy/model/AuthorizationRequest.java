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

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An authorization request consists of a principal, action, and resource as well as a context mapping
 * strings to Cedar values. When evaluating the request against a set of policies and entities, the authorization engine
 * determines if the policies allow for the given principal to perform the given action against the
 * given resource.
 *
 * <p>If the (optional) schema is provided, this will inform parsing the
 * `context` from JSON: for instance, it will allow `__entity` and `__extn`
 * escapes to be implicit, and it will error if attributes have the wrong types
 * (e.g., string instead of integer).
 * If the schema is provided and `enableRequestValidation` is true, then the
 * schema will also be used for request validation.
 */
public class AuthorizationRequest {
    /** EUID of the principal in the request. */
    @JsonProperty("principal")
    public final EntityUID principalEUID;
    /** EUID of the action in the request. */
    @JsonProperty("action")
    public final EntityUID actionEUID;
    /** EUID of the resource in the request. */
    @JsonProperty("resource")
    public final EntityUID resourceEUID;

    /** Key/Value map representing the context of the request. */
    public final Optional<Map<String, Value>> context;

    /** JSON object representing the Schema. Used for schema-based parsing of
     * `context`, and also (if `enableRequestValidation` is `true`) for
     * request validation. */
    public final Optional<Schema> schema;

    /** If this is `true` and a schema is provided, perform request validation.
     * If this is `false`, the schema will only be used for schema-based parsing
     * of `context`, and not for request validation.
     * If a schema is not provided, this option has no effect. */
    @JsonProperty("validateRequest")
    public final boolean enableRequestValidation;

    /**
     * Create an authorization request from the EUIDs and Context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     * @param enableRequestValidation Whether to use the schema for just
     * schema-based parsing of `context` (false) or also for request validation
     * (true). No effect if `schema` is not provided.
     */
    public AuthorizationRequest(
            EntityUID principalEUID,
            EntityUID actionEUID,
            EntityUID resourceEUID,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema,
            boolean enableRequestValidation) {
        this.principalEUID = principalEUID;
        this.actionEUID = actionEUID;
        this.resourceEUID = resourceEUID;
        if (!context.isPresent() || context.get() == null) {
            this.context = Optional.empty();
        } else {
            this.context = Optional.of(new HashMap<>(context.get()));
        }
        this.schema = schema;
        this.enableRequestValidation = enableRequestValidation;
    }

    /**
     * Create an authorization request from the EUIDs and Context.
     * Constructor overloading to support Context object while preserving backward compatability.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Context object.
     * @param schema Schema (optional).
     * @param enableRequestValidation Whether to use the schema for just
     * schema-based parsing of `context` (false) or also for request validation
     * (true). No effect if `schema` is not provided.
     */
    public AuthorizationRequest(
        EntityUID principalEUID,
        EntityUID actionEUID,
        EntityUID resourceEUID,
        Context context,
        Optional<Schema> schema,
        boolean enableRequestValidation) {
    this.principalEUID = principalEUID;
    this.actionEUID = actionEUID;
    this.resourceEUID = resourceEUID;
    this.context = Optional.of(context.getContext());
    this.schema = schema;
    this.enableRequestValidation = enableRequestValidation;
}

    /**
     * Create a request without a schema.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(EntityUID principalEUID, EntityUID actionEUID, EntityUID resourceEUID, Map<String, Value> context) {
        this(
                principalEUID,
                actionEUID,
                resourceEUID,
                Optional.of(context),
                Optional.empty(),
                false);
    }

    /**
     * Create a request without a schema.
     * Constructor overloading to support Context object while preserving backward compatability.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(EntityUID principalEUID, EntityUID actionEUID, EntityUID resourceEUID, Context context) {
        this(
                principalEUID,
                actionEUID,
                resourceEUID,
                context,
                Optional.empty(),
                false);
    }

    /**
     * Create a request without a schema, using Entity objects for principal/action/resource.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(Entity principalEUID, Entity actionEUID, Entity resourceEUID, Map<String, Value> context) {
        this(
                principalEUID.getEUID(),
                actionEUID.getEUID(),
                resourceEUID.getEUID(),
                context);
    }

    /**
     * Create a request without a schema, using Entity objects for principal/action/resource.
     * Constructor overloading to support Context object while preserving backward compatability.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     */
    public AuthorizationRequest(Entity principalEUID, Entity actionEUID, Entity resourceEUID, Context context) {
        this(
                principalEUID.getEUID(),
                actionEUID.getEUID(),
                resourceEUID.getEUID(),
                context);
    }


    /**
     * Create a request from Entity objects and Context.
     *
     * @param principal
     * @param action
     * @param resource
     * @param context
     * @param schema
     * @param enableRequestValidation Whether to use the schema for just
     * schema-based parsing of `context` (false) or also for request validation
     * (true). No effect if `schema` is not provided.
     */

    public AuthorizationRequest(Entity principal, Entity action, Entity resource,
                                Optional<Map<String, Value>> context, Optional<Schema> schema, boolean enableRequestValidation) {
        this(
            principal.getEUID(),
            action.getEUID(),
            resource.getEUID(),
            context,
            schema,
            enableRequestValidation
        );
    }

    /**
     * Create a request from Entity objects and Context.
     * Constructor overloading to support Context object while preserving backward compatability.
     *
     * @param principal
     * @param action
     * @param resource
     * @param context
     * @param schema
     * @param enableRequestValidation Whether to use the schema for just
     * schema-based parsing of `context` (false) or also for request validation
     * (true). No effect if `schema` is not provided.
     */

    public AuthorizationRequest(Entity principal, Entity action, Entity resource,
                                Context context, Optional<Schema> schema, boolean enableRequestValidation) {
        this(
            principal.getEUID(),
            action.getEUID(),
            resource.getEUID(),
            context,
            schema,
            enableRequestValidation
        );
    }

    /** Readable string representation. */
    @Override
    public String toString() {
        return "Request(" + principalEUID + ", " + actionEUID + ", " + resourceEUID + ", " + context + ")";
    }
}
