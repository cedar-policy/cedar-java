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

package cedarpolicy.model.schema;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Objects;

/** Represent a schema. */
public final class Schema {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // The schema after being parsed as a JSON object.
    @JsonValue private final JsonNode schemaJson;

    /**
     * Build a Schema from a string containing the JSON source for the model. This constructor will
     * fail with an exception if the string does not parse as JSON, but it does not check that the
     * parsed JSON object represents a valid schema.
     *
     * @param schemaJson List of EntityTypes.
     * @throws java.io.IOException When any errors are encountered while parsing the authorization
     *     model json string into json node.
     */
    @SuppressFBWarnings
    public Schema(String schemaJson) throws IOException {
        if (schemaJson == null) {
            throw new NullPointerException("schemaJson");
        }

        this.schemaJson = OBJECT_MAPPER.readTree(schemaJson);
    }

    /**
     * Build a Schema from a json node. This does not check that the parsed JSON object represents a
     * valid schema.
     *
     * @param schemaJson List of EntityTypes.
     */
    @SuppressFBWarnings
    public Schema(JsonNode schemaJson) {
        if (schemaJson == null) {
            throw new NullPointerException("schemaJson");
        }

        this.schemaJson = schemaJson;
    }

    /** Equals. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Schema)) {
            return false;
        }

        final Schema other = (Schema) o;
        return schemaJson.equals(other.schemaJson);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(schemaJson);
    }

    /** Readable string representation. */
    public String toString() {
        return "Schema(schemaJson=" + schemaJson + ")";
    }
}
