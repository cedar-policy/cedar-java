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

import static com.cedarpolicy.CedarJson.objectReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;

/**
 * A class representing a collection of Cedar policy entities.
 */
public class Entities {
    private Set<Entity> entities;

    /**
     * Constructs a new empty Entities collection. Creates a new HashSet to store Entity objects.
     */
    public Entities() {
        this.entities = new HashSet<>();
    }

    /**
     * Constructs a new Entities collection from a given Set of Entity objects.
     *
     * @param entities The Set of Entity objects to initialize this collection with
     */
    public Entities(Set<Entity> entities) {
        this.entities = new HashSet<>(entities);
    }

    /**
     * Returns a copy of the set of entities in this collection.
     *
     * @return A new HashSet containing all Entity objects in this collection
     */
    public Set<Entity> getEntities() {
        return new HashSet<>(entities);
    }

    /**
     * Parses a JSON string representation into an Entities collection.
     *
     * @param jsonString The JSON string containing entity data to parse
     *
     * @return A new Entities instance containing the parsed entities
     * @throws JsonProcessingException If the JSON string cannot be parsed into valid entities
     */
    public static Entities parse(String jsonString) throws JsonProcessingException {
        return new Entities(objectReader().forType(new TypeReference<Set<Entity>>() {
        }).readValue(jsonString));
    }

    /**
     * Parses a JSON file at the specified path into an Entities collection.
     *
     * @param filePath The path to the JSON file containing entity data to parse
     *
     * @return A new Entities instance containing the parsed entities
     * @throws IOException             If there is an error reading the file
     * @throws JsonProcessingException If the JSON content cannot be parsed into valid entities
     */
    public static Entities parse(Path filePath) throws IOException, JsonProcessingException {
        String jsonString = Files.readString(filePath);
        return new Entities(objectReader().forType(new TypeReference<Set<Entity>>() {
        }).readValue(jsonString));
    }

    @Override
    public String toString() {
        return String.join("\n", this.entities.stream().map(Entity::toString).toList());
    }
}
