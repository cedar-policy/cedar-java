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

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;
import com.cedarpolicy.model.schema.Schema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.cedarpolicy.CedarJson.objectMapper;
import static com.cedarpolicy.CedarJson.objectWriter;

/**
 * A collection of partially known Cedar entities. Entities left out of the collection are entities whose existence, as
 * well as all of whose data, is unknown.
 *
 * <p>Two of Cedar's checks span the whole collection and so can only run here: no two entities may share a UID, and an
 * entity that supplies its parents may not name a parent that is present in this collection with its own parents
 * omitted. Both are hard errors; see {@link PartialEntity} for why the second one is not a partial unknown.
 *
 * <p>An instance is immutable, as are the entities it holds.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
public final class PartialEntities {
    static {
        LibraryLoader.loadLibrary();
    }

    private final Set<PartialEntity> entities;

    private PartialEntities(Set<PartialEntity> entities) {
        this.entities = Set.copyOf(entities);
    }

    /**
     * Returns the entities in this collection.
     *
     * @return An unmodifiable set of the PartialEntity objects in this collection
     */
    public Set<PartialEntity> getEntities() {
        return entities;
    }

    /**
     * Constructs a collection from a given Set of PartialEntity objects, checking the collection as a whole against the
     * schema.
     *
     * @param entities The partially known entities.
     * @param schema   The schema to check the entities against.
     * @throws InternalException                   If the entities do not check out against the schema, or if they cannot
     *                                             be serialized.
     * @throws MissingExperimentalFeatureException If the native library was built without the {@code tpe} feature.
     * @throws NullPointerException                If the schema is null.
     */
    public PartialEntities(Set<PartialEntity> entities, Schema schema) throws InternalException {
        final String entitiesJson;
        try {
            entitiesJson = objectWriter().writeValueAsString(entities);
        } catch (JsonProcessingException e) {
            throw new InternalException("Failed to serialize the partial entities: " + e.getMessage());
        }
        validate(entitiesJson, schema);
        this.entities = Set.copyOf(entities);
    }

    /**
     * Constructs a collection from concrete entities. Each one is fully known, so its attributes, parents, and tags all
     * come across as present rather than unknown.
     *
     * @param entities The concrete entities.
     * @param schema   The schema to check the entities against.
     * @throws InternalException                   If the entities do not check out against the schema, or if they cannot
     *                                             be serialized.
     * @throws MissingExperimentalFeatureException If the native library was built without the {@code tpe} feature.
     * @throws NullPointerException                If the schema is null.
     */
    public PartialEntities(Entities entities, Schema schema) throws InternalException {
        this(lift(entities), schema);
    }

    /**
     * Constructs a collection from Cedar's JSON encoding of partially known entities, which is an array of objects, each
     * with a {@code uid} and with {@code attrs}, {@code parents}, and {@code tags} present only when they are known.
     *
     * <p>Attribute and tag values are read with the same plumbing as concrete entities, so an entity reference in a value
     * must use the {@code __entity} escape rather than the bare {@code {"type", "id"}} form that Cedar also accepts when
     * it parses against a schema.
     *
     * @param json   The array of encoded entities.
     * @param schema The schema to check the entities against.
     * @return The collection.
     * @throws InternalException                   If the entities do not check out against the schema, or if the encoding
     *                                             cannot be read.
     * @throws MissingExperimentalFeatureException If the native library was built without the {@code tpe} feature.
     * @throws NullPointerException                If the JSON or the schema is null.
     */
    public static PartialEntities fromJson(JsonNode json, Schema schema) throws InternalException {
        if (!json.isArray()) {
            throw new InternalException("Partially known entities must be encoded as a JSON array.");
        }
        validate(json.toString(), schema);
        final ObjectMapper mapper = objectMapper();
        final Set<PartialEntity> entities = new HashSet<>();
        for (JsonNode entityJson : json) {
            entities.add(PartialEntity.fromJson(entityJson, mapper));
        }
        return new PartialEntities(entities);
    }

    /**
     * Constructs a collection in which nothing at all is known.
     *
     * @return The collection.
     */
    public static PartialEntities empty() {
        return new PartialEntities(new HashSet<>());
    }

    @Override
    public String toString() {
        return String.join("\n", this.entities.stream().map(PartialEntity::toString).toList());
    }

    private static void validate(String entitiesJson, Schema schema) throws InternalException {
        Objects.requireNonNull(schema, "A schema is required because Cedar checks the supplied fields against it.");
        final String schemaJson;
        try {
            schemaJson = objectWriter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new InternalException("Failed to serialize the schema: " + e.getMessage());
        }
        try {
            validatePartialEntitiesJni(entitiesJson, schemaJson);
        } catch (InternalException e) {
            throw ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION.translateIfDisabled(e);
        }
    }

    /**
     * Lift concrete entities without checking each one, because the caller goes on to check the whole collection in a
     * single native call, which subsumes the per-entity checks.
     */
    private static Set<PartialEntity> lift(Entities entities) {
        final Set<PartialEntity> lifted = new HashSet<>();
        for (Entity entity : entities.getEntities()) {
            lifted.add(new PartialEntity(entity.getEUID(), Optional.of(entity.attrs),
                    Optional.of(entity.getParents()), Optional.of(entity.tags)));
        }
        return lifted;
    }

    private static native String validatePartialEntitiesJni(String entitiesJson, String schemaJson)
            throws InternalException;
}
