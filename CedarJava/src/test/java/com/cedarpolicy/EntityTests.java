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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cedarpolicy.value.*;
import com.cedarpolicy.model.entity.Entity;
import static com.cedarpolicy.CedarJson.objectWriter;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

public class EntityTests {
    private static final String TEST_RESOURCES_DIR = "src/test/resources/";

    @Test
    public void getAttrTests() {
        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Entity principal = new Entity(principalType.of("Alice"), attrs, new HashSet<>());

        // Test valid attribute key and value
        assertEquals(principal.getAttr("stringAttr"), stringAttr);

        // Test invalid attribute key
        assertThrows(IllegalArgumentException.class, () -> {
            principal.getAttr(null);
        });

        // Test key not found
        assertEquals(principal.getAttr("decimalAttr"), null);
    }

    @Test
    public void newWithEntityUIDTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Entity principal = new Entity(principalType.of("Alice"));

        // Test the Entity's uid
        assertEquals(principal.getEUID(), principalType.of("Alice"));

        // Test that a key is not found
        assertEquals(principal.getAttr("stringAttr"), null);

        // Test the Entity's parents
        assertEquals(principal.getParents().size(), 0);
    }

    @Test
    public void newWithoutAttributesTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));

        Entity principal = new Entity(principalType.of("Alice"), parents);

        // Test the Entity's uid
        assertEquals(principal.getEUID(), principalType.of("Alice"));

        // Test that a key is not found
        assertEquals(principal.getAttr("stringAttr"), null);

        // Test the Entity's parents
        assertEquals(principal.getParents(), parents);
    }

    public void givenValidJSONStringParseReturns() throws JsonProcessingException {
        String validEntityJson = """
                {"uid":{"type":"Photo","id":"pic01"},
                "attrs":{
                    "dummyIP": {"__extn":{"fn":"ip","arg":"192.168.1.100"}},
                    "dummyUser": {"__entity":{"type":"User","id":"Alice"}},
                    "nestedAttr":{
                            "managerName": "Someone",
                            "skip":{
                                "name": "something",
                                "who": {"__entity":{"type":"User","id":"Alice"}}
                            }
                        }
                    },
                "parents":[{"type":"Photo","id":"pic01"}],
                "tags": {
                        "dummyTagIP": {"__extn":{"fn":"ip","arg":"192.168.1.100"}},
                        "dummyTagUser": {"__entity":{"type":"User::Tag","id":"Alice"}},
                        "nestedTagAttr":{
                            "managerTagName": "Someone",
                            "skipTag":{
                                "name": "somethingTag",
                                "who": {"__entity":{"type":"UserFromStringTag","id":"AliceTag"}}
                            }
                        }
                    }
                }
                """;

        Entity entity = Entity.parse(validEntityJson);
        String jsonRepresentation = objectWriter().writeValueAsString(entity);
        String expectedRepresentation = "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"},"
                + "\"attrs\":{\"dummyIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"192.168.1.100\"}},"
                + "\"nestedAttr\":{\"skip\":{\"name\":\"something\",\"who\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User\"}}},"
                + "\"managerName\":\"Someone\"},\"dummyUser\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User\"}}},"
                + "\"parents\":[{\"type\":\"Photo\",\"id\":\"pic01\"}],"
                + "\"tags\":{\"dummyTagIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"192.168.1.100\"}},"
                + "\"nestedTagAttr\":{\"skipTag\":{\"name\":\"somethingTag\","
                + "\"who\":{\"__entity\":{\"id\":\"AliceTag\",\"type\":\"UserFromStringTag\"}}},\"managerTagName\":\"Someone\"},"
                + "\"dummyTagUser\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User::Tag\"}}}}";

        assertEquals(expectedRepresentation, jsonRepresentation);

        validEntityJson = """
                {"uid":{"type":"Photo","id":"pic01"},
                "attrs":{},
                "parents":[]}
                """;
        entity = Entity.parse(validEntityJson);
        jsonRepresentation = objectWriter().writeValueAsString(entity);
        expectedRepresentation = "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"},"
                + "\"attrs\":{},"
                + "\"parents\":[],"
                + "\"tags\":{}}";

        assertEquals(expectedRepresentation, jsonRepresentation);
    }

    @Test
    public void givenInvalidJSONStringParseThrows() throws JsonProcessingException {
        String invalidEntityJson = """
                {"uid":{"type":"Photo","id":"pic01"}}
                """;

        assertThrows(JsonProcessingException.class, () -> {
            Entity.parse(invalidEntityJson);
        });

        String invalidEntityJson2 = """
                {"uid":{"type":"Photo","id":"pic01"}},
                "parents":{},
                "attrs":[]
                """;

        assertThrows(JsonProcessingException.class, () -> {
            Entity.parse(invalidEntityJson2);
        });

        String invalidEntityJson3 = """
                {"uid":{"type":"Photo","id":"pic01"}},
                "parents":[],
                "attrs":{},
                "tags":[]
                """;

        assertThrows(JsonProcessingException.class, () -> {
            Entity.parse(invalidEntityJson3);
        });
    }

    public void givenValidJSONFileParseReturns() throws JsonProcessingException, IOException {
        Entity entity = Entity.parse(Path.of(TEST_RESOURCES_DIR + "valid_entity.json"));
        String jsonRepresentation = objectWriter().writeValueAsString(entity);
        String expectedRepresentation = "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"},"
                + "\"attrs\":{\"dummyIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"192.168.1.100\"}},"
                + "\"nestedAttr\":{\"skip\":{\"name\":\"something\",\"who\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User\"}}},"
                + "\"managerName\":\"Someone\"},\"dummyUser\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User\"}}},"
                + "\"parents\":[{\"type\":\"Photo\",\"id\":\"pic01\"}],"
                + "\"tags\":{\"dummyTagIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"192.168.1.100\"}},"
                + "\"nestedTagAttr\":{\"skipTag\":{\"name\":\"somethingTag\","
                + "\"who\":{\"__entity\":{\"id\":\"AliceTag\",\"type\":\"UserFromStringTag\"}}},\"managerTagName\":\"Someone\"},"
                + "\"dummyTagUser\":{\"__entity\":{\"id\":\"Alice\",\"type\":\"User::Tag\"}}}}";

        assertEquals(expectedRepresentation, jsonRepresentation);
    }

    public void givenInvalidJSONFileParseThrows() throws JsonProcessingException, IOException {
        assertThrows(JsonProcessingException.class, () -> {
            Entity.parse(Path.of(TEST_RESOURCES_DIR + "invalid_entity.json"));
        });
    }
}
