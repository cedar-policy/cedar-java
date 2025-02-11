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
import static com.cedarpolicy.CedarJson.objectReader;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.Path;

/**
 * An entity is the kind of object about which authorization decisions are made; principals, actions, and resources are
 * all a kind of entity. Each entity is defined by its entity type, a unique identifier (UID), zero or more attributes
 * mapped to values, zero or more parent entities, and zero or more tags.
 */
public class Entity {
    private final EntityUID euid;

    /** Key/Value attribute map. */
    public final Map<String, Value> attrs;

    /** Set of entity EUIDs that are parents to this entity. */
    public final Set<EntityUID> parentsEUIDs;

    /** Tags on this entity (RFC 82) */
    public final Map<String, Value> tags;

    /**
     * Create an entity from an EntityUID. It will have no attributes, parents, or tags.
     *
     * @param uid EUID of the Entity.
     */
    public Entity(EntityUID uid) {
        this(uid, new HashMap<>(), new HashSet<>(), new HashMap<>());
    }

    /**
     * Create an entity from an EntityUID and a set of parent EntityUIDs. It will have no attributes or tags.
     *
     * @param uid          EUID of the Entity.
     * @param parentsEUIDs Set of parent entities' EUIDs.
     */
    public Entity(EntityUID uid, Set<EntityUID> parentsEUIDs) {
        this(uid, new HashMap<>(), parentsEUIDs, new HashMap<>());
    }

    /**
     * Create an entity from an EntityUIDs, a map of attributes, and a set of parent EntityUIDs.
     *
     * @param uid          EUID of the Entity.
     * @param attributes   Key/Value map of attributes.
     * @param parentsEUIDs Set of parent entities' EUIDs.
     */
    public Entity(EntityUID uid, Map<String, Value> attributes, Set<EntityUID> parentsEUIDs) {
        this(uid, attributes, parentsEUIDs, new HashMap<>());
    }

    /**
     * Create an entity from an EntityUIDs, a map of attributes, a set of parent EntityUIDs, and a map of tags.
     *
     * @param uid          EUID of the Entity.
     * @param attributes   Key/Value map of attributes.
     * @param parentsEUIDs Set of parent entities' EUIDs.
     * @param tags         Key/Value map of tags.
     */
    public Entity(EntityUID uid, Map<String, Value> attributes, Set<EntityUID> parentsEUIDs, Map<String, Value> tags) {
        this.attrs = new HashMap<>(attributes);
        this.euid = uid;
        this.parentsEUIDs = parentsEUIDs;
        this.tags = new HashMap<>(tags);
    }

    /**
     * Get the value for the given attribute, or null if not present.
     *
     * @param attribute Attribute key
     * 
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
            List<String> parentStrs = new ArrayList<String>(
                    parentsEUIDs.stream().map(euid -> euid.toString()).collect(Collectors.toList()));
            parentStr = "\n\tparents:\n\t\t" + String.join("\n\t\t", parentStrs);
        }
        String attributeStr = "";
        if (!attrs.isEmpty()) {
            attributeStr = "\n\tattrs:\n\t\t" + attrs.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n\t\t"));
        }
        String tagsStr = "";
        if (!tags.isEmpty()) {
            tagsStr = "\n\ttags:\n\t\t" + tags.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n\t\t"));
        }
        return euid.toString() + parentStr + attributeStr + tagsStr;
    }

    /**
     * Get the entity uid
     *
     * @return Entity UID
     */
    public EntityUID getEUID() {
        return euid;
    }

    /**
     * Get this Entity's parents
     *
     * @return the set of parent EntityUIDs
     */
    public Set<EntityUID> getParents() {
        return parentsEUIDs;
    }

    /**
     * Get this Entity's tags
     *
     * @return the map of tags
     */
    public Map<String, Value> getTags() {
        return tags;
    }

    /**
     * Parse Entity from a JSON string
     *
     * @param jsonString The JSON string representation of an Entity
     *
     * @return Entity object parsed from the JSON string
     * @throws JsonProcessingException if the JSON string cannot be parsed into an Entity
     */
    public static Entity parse(String jsonString) throws JsonProcessingException {
        return objectReader().forType(Entity.class).readValue(jsonString);
    }

    /**
     * Parse Entity from a file containing JSON representation of an Entity
     *
     * @param filePath Path to the file containing Entity JSON
     *
     * @return Entity object parsed from the file contents
     * @throws IOException             if there is an error reading the file
     * @throws JsonProcessingException if the file contents cannot be parsed into an Entity
     */
    public static Entity parse(Path filePath) throws IOException, JsonProcessingException {
        String jsonString = Files.readString(filePath);
        return parse(jsonString);
    }
}
