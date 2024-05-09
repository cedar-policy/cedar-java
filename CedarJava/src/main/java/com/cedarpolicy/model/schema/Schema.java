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

package com.cedarpolicy.model.schema;

import com.cedarpolicy.loader.LibraryLoader;
import com.cedarpolicy.model.exception.InternalException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Optional;

/** Represents a schema. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Schema {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        LibraryLoader.loadLibrary();
    }

    /** Is this schema in the JSON or human format */
    @JsonIgnore
    public final JsonOrHuman type;
    /** This will be present if and only if `type` is `Json`. */
    @JsonProperty("json")
    private final Optional<JsonNode> schemaJson;
    /** This will be present if and only if `type` is `Human`. */
    @JsonProperty("human")
    public final Optional<String> schemaText;

    /**
     * If `type` is `Json`, `schemaJson` should be present and `schemaText` empty.
     * If `type` is `Human`, `schemaText` should be present and `schemaJson` empty.
     * This constructor does not check that the input text represents a valid JSON
     * or Cedar schema. Use the `parse` function to ensure schema validity.
     * 
     * @param type       The schema format used.
     * @param schemaJson Optional schema in Cedar's JSON schema format.
     * @param schemaText Optional schema in Cedar's human-readable schema format.
     */
    public Schema(JsonOrHuman type, Optional<String> schemaJson, Optional<String> schemaText) {
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
        this.type = JsonOrHuman.Json;
        this.schemaJson = Optional.of(schemaJson);
        this.schemaText = Optional.empty();
    }

    /**
     * Build a Schema from a string. This does not check that the string represents
     * a valid schema. Use `parse` to check validity.
     *
     * @param schemaText Schema in Cedar's human-readable schema format.
     */
    public Schema(String schemaText) {
        if (schemaText == null) {
            throw new NullPointerException("schemaText");
        }
        this.type = JsonOrHuman.Human;
        this.schemaJson = Optional.empty();
        this.schemaText = Optional.of(schemaText);
    }

    public String toString() {
        if (type == JsonOrHuman.Json) {
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
    public static Schema parse(JsonOrHuman type, String str) throws InternalException, NullPointerException {
        if (type == JsonOrHuman.Json) {
            parseJsonSchemaJni(str);
            return new Schema(JsonOrHuman.Json, Optional.of(str), Optional.empty());
        } else {
            parseHumanSchemaJni(str);
            return new Schema(JsonOrHuman.Human, Optional.empty(), Optional.of(str));
        }

    }

    /** Specifies the schema format used. */
    public enum JsonOrHuman {
        /**
         * Cedar JSON schema format. See <a href=
         * "https://docs.cedarpolicy.com/schema/json-schema.html">https://docs.cedarpolicy.com/schema/json-schema.html</a>
         */
        Json,
        /**
         * Cedar schema format. See <a href=
         * "https://docs.cedarpolicy.com/schema/human-readable-schema.html">https://docs.cedarpolicy.com/schema/human-readable-schema.html</a>
         */
        Human
    }

    private static native String parseJsonSchemaJni(String schemaJson) throws InternalException, NullPointerException;

    private static native String parseHumanSchemaJni(String schemaText) throws InternalException, NullPointerException;
}
