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

package com.cedarpolicy.model.schema;

import java.util.Optional;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Represents a schema. */
public final class Schema {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        LibraryLoader.loadLibrary();
    }

    /** Is this schema in the JSON or Cedar format */
    public final JsonOrCedar type;

    /** This will be present if and only if `type` is `Json`. */
    public final Optional<JsonNode> schemaJson;

    /** This will be present if and only if `type` is `Cedar`. */
    public final Optional<String> schemaText;

    /**
     * If `type` is `Json`, `schemaJson` should be present and `schemaText` empty.
     * If `type` is `Cedar`, `schemaText` should be present and `schemaJson` empty.
     * This constructor does not check that the input text represents a valid JSON
     * or Cedar schema. Use the `parse` function to ensure schema validity.
     *
     * @param type       The schema format used.
     * @param schemaJson Optional schema in the JSON schema format.
     * @param schemaText Optional schema in the Cedar schema format.
     */
    public Schema(JsonOrCedar type, Optional<String> schemaJson, Optional<String> schemaText) {
        this.type = type;
        this.schemaJson = schemaJson.map(jsonStr -> {
            try {
                return OBJECT_MAPPER.readTree(jsonStr);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        this.schemaText = schemaText;
    }

    /**
     * Build a Schema from a json node. This does not check that the parsed JSON
     * object represents a valid schema. Use `parse` to check validity.
     *
     * @param schemaJson Schema in Cedar's JSON schema format.
     */
    public Schema(JsonNode schemaJson) {
        if (schemaJson == null) {
            throw new NullPointerException("schemaJson");
        }
        this.type = JsonOrCedar.Json;
        this.schemaJson = Optional.of(schemaJson);
        this.schemaText = Optional.empty();
    }

    /**
     * Build a Schema from a string. This does not check that the string represents
     * a valid schema. Use `parse` to check validity.
     *
     * @param schemaText Schema in the Cedar schema format.
     */
    public Schema(String schemaText) {
        if (schemaText == null) {
            throw new NullPointerException("schemaText");
        }
        this.type = JsonOrCedar.Cedar;
        this.schemaJson = Optional.empty();
        this.schemaText = Optional.of(schemaText);
    }

    public String toString() {
        if (type == JsonOrCedar.Json) {
            return "Schema(schemaJson=" + schemaJson.get() + ")";
        } else {
            return "Schema(schemaText=" + schemaText.get() + ")";
        }
    }

    /**
     * Try to parse a string representing a JSON or Cedar schema. If parsing
     * succeeds, return a `Schema`, otherwise raise an exception.
     *
     * @param type The schema format used.
     * @param str  Schema text to parse.
     * @throws InternalException    If parsing fails.
     * @throws NullPointerException If the input text is null.
     * @return A {@link Schema} that is guaranteed to be valid.
     */
    public static Schema parse(JsonOrCedar type, String str) throws InternalException, NullPointerException {
        if (type == JsonOrCedar.Json) {
            parseJsonSchemaJni(str);
            return new Schema(JsonOrCedar.Json, Optional.of(str), Optional.empty());
        } else {
            parseCedarSchemaJni(str);
            return new Schema(JsonOrCedar.Cedar, Optional.empty(), Optional.of(str));
        }

    }

    /**
     * Converts a schema to Cedar format
     *
     * @return String representing the schema in Cedar format
     * @throws InternalException     If conversion from JSON to Cedar format fails
     * @throws IllegalStateException If schema content is missing
     * @throws NullPointerException  If schema text is null
     */
    public String toCedarFormat() throws InternalException {
        if (type == JsonOrCedar.Cedar && schemaText.isPresent()) {
            return schemaText.get();
        } else if (type == JsonOrCedar.Json && schemaJson.isPresent()) {
            return jsonToCedarJni(schemaJson.get().toString());
        } else {
            throw new IllegalStateException("Schema content is missing");
        }
    }

    /**
     * Converts a Cedar format schema to JSON format
     *
     * @return JsonNode representing the schema in JSON format
     * @throws InternalException       If conversion from Cedar to JSON format fails
     * @throws JsonMappingException    If JSON mapping fails
     * @throws JsonProcessingException If JSON processing fails
     * @throws IllegalStateException   If schema content is missing
     * @throws NullPointerException    If schema text is null
     */
    public JsonNode toJsonFormat()
            throws InternalException, JsonMappingException, JsonProcessingException, NullPointerException {
        if (type == JsonOrCedar.Json && schemaJson.isPresent()) {
            return schemaJson.get();
        } else if (type == JsonOrCedar.Cedar && schemaText.isPresent()) {
            return OBJECT_MAPPER.readTree(cedarToJsonJni(schemaText.get()));
        } else {
            throw new IllegalStateException("Schema content is missing");
        }
    }

    /** Specifies the schema format used. */
    public enum JsonOrCedar {
        /**
         * Cedar JSON schema format. See
         * <a href="https://docs.cedarpolicy.com/schema/json-schema.html">
         * https://docs.cedarpolicy.com/schema/json-schema.html</a>
         */
        Json,
        /**
         * Cedar schema format. See
         * <a href="https://docs.cedarpolicy.com/schema/human-readable-schema.html">
         * https://docs.cedarpolicy.com/schema/human-readable-schema.html</a>
         */
        Cedar
    }

    private static native String parseJsonSchemaJni(String schemaJson) throws InternalException, NullPointerException;

    private static native String parseCedarSchemaJni(String schemaText) throws InternalException, NullPointerException;

    private static native String jsonToCedarJni(String json) throws InternalException, NullPointerException;

    private static native String cedarToJsonJni(String cedar) throws InternalException, NullPointerException;
}
