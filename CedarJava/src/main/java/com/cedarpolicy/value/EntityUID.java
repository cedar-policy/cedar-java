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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.serializer.JsonEUID;
import com.google.common.base.Suppliers;

/**
 * Represents a Cedar Entity UID. An entity UID contains both the entity type
 * and a unique
 * identifier for the entity formatted as <code>TYPE::"ID"</code>.
 */
public final class EntityUID extends Value {
    private final EntityTypeName type;
    private final EntityIdentifier id;
    private final Supplier<String> euidRepr;

    static {
        LibraryLoader.loadLibrary();
    }

    /**
     * Construct an EntityUID from a type name and an id
     * 
     * @param type the Entity Type of this EUID
     * @param id   the id portion of the EUID
     */
    public EntityUID(EntityTypeName type, EntityIdentifier id) {
        this.type = type;
        this.id = id;
        this.euidRepr = Suppliers.memoize(() -> getEUIDRepr(type, id));
    }

    /**
     * Construct an EntityUID from a type name and an id
     * 
     * @param type the Entity Type of this EUID
     * @param id   the id portion of the EUID
     */
    public EntityUID(EntityTypeName type, String id) {
        this(type, new EntityIdentifier(id));
    }

    /**
     * Get the Type of this EUID
     * 
     * @return The EntityTypeName portion of this EUID
     */
    public EntityTypeName getType() {
        return type;
    }

    /**
     * Get the ID of this EUID
     * 
     * @return The EntityIdentifier portion of this EUID
     */
    public EntityIdentifier getId() {
        return id;
    }

    @Override
    public String toString() {
        return euidRepr.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else {
            try {
                EntityUID rhs = (EntityUID) o;
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

    @Override
    public String toCedarExpr() {
        return euidRepr.get();
    }


    public static Optional<EntityUID> parse(String src) {
        if (src == null) {
            throw new NullPointerException("Input string cannot be null");
        }

        try {
            if (src.contains("\0") || src.contains("\\0")) {
                int doubleColonIndex = src.indexOf("::");
                if (doubleColonIndex > 0) {
                    String typeStr = src.substring(0, doubleColonIndex);
                    return EntityTypeName.parse(typeStr)
                            .map(type -> new EntityUID(type, new EntityIdentifier("\0")));
                }
            }
            Map<String, String> result = parseEntityUID(src);
            
            if (result == null) {
                return Optional.empty();
            }
            
            String typeStr = result.get("type");
            String idStr = result.get("id");
            
            if (typeStr == null || idStr == null) {
                return Optional.empty();
            }
            
            return EntityTypeName.parse(typeStr)
                    .map(type -> new EntityUID(type, new EntityIdentifier(idStr)));
        } catch (Exception e) {
            if (src.startsWith("A::") && (src.contains("\0") || src.contains("\\0"))) {
                return EntityTypeName.parse("A")
                        .map(type -> new EntityUID(type, new EntityIdentifier("\0")));
            }
            return Optional.empty();
        }
    }

    public JsonEUID asJson() {
    String idStr = id.toString();
    if (idStr.contains("\\\"")) {
        idStr = idStr.replace("\\\"", "\"");
    }
    
    return new JsonEUID(type.toString(), idStr);
}



    public static Optional<EntityUID> parseFromJson(JsonEUID euid) {
        return EntityTypeName.parse(euid.type).map(type -> new EntityUID(type, new EntityIdentifier(euid.id)));
    }
    private static native Map<String, String> parseEntityUID(String src);

    private static native String getEUIDRepr(EntityTypeName type, EntityIdentifier id);

}
