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

package com.cedarpolicy.value;

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Objects;
import java.util.Optional;

/**
 * An entity UID whose id may be unknown. The entity type is always known: type-aware partial evaluation requires the
 * type of an unknown principal or resource in order to type check the request.
 *
 * <p>This is the partial counterpart to {@link EntityUID} and holds the same component types, {@link EntityTypeName}
 * and {@link EntityIdentifier}, so that an id that is present has already been through the same validation as a
 * concrete one.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public final class PartialEntityUID {
    private final EntityTypeName type;
    private final Optional<EntityIdentifier> id;

    /**
     * Construct a partial EUID from a type name and an optional id. Mirrors {@code PartialEntityUid::new}.
     *
     * @param type the Entity Type of this EUID
     * @param id   the id portion of the EUID, absent if unknown
     */
    public PartialEntityUID(EntityTypeName type, Optional<EntityIdentifier> id) {
        this.type = type;
        this.id = id;
    }

    /**
     * Construct a partial EUID whose id is known.
     *
     * @param type the Entity Type of this EUID
     * @param id   the id portion of the EUID
     */
    public PartialEntityUID(EntityTypeName type, EntityIdentifier id) {
        this(type, Optional.of(id));
    }

    /**
     * Construct a partial EUID of the given type whose id is unknown.
     *
     * @param type the Entity Type of this EUID
     */
    public PartialEntityUID(EntityTypeName type) {
        this(type, Optional.empty());
    }

    /**
     * Construct a fully known partial EUID from a concrete EUID.
     *
     * @param euid the concrete EUID
     */
    public PartialEntityUID(EntityUID euid) {
        this(euid.getType(), euid.getId());
    }

    /**
     * Get the Type of this EUID.
     *
     * @return The EntityTypeName portion of this EUID.
     */
    @JsonProperty("type")
    @JsonSerialize(using = ToStringSerializer.class)
    public EntityTypeName getType() {
        return type;
    }

    /**
     * Get the ID of this EUID, absent if unknown.
     *
     * @return The EntityIdentifier portion of this EUID, absent if unknown.
     */
    @JsonProperty("id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    public Optional<EntityIdentifier> getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else {
            try {
                PartialEntityUID rhs = (PartialEntityUID) o;
                return this.type.equals(rhs.type) && this.id.equals(rhs.id);
            } catch (ClassCastException e) {
                return false;
            }
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
