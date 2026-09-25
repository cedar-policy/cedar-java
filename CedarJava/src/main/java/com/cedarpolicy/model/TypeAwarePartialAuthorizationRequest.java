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
import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PartialEntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.cedarpolicy.CedarJson.objectWriter;

/**
 * A type-aware partial authorization request. The principal and resource may have an unknown id, but their types are
 * always known, and the action must be concrete. Cedar validates the request against the schema, which is therefore
 * required rather than optional.
 *
 * <p>The context is all-or-nothing: absent means the whole context is unknown, whereas {@link Builder#emptyContext}
 * states that the context is known to be empty. Individual context keys cannot be left unknown.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class TypeAwarePartialAuthorizationRequest {

    static {
        LibraryLoader.loadLibrary();
    }

    /** EUID of the principal in the request, whose id may be unknown. */
    public final PartialEntityUID principal;

    /**
     * EUID of the action in the request. Note this serializes with the {@code __entity} escape, because
     * {@link EntityUID} does, whereas the principal and resource serialize as a bare type/id pair. Cedar accepts both.
     */
    public final EntityUID action;

    /** EUID of the resource in the request, whose id may be unknown. */
    public final PartialEntityUID resource;

    /** Key/Value map representing the context of the request. An empty {@code Optional} means it is unknown. */
    public final Optional<Map<String, Value>> context;

    /** Schema used to validate the request, and for schema-based parsing of `context`. */
    public final Schema schema;

    /**
     * Create a type-aware partial authorization request without validating it. Use {@link #builder()} instead,
     * which validates the request against the schema.
     *
     * @param principal Principal's partial EUID.
     * @param action Action's EUID, which must be concrete.
     * @param resource Resource's partial EUID.
     * @param context Key/Value context. An empty {@code Optional} means the whole context is unknown.
     * @param schema Schema.
     */
    protected TypeAwarePartialAuthorizationRequest(
            PartialEntityUID principal,
            EntityUID action,
            PartialEntityUID resource,
            Optional<Map<String, Value>> context,
            Schema schema) {
        this.principal = principal;
        this.action = action;
        this.resource = resource;
        this.context = context;
        this.schema = schema;
    }

    /**
     * Copy an existing type-aware partial authorization request.
     *
     * @param other The request to copy.
     */
    protected TypeAwarePartialAuthorizationRequest(TypeAwarePartialAuthorizationRequest other) {
        this(other.principal, other.action, other.resource, other.context, other.schema);
    }

    /**
     * Creates a builder of type-aware partial authorization request.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder of type-aware partial authorization requests. */
    public static final class Builder {
        private PartialEntityUID principalEUID;
        private EntityUID actionEUID;
        private PartialEntityUID resourceEUID;
        private Optional<Map<String, Value>> context = Optional.empty();
        private Schema schema;

        private Builder() {
        }

        public Builder principal(PartialEntityUID principalEUID) {
            this.principalEUID = principalEUID;
            return this;
        }

        public Builder principal(EntityUID principalEUID) {
            this.principalEUID = new PartialEntityUID(principalEUID);
            return this;
        }

        /**
         * Set a principal whose id is unknown. Its type is still required, since Cedar validates the request.
         *
         * @param principalType the principal's entity type
         * @return The builder.
         */
        public Builder principal(EntityTypeName principalType) {
            this.principalEUID = new PartialEntityUID(principalType);
            return this;
        }

        public Builder action(EntityUID actionEUID) {
            this.actionEUID = actionEUID;
            return this;
        }

        public Builder resource(PartialEntityUID resourceEUID) {
            this.resourceEUID = resourceEUID;
            return this;
        }

        public Builder resource(EntityUID resourceEUID) {
            this.resourceEUID = new PartialEntityUID(resourceEUID);
            return this;
        }

        /**
         * Set a resource whose id is unknown. Its type is still required, since Cedar validates the request.
         *
         * @param resourceType the resource's entity type
         * @return The builder.
         */
        public Builder resource(EntityTypeName resourceType) {
            this.resourceEUID = new PartialEntityUID(resourceType);
            return this;
        }

        public Builder context(Map<String, Value> context) {
            this.context = Optional.of(Map.copyOf(context));
            return this;
        }

        public Builder context(Context context) {
            this.context = Optional.of(Map.copyOf(context.getContext()));
            return this;
        }

        /**
         * Set the context to be empty, not unknown.
         * @return The builder.
         */
        public Builder emptyContext() {
            this.context = Optional.of(Map.of());
            return this;
        }

        public Builder schema(Schema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Build the type-aware partial authorization request, validating it against the schema.
         *
         * @return The request.
         * @throws InternalException If the request does not validate against the schema, if the context contains an
         *     {@link com.cedarpolicy.value.Unknown} (the context here is all-or-nothing, so individual values cannot be
         *     left unknown), or if the request cannot be serialized.
         * @throws NullPointerException If the principal, action, resource, or schema was not set.
         */
        public TypeAwarePartialAuthorizationRequest build() throws InternalException {
            Objects.requireNonNull(principalEUID, "principal is required, pass a PartialEntityUID built from just "
                    + "its type if the id is unknown");
            Objects.requireNonNull(actionEUID, "action is required and must be concrete");
            Objects.requireNonNull(resourceEUID, "resource is required, pass a PartialEntityUID built from just "
                    + "its type if the id is unknown");
            Objects.requireNonNull(schema, "schema is required, type-aware partial evaluation validates the request");
            final TypeAwarePartialAuthorizationRequest request = new TypeAwarePartialAuthorizationRequest(
                    principalEUID,
                    actionEUID,
                    resourceEUID,
                    context,
                    schema);
            try {
                validateTypeAwarePartialRequestJni(objectWriter().writeValueAsString(request));
            } catch (JsonProcessingException e) {
                throw new InternalException("failed to serialize the type-aware partial authorization request: "
                        + e.getMessage());
            } catch (InternalException e) {
                throw ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION.translateIfDisabled(e);
            }
            return request;
        }
    }

    private static native String validateTypeAwarePartialRequestJni(String requestJson) throws InternalException;
}
