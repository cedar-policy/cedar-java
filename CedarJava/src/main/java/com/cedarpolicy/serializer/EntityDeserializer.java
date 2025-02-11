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

package com.cedarpolicy.serializer;

import com.cedarpolicy.model.exception.InvalidValueDeserializationException;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.model.entity.Entity;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Deserialize Json to Entity.
 */
public class EntityDeserializer extends JsonDeserializer<Entity> {

    /**
     * Deserializes a JSON input into an Entity object.
     *
     * @param parser  The JsonParser providing the JSON input to deserialize
     * @param context The deserialization context
     *
     * @return An Entity object constructed from the JSON input
     * @throws IOException                          If there is an error reading from the JsonParser
     * @throws InvalidValueDeserializationException If the JSON input is invalid or missing required fields
     */
    @Override
    public Entity deserialize(JsonParser parser, DeserializationContext context)
            throws IOException, InvalidValueDeserializationException {
        final JsonNode node = parser.getCodec().readTree(parser);
        final ObjectMapper mapper = (ObjectMapper) parser.getCodec();

        EntityUID euid;
        if (node.has("uid")) {
            JsonNode uidNode = node.get("uid");
            euid = parseEntityUID(parser, uidNode);
        } else {
            String msg = "\"uid\" not found";
            throw new InvalidValueDeserializationException(parser, msg, node.asToken(), Entity.class);
        }

        Map<String, Value> attrs;
        if (node.has("attrs")) {
            JsonNode attrsNode = node.get("attrs");
            if (attrsNode.isObject()) {
                attrs = parseValueMap(mapper, attrsNode);
            } else {
                String msg = "\"attrs\" must be a JSON object";
                throw new InvalidValueDeserializationException(parser, msg, attrsNode.asToken(), Entity.class);
            }
        } else {
            String msg = "\"attrs\" not found";
            throw new InvalidValueDeserializationException(parser, msg, node.asToken(), Entity.class);
        }

        Set<EntityUID> parentEUIDs;
        if (node.has("parents")) {
            JsonNode parentsNode = node.get("parents");
            if (parentsNode.isArray()) {
                parentEUIDs = StreamSupport.stream(parentsNode.spliterator(), false).map(parentNode -> {
                    try {
                        return parseEntityUID(parser, parentNode);
                    } catch (InvalidValueDeserializationException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());
            } else {
                String msg = "\"parents\" field must be a JSON array";
                throw new InvalidValueDeserializationException(parser, msg, parentsNode.asToken(), Entity.class);
            }
        } else {
            String msg = "\"parents\" not found";
            throw new InvalidValueDeserializationException(parser, msg, node.asToken(), Entity.class);
        }

        Map<String, Value> tags = new HashMap<>();
        if (node.has("tags")) {
            JsonNode tagsNode = node.get("tags");
            if (tagsNode.isObject()) {
                tags.putAll(parseValueMap(mapper, tagsNode));
            } else {
                String msg = "\"tags\" must be a JSON object";
                throw new InvalidValueDeserializationException(parser, msg, tagsNode.asToken(), Entity.class);
            }
        }
        return new Entity(euid, attrs, parentEUIDs, tags);
    }

    /**
     * Parses a JSON node into an EntityUID object.
     *
     * @param parser        The JsonParser used for error reporting
     * @param entityUIDJson The JsonNode containing the entity UID data to parse. Must have "type" and "id" fields.
     *
     * @return An EntityUID object constructed from the JSON data
     * @throws InvalidValueDeserializationException if the required fields are missing or invalid
     */
    private EntityUID parseEntityUID(JsonParser parser, JsonNode entityUIDJson)
            throws InvalidValueDeserializationException {
        if (entityUIDJson.has("type") && entityUIDJson.has("id")) {
            JsonEUID jsonEuid = new JsonEUID(entityUIDJson.get("type").asText(), entityUIDJson.get("id").asText());
            return EntityUID.parseFromJson(jsonEuid).get();
        } else {
            String msg = "\"type\" or \"id\" not found";
            throw new InvalidValueDeserializationException(parser, msg, entityUIDJson.asToken(), Entity.class);
        }
    }

    /**
     * Parses a JSON node containing key-value pairs into a Map of String to Value objects.
     *
     * @param mapper       The ObjectMapper used to convert JSON nodes to Value objects
     * @param valueMapJson The JsonNode containing the key-value pairs to parse
     *
     * @return A Map where keys are Strings and values are Value objects
     * @throws RuntimeException if there is an error converting a JSON node to a Value
     */
    private Map<String, Value> parseValueMap(ObjectMapper mapper, JsonNode valueMapJson) {
        Map<String, Value> valueMap = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(valueMapJson.fields(), Spliterator.ORDERED), false)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    try {
                        return mapper.treeToValue(entry.getValue(), Value.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
        return valueMap;
    }
}
