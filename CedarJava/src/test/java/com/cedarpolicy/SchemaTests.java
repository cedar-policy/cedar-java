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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.fasterxml.jackson.databind.JsonNode;

public class SchemaTests {
    @Test
    public void parseJsonSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrCedar.Json, "{}");
            Schema.parse(JsonOrCedar.Json, """
                    {
                        "Foo::Bar": {
                            "entityTypes": {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrCedar.Json, """
                    {
                        "": {
                            "entityTypes": {
                                "User": {
                                    "shape": {
                                        "type": "Record",
                                        "attributes": {
                                            "name": {
                                                "type": "String",
                                                "required": true
                                            },
                                            "age": {
                                                "type": "Long",
                                                "required": false
                                            }
                                        }
                                    }
                                },
                                "Photo": {
                                    "memberOfTypes": [ "Album" ]
                                },
                                "Album": {}
                            },
                            "actions": {
                                "view": {
                                    "appliesTo": {
                                        "principalTypes": ["User"],
                                        "resourceTypes": ["Photo", "Album"]
                                    }
                                }
                            }
                        }
                    }
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrCedar.Json, "{\"foo\": \"bar\"}");
            Schema.parse(JsonOrCedar.Json, "namespace Foo::Bar;");
        });
    }

    @Test
    public void parseCedarSchema() {
        assertDoesNotThrow(() -> {
            Schema.parse(JsonOrCedar.Cedar, "");
            Schema.parse(JsonOrCedar.Cedar, "namespace Foo::Bar {}");
            Schema.parse(JsonOrCedar.Cedar, """
                    entity User = {
                        name: String,
                        age?: Long,
                    };
                    entity Photo in Album;
                    entity Album;
                    action view
                      appliesTo { principal: [User], resource: [Album, Photo] };
                    """);
        });
        assertThrows(Exception.class, () -> {
            Schema.parse(JsonOrCedar.Cedar, """
                    {
                        "Foo::Bar": {
                            "entityTypes" {},
                            "actions": {}
                        }
                    }
                    """);
            Schema.parse(JsonOrCedar.Cedar, "namspace Foo::Bar;");
        });
    }

    @Test
    public void testToCedarFormat() throws InternalException {
        String cedarSchema = "entity User;";
        Schema cedarSchemaObj = new Schema(cedarSchema);
        String result = cedarSchemaObj.toCedarFormat();
        assertNotNull(result);
        assertEquals(cedarSchema, result);

        String jsonSchema = """
                {
                    "": {
                        "entityTypes": {
                            "User": {}
                        },
                        "actions": {}
                    }
                }
                """;
        Schema jsonSchemaObj = Schema.parse(JsonOrCedar.Json, jsonSchema);
        String convertedCedar = jsonSchemaObj.toCedarFormat();
        assertNotNull(convertedCedar);
        assertTrue(convertedCedar.contains("entity User"));

        Schema invalidSchema = new Schema(JsonOrCedar.Cedar, java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(InternalException.class, () -> {
            invalidSchema.toCedarFormat();
        });
    }

    @Test
    public void testToJsonFormat() throws Exception {
        String cedarSchema = "entity User;";
        Schema cedarSchemaObj = new Schema(cedarSchema);
        JsonNode jsonResult = cedarSchemaObj.toJsonFormat();
        assertNotNull(jsonResult);
        assertTrue(jsonResult.has(""));
        assertTrue(jsonResult.get("").has("entityTypes"));
        assertTrue(jsonResult.get("").get("entityTypes").has("User"));

        String jsonSchema = """
                {
                    "": {
                        "entityTypes": {
                            "User": {}
                        },
                        "actions": {}
                    }
                }
                """;
        Schema jsonSchemaObj = Schema.parse(JsonOrCedar.Json, jsonSchema);
        assertThrows(InternalException.class, () -> {
            jsonSchemaObj.toJsonFormat();
        });

        Schema invalidSchema = new Schema(JsonOrCedar.Cedar, java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(InternalException.class, () -> {
            invalidSchema.toJsonFormat();
        });
    }

}
