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
import com.cedarpolicy.serializer.JsonEUID;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cedarpolicy.CedarJson.objectWriter;

/**
 * An entity whose attributes, parents, and tags may be unknown. The EUID is always known; each of the three other
 * fields is either absent, meaning unknown, or present, in which case it must be <em>complete</em>. Cedar checks any
 * field that is supplied against the schema in full, and derives the transitive ancestor closure from the parents that
 * are supplied, so an incomplete map or parent set is an error rather than a partial unknown. Unknown-ness is therefore
 * per field, never per attribute or per tag.
 *
 * <p>Absent is not the same as empty: {@code Optional.empty()} leaves the attributes unknown, whereas an empty map
 * states that the entity is known to have no attributes. Parents and tags behave the same way.
 *
 * <p>The two levels of unknown-ness are distinct. Omitting an entity from a {@link PartialEntities} altogether leaves
 * both its existence and all of its data unknown, while including it here with a field omitted asserts that the entity
 * does exist and leaves only that one field unknown.
 */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
public final class PartialEntity {
    private static final TypeReference<Map<String, Value>> VALUE_MAP = new TypeReference<>() {
    };

    static {
        LibraryLoader.loadLibrary();
    }

    private final EntityUID euid;
    private final Optional<Map<String, Value>> attrs;
    private final Optional<Set<EntityUID>> parents;
    private final Optional<Map<String, Value>> tags;

    /**
     * Construct a partial entity, checking every field that was supplied against the schema. Mirrors
     * {@code PartialEntity::new}. Each of the three optional fields is either absent, meaning unknown, or present and
     * complete: Cedar cannot tell whether a supplied map or parent set is complete without the schema, so the schema is
     * required.
     *
     * @param euid    EUID of the entity.
     * @param attrs   The attributes, absent if unknown, empty if the entity is known to have none.
     * @param parents The direct parents, absent if unknown, empty if the entity is known to have none.
     * @param tags    The tags, absent if unknown, empty if the entity is known to have none.
     * @param schema  The schema to check the entity against.
     * @throws InternalException                   If the entity does not check out against the schema, or if it cannot
     *                                             be serialized.
     * @throws MissingExperimentalFeatureException If the native library was built without the {@code tpe} feature.
     * @throws NullPointerException                If the schema is null.
     */
    public PartialEntity(EntityUID euid, Optional<Map<String, Value>> attrs, Optional<Set<EntityUID>> parents,
            Optional<Map<String, Value>> tags, Schema schema) throws InternalException {
        this(euid, attrs, parents, tags);
        validate(schema);
    }

    /**
     * Lift a fully known entity: its attributes, parents, and tags are all present.
     *
     * @param entity The concrete entity.
     * @param schema The schema to check the entity against.
     * @throws InternalException                   If the entity does not check out against the schema, or if it cannot
     *                                             be serialized.
     * @throws MissingExperimentalFeatureException If the native library was built without the {@code tpe} feature.
     * @throws NullPointerException                If the schema is null.
     */
    public PartialEntity(Entity entity, Schema schema) throws InternalException {
        this(entity.getEUID(), Optional.of(entity.attrs), Optional.of(entity.getParents()), Optional.of(entity.tags),
                schema);
    }

    /**
     * Construct a partial entity without checking it against a schema. Only for callers that go on to have Cedar check
     * the whole collection in a single native call, which subsumes the per-entity checks and additionally covers the
     * rules that span the collection. In practice that means {@link PartialEntities}, on both the JSON and the concrete
     * path.
     */
    PartialEntity(EntityUID euid, Optional<Map<String, Value>> attrs, Optional<Set<EntityUID>> parents,
            Optional<Map<String, Value>> tags) {
        this.euid = euid;
        this.attrs = attrs.map(Map::copyOf);
        this.parents = parents.map(Set::copyOf);
        this.tags = tags.map(Map::copyOf);
    }

    /**
     * Read a partial entity from Cedar's JSON encoding, without checking it against a schema. Only for callers that go
     * on to have Cedar check the whole collection, as described on the constructor this delegates to. The encoding is an
     * object with a {@code uid} and with {@code attrs}, {@code parents}, and {@code tags} present only when they are
     * known.
     *
     * @param json   The encoded entity.
     * @param mapper The mapper to read attribute and tag values with.
     * @return The partial entity.
     * @throws InternalException If the encoding cannot be read.
     */
    static PartialEntity fromJson(JsonNode json, ObjectMapper mapper) throws InternalException {
        if (!json.has("uid")) {
            throw new InternalException("A partially known entity must have a \"uid\" field: " + json);
        }
        Optional<Map<String, Value>> attrs = Optional.empty();
        if (json.hasNonNull("attrs")) {
            attrs = Optional.of(parseValueMap(mapper, json.get("attrs"), "attrs"));
        }
        Optional<Set<EntityUID>> parents = Optional.empty();
        if (json.hasNonNull("parents")) {
            final Set<EntityUID> directParents = new HashSet<>();
            for (JsonNode parent : json.get("parents")) {
                directParents.add(parseEntityUID(parent));
            }
            parents = Optional.of(directParents);
        }
        Optional<Map<String, Value>> tags = Optional.empty();
        if (json.hasNonNull("tags")) {
            tags = Optional.of(parseValueMap(mapper, json.get("tags"), "tags"));
        }
        return new PartialEntity(parseEntityUID(json.get("uid")), attrs, parents, tags);
    }

    private static EntityUID parseEntityUID(JsonNode json) throws InternalException {
        final JsonNode euid = json.has("__entity") ? json.get("__entity") : json;
        if (!euid.has("type") || !euid.has("id")) {
            throw new InternalException("An entity UID must have \"type\" and \"id\" fields: " + json);
        }
        final String type = euid.get("type").asText();
        return EntityUID.parseFromJson(new JsonEUID(type, euid.get("id").asText()))
                .orElseThrow(() -> new InternalException("Invalid entity type name: " + type));
    }

    private static Map<String, Value> parseValueMap(ObjectMapper mapper, JsonNode json, String field)
            throws InternalException {
        try {
            return mapper.convertValue(json, VALUE_MAP);
        } catch (IllegalArgumentException e) {
            throw new InternalException("Failed to read \"" + field + "\": " + e.getMessage());
        }
    }

    private void validate(Schema schema) throws InternalException {
        Objects.requireNonNull(schema, "A schema is required because Cedar checks the supplied fields against it.");
        final String entityJson;
        try {
            entityJson = objectWriter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new InternalException("Failed to serialize the partial entity: " + e.getMessage());
        }
        final String schemaJson;
        try {
            schemaJson = objectWriter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new InternalException("Failed to serialize the schema: " + e.getMessage());
        }
        try {
            validatePartialEntityJni(entityJson, schemaJson);
        } catch (InternalException e) {
            throw ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION.translateIfDisabled(e);
        }
    }

    /**
     * Get the EUID of this entity, which is always known.
     *
     * @return The EUID.
     */
    public EntityUID getEUID() {
        return euid;
    }

    /**
     * Get the attributes of this entity.
     *
     * @return An unmodifiable map of the attributes, or absent if they are unknown.
     */
    public Optional<Map<String, Value>> getAttrs() {
        return attrs;
    }

    /**
     * Get the direct parents of this entity, from which Cedar derives the transitive closure.
     *
     * @return An unmodifiable set of the direct parents, or absent if they are unknown.
     */
    public Optional<Set<EntityUID>> getParents() {
        return parents;
    }

    /**
     * Get the tags of this entity.
     *
     * @return An unmodifiable map of the tags, or absent if they are unknown.
     */
    public Optional<Map<String, Value>> getTags() {
        return tags;
    }

    /**
     * A debug rendering, laid out like {@link Entity#toString()}. Nothing in the translation layer reads it: the wire
     * form is produced by {@link com.cedarpolicy.serializer.PartialEntitySerializer} from the getters. A field that is
     * unknown is labelled as such, while a field that is known to be empty is omitted, the same as on a concrete
     * entity.
     *
     * @return a debug rendering of this partial entity
     */
    @Override
    public String toString() {
        return euid.toString()
                + section("parents", parents.map(ps -> ps.stream().map(EntityUID::toString)))
                + section("attrs", attrs.map(PartialEntity::renderEntries))
                + section("tags", tags.map(PartialEntity::renderEntries));
    }

    private static Stream<String> renderEntries(Map<String, Value> values) {
        return values.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue());
    }

    private static String section(String label, Optional<Stream<String>> entries) {
        if (entries.isEmpty()) {
            return "\n\t" + label + ": unknown";
        }
        final String body = entries.get().collect(Collectors.joining("\n\t\t"));
        return body.isEmpty() ? "" : "\n\t" + label + ":\n\t\t" + body;
    }

    private static native String validatePartialEntityJni(String entityJson, String schemaJson)
            throws InternalException;
}
