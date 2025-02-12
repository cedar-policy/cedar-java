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

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.schema.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

/**
 * A partial authorization request consists of an optional principal, action, and optional resource as well as an
 * optional context mapping strings to Cedar values. When evaluating the request against a set of policies and entities,
 * the authorization engine determines if the policies allow for the given principal to perform the given action against
 * the given resource. If a decision can be reached, then the response will provide the decision. If a decision can't be
 * reached due to missing information Cedar will attempt to reduce the policies as much as possible and will return the
 * residual policies.
 *
 * <p>If the (optional) schema is provided, this will inform parsing the
 * `context` from JSON: for instance, it will allow `__entity` and `__extn`
 * escapes to be implicit, and it will error if attributes have the wrong types
 * (e.g., string instead of integer).
 * If the schema is provided and `enableRequestValidation` is true, then the
 * schema will also be used for request validation.
 */
@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class PartialAuthorizationRequest {

    /** EUID of the principal in the request. */
    public final Optional<EntityUID> principal;
    /** EUID of the action in the request. */
    public final Optional<EntityUID> action;
    /** EUID of the resource in the request. */
    public final Optional<EntityUID> resource;

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
     * Create a partial authorization request from the EUIDs and Context. We recommend using the {@link Builder}
     * for convenience.
     *
     * @param principal Principal's EUID.
     * @param action Action's EUID.
     * @param resource Resource's EUID.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     * @param enableRequestValidation Whether to use the schema for just
     * schema-based parsing of `context` (false) or also for request validation
     * (true). No effect if `schema` is not provided.
     */
    public PartialAuthorizationRequest(
            Optional<EntityUID> principal,
            Optional<EntityUID> action,
            Optional<EntityUID> resource,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema,
            boolean enableRequestValidation) {
        this.principal = principal;
        this.action = action;
        this.resource = resource;
        this.context = context;
        this.schema = schema;
        this.enableRequestValidation = enableRequestValidation;
    }

    /**
     * Creates a builder of partial authorization request.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder();
    }


    public static final class Builder {
        private Optional<EntityUID> principalEUID = Optional.empty();
        private Optional<EntityUID> actionEUID = Optional.empty();
        private Optional<EntityUID> resourceEUID = Optional.empty();
        private Optional<Map<String, Value>> context = Optional.empty();
        private Optional<Schema> schema = Optional.empty();
        private boolean enableRequestValidation = false;

        private Builder() {
        }

        /**
         * Set the principal.
         * @param principalEUID Principal's EUID.
         * @return The builder.
         */
        public Builder principal(EntityUID principalEUID) {
            this.principalEUID = Optional.of(principalEUID);
            return this;
        }

        /**
         * Set the action.
         * @param actionEUID Action's EUID.
         * @return The builder.
         */
        public Builder action(EntityUID actionEUID) {
            this.actionEUID = Optional.of(actionEUID);
            return this;
        }

        /**
         * Set the resource.
         * @param resourceEUID Resource's EUID.
         * @return The builder.
         */
        public Builder resource(EntityUID resourceEUID) {
            this.resourceEUID = Optional.of(resourceEUID);
            return this;
        }

        /**
         * Set the context.
         * @param context
         * @return The builder.
         */
        public Builder context(Map<String, Value> context) {
            this.context = Optional.of(ImmutableMap.copyOf(context));
            return this;
        }

        public Builder context(Context context) {
            this.context = Optional.of(ImmutableMap.copyOf(context.getContext()));
            return this;
        }

        /**
         * Set the context to be empty, not unknown
         * @return The builder.
         */
        public Builder emptyContext() {
            this.context = Optional.of(new HashMap<>());
            return this;
        }

        /**
         * Set the schema.
         * @param schema
         * @return The builder.
         */
        public Builder schema(Schema schema) {
            this.schema = Optional.of(schema);
            return this;
        }

        /**
         * Enable request validation.
         * @return The builder.
         */
        public Builder enableRequestValidation() {
            this.enableRequestValidation = true;
            return this;
        }

        /**
         * Build the partial authorization request.
         * @return The request.
         */
        public PartialAuthorizationRequest build() {
            return new PartialAuthorizationRequest(
                    principalEUID,
                    actionEUID,
                    resourceEUID,
                    context,
                    schema,
                    enableRequestValidation);
        }
    }
}
