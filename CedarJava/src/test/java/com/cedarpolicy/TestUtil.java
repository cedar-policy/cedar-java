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

package com.cedarpolicy;

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

/** Utils to help with tests. */
public final class TestUtil {
    private TestUtil() {
    }

    /**
     * Load schema file.
     *
     * @param schemaFile Schema file name
     */
    public static Schema loadSchemaResource(String schemaFile) {
        try {
            String text = new String(Files.readAllBytes(
                    Paths.get(
                            ValidationTests.class.getResource(schemaFile).toURI())),
                    StandardCharsets.UTF_8);
            return new Schema(JsonOrCedar.Json, Optional.of(text), Optional.empty());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test schema file " + schemaFile, e);
        }
    }
}
