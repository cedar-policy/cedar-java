package com.cedarpolicy.model;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

/**
 * A partial authorization request consists of an optional principal, action, and optional resource as well as an
 * optional context mapping strings to Cedar values. When evaluating the request against a slice, the authorization
 * engine determines if the policies allow for the given principal to perform the given action against the given
 * resource. If a decision can be reached, then the response will provide the decision. If a decision can't be reached
 * due to missing information Cedar will attempt to reduce the policies as much as possible and will return the residual
 * policies.
 *
 * <p>If the (optional) schema is provided, this will inform parsing the
 * `context` from JSON: for instance, it will allow `__entity` and `__extn`
 * escapes to be implicit, and it will error if attributes have the wrong types
 * (e.g., string instead of integer).
 * If the schema is provided and `enable_request_validation` is true, then the
 * schema will also be used for request validation.
 */
@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class PartialAuthorizationRequest extends AuthorizationRequest {
    /**
     * Create a partial authorization request from the EUIDs and Context. We recommend using the {@link Builder}
     * for convenience.
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
    public PartialAuthorizationRequest(
            Optional<EntityUID> principalEUID,
            EntityUID actionEUID,
            Optional<EntityUID> resourceEUID,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema,
            boolean enableRequestValidation) {
        super(principalEUID, actionEUID, resourceEUID, context, schema, enableRequestValidation);
    }

    /**
     * Create a partial authorization request from Entity objects and Context. We recommend using the {@link Builder}
     * for convenience.
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
    public PartialAuthorizationRequest(
            Optional<Entity> principal,
            Entity action,
            Optional<Entity> resource,
            Optional<Map<String, Value>> context,
            Optional<Schema> schema,
            boolean enableRequestValidation) {
        super(principal, action, resource, context, schema, enableRequestValidation);
    }

    /**
     * Creates a builder of partial authorization request.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityUID principalEUID;
        private EntityUID actionEUID;
        private EntityUID resourceEUID;
        private Map<String, Value> context;
        private Schema schema;
        private boolean enableRequestValidation;

        private Builder() {}

        /**
         * Set the principal.
         * @param principalEUID Principal's EUID.
         * @return The builder.
         */
        public Builder principal(EntityUID principalEUID) {
            this.principalEUID = principalEUID;
            return this;
        }

        /**
         * Set the principal.
         * @param principal
         * @return The builder.
         */
        public Builder principal(Entity principal) {
            return principal(principal != null ? principal.getEUID() : null);
        }

        /**
         * Set the action.
         * @param actionEUID Action's EUID.
         * @return The builder.
         */
        public Builder action(EntityUID actionEUID) {
            this.actionEUID = actionEUID;
            return this;
        }

        /**
         * Set the action.
         * @param action
         * @return The builder.
         */
        public Builder action(Entity action) {
            return action(action != null ? action.getEUID() : null);
        }

        /**
         * Set the resource.
         * @param resourceEUID Resource's EUID.
         * @return The builder.
         */
        public Builder resource(EntityUID resourceEUID) {
            this.resourceEUID = resourceEUID;
            return this;
        }

        /**
         * Set the resource.
         * @param resource
         * @return The builder.
         */
        public Builder resource(Entity resource) {
            return resource(resource != null ? resource.getEUID() : null);
        }

        /**
         * Set the context.
         * @param context
         * @return The builder.
         */
        public Builder context(Map<String, Value> context) {
            this.context = ImmutableMap.copyOf(context);
            return this;
        }

        /**
         * Set the schema.
         * @param schema
         * @return The builder.
         */
        public Builder schema(Schema schema) {
            this.schema = schema;
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
            return new PartialAuthorizationRequest(Optional.ofNullable(principalEUID), actionEUID,
                    Optional.ofNullable(resourceEUID), Optional.ofNullable(context), Optional.ofNullable(schema),
                    enableRequestValidation);
        }
    }
}
