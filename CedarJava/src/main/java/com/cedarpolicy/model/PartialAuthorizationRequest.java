package com.cedarpolicy.model;

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
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class PartialAuthorizationRequest extends AuthorizationRequest {
    public PartialAuthorizationRequest(Optional<EntityUID> principalEUID, EntityUID actionEUID, Optional<EntityUID> resourceEUID, Optional<Map<String, Value>> context, Optional<Schema> schema, boolean enable_request_validation) {
        super(principalEUID, actionEUID, resourceEUID, context, schema, enable_request_validation);
    }

    public PartialAuthorizationRequest(Optional<Entity> principal, Entity action, Optional<Entity> resource, Optional<Map<String, Value>> context, Optional<Schema> schema, boolean enable_request_validation) {
        super(principal, action, resource, context, schema, enable_request_validation);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityUID principalEUID;
        private EntityUID actionEUID;
        private EntityUID resourceEUID;
        private Map<String, Value> context;
        private Schema schema;
        private boolean enable_request_validation;

        private Builder() {}

        public Builder principal(EntityUID principalEUID) {
            this.principalEUID = principalEUID;
            return this;
        }

        public Builder principal(Entity principal) {
            return principal(principal != null ? principal.getEUID() : null);
        }

        public Builder action(EntityUID actionEUID) {
            this.actionEUID = actionEUID;
            return this;
        }

        public Builder action(Entity action) {
            return action(action != null ? action.getEUID() : null);
        }

        public Builder resource(EntityUID resourceEUID) {
            this.resourceEUID = resourceEUID;
            return this;
        }

        public Builder resource(Entity resource) {
            return resource(resource != null ? resource.getEUID() : null);
        }

        public Builder context(Map<String, Value> context) {
            this.context = ImmutableMap.copyOf(context);
            return this;
        }

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        public Builder enableRequestValidation() {
            this.enable_request_validation = true;
            return this;
        }

        public PartialAuthorizationRequest build() {
            return new PartialAuthorizationRequest(Optional.ofNullable(principalEUID), actionEUID,
                    Optional.ofNullable(resourceEUID), Optional.ofNullable(context), Optional.ofNullable(schema),
                    enable_request_validation);
        }
    }
}
