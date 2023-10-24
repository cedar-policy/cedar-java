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

package com.cedarpolicy.value;

import java.util.Optional;

import com.cedarpolicy.serializer.JsonEUID;

/**
 * Represents a Cedar Entity UID. An entity UID contains both the entity type and a unique
 * identifier for the entity formatted as <code>TYPE::"ID"</code>.
 */

public final class EntityUID extends Value {
    private final EntityTypeName type;
    private final EntityIdentifier id;
    private final String repr;

    static { 
        System.load(System.getenv("CEDAR_JAVA_FFI_LIB"));
    }

    public EntityUID(EntityTypeName type, EntityIdentifier id) {
        this.type = type;
        this.id = id;
        this.repr = getEUIDRepr(type, id);
    }

    public EntityUID(EntityTypeName type, String id) {
        this(type, new EntityIdentifier(id));
    }

    public EntityTypeName getType() {
        return type;
    }

    public EntityIdentifier getId() {
        return id;
    }


    @Override
    public String toString() {
        return this.repr;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } if (o == this) {
            return true;
        } else {
            try {
                var rhs = (EntityUID) o;
                return this.type.equals(rhs.type) && this.id.equals(rhs.id);
            } catch (ClassCastException e) {
                return false;
            }
        }
    }

    @Override
    public int hashCode() {
        return type.hashCode() + id.hashCode();
    }

    @Override
    public String toCedarExpr() {
        return this.repr;
    }


    public static Optional<EntityUID> parse(String src) {
        return parseEntityUID(src);
    }

    public JsonEUID asJson() {
        return new JsonEUID(type.toString(), id.toString());
    }

    public static Optional<EntityUID> parseFromJson(JsonEUID euid) {
        return EntityTypeName.parse(euid.type).map(type -> new EntityUID(type, new EntityIdentifier(euid.id)));
    }

    private static native Optional<EntityUID> parseEntityUID(String src);
    private static native String getEUIDRepr(EntityTypeName type, EntityIdentifier id);

}

