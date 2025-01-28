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

package com.cedarpolicy.model.entity;

import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.model.exception.InternalException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An entity is the kind of object about which authorization decisions are made; principals,
 * actions, and resources are all a kind of entity. Each entity is defined by its entity type, a
 * unique identifier (UID), zero or more attributes mapped to values, zero or more parent
 * entities, and zero or more tags.
 */
public class Entity {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EntityUID euid;

    /** Key/Value attribute map. */
    public final Map<String, Value> attrs;

    /** Set of entity EUIDs that are parents to this entity. */
    public final Set<EntityUID> parentsEUIDs;

    /** Tags on this entity (RFC 82) */
    public final Map<String, Value> tags;

    /**
     * Create an entity from an EntityUIDs, a map of attributes, and a set of parent EntityUIDs.
     *
     * @param uid EUID of the Entity.
     * @param attributes Key/Value map of attributes.
     * @param parentsEUIDs Set of parent entities' EUIDs.
     */
    public Entity(EntityUID uid, Map<String, Value> attributes, Set<EntityUID> parentsEUIDs) {
        this(uid, attributes, parentsEUIDs, new HashMap<>());
    }

    /**
     * Create an entity from an EntityUIDs, a map of attributes, a set of parent EntityUIDs, and a map of tags.
     *
     * @param uid EUID of the Entity.
     * @param attributes Key/Value map of attributes.
     * @param parentsEUIDs Set of parent entities' EUIDs.
     * @param tags Key/Value map of tags.
     */
    public Entity(EntityUID uid, Map<String, Value> attributes, Set<EntityUID> parentsEUIDs, Map<String, Value> tags) {
        this.attrs = new HashMap<>(attributes);
        this.euid = uid;
        this.parentsEUIDs = parentsEUIDs;
        this.tags = new HashMap<>(tags);
    }

    public String toJsonString() throws InternalException, NullPointerException {
        String entityJsonStr = toJsonEntityJni(this);
        return entityJsonStr;
    }

    public JsonNode toJsonValue() throws InternalException, NullPointerException, JsonProcessingException {
        String entityJsonStr = this.toJsonString();
        return OBJECT_MAPPER.readTree(entityJsonStr);
    }

    /**
     * Get the value for the given attribute, or null if not present.
     * 
     * @param attribute Attribute key
     * @return Attribute value for the given key or null if not present
     * @throws IllegalArgumentException if attribute is null
     */
    public Value getAttr(String attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute key cannot be null");
        }
        return this.attrs.getOrDefault(attribute, null);
    }

    @Override
    public String toString() {
        String parentStr = "";
        if (!parentsEUIDs.isEmpty()) {
            List<String> parentStrs = new ArrayList<String>(parentsEUIDs.stream()
                    .map(euid -> euid.toString()).collect(Collectors.toList()));
            parentStr = "\n\tparents:\n\t\t" + String.join("\n\t\t", parentStrs);
        }
        String attributeStr = "";
        if (!attrs.isEmpty()) {
            attributeStr =
                    "\n\tattrs:\n\t\t"
                            + attrs.entrySet().stream()
                                    .map(e -> e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n\t\t"));
        }
        String tagsStr = "";
        if (!tags.isEmpty()) {
            tagsStr =
                    "\n\ttags:\n\t\t"
                            + tags.entrySet().stream()
                                    .map(e -> e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n\t\t"));
        }
        return euid.toString() + parentStr + attributeStr + tagsStr;
    }


    /**
     * Get the entity uid
     * @return Entity UID
     */
    public EntityUID getEUID() {
        return euid;
    }

    /**
     * Get the Entity's attributes
     * @return the map of attributes
     */
    public Map<String, Value> getAttributes() {
        return attrs;
    }

    /**
     * Get this Entity's parents
     * @return the set of parent EntityUIDs
     */
    public Set<EntityUID> getParents() {
        return parentsEUIDs;
    }

    /**
     * Get this Entity's tags
     * @return the map of tags
     */
    public Map<String, Value> getTags() {
        return tags;
    }

    private static native String toJsonEntityJni(Entity entity) throws InternalException, NullPointerException;
}
