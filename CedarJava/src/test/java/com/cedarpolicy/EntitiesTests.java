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
import com.cedarpolicy.model.entity.Entities;
import static com.cedarpolicy.CedarJson.objectWriter;

import org.skyscreamer.jsonassert.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class EntitiesTests {
    private static final String TEST_RESOURCES_DIR = "src/test/resources/";

    @Test
    public void givenValidEntitySetConstructorConstructs() {

        Entity alice = new Entity(EntityUID.parse("User::\"Alice\"").get());
        Set<EntityUID> parentAlice = new HashSet<>();
        parentAlice.add(alice.getEUID());

        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);

        Entity aliceChild = new Entity(EntityUID.parse("User::\"Alice_child\"").get(), attrs, parentAlice);

        Set<Entity> entitySet = new HashSet<>();
        entitySet.add(aliceChild);
        entitySet.add(alice);

        Entities entities = new Entities(entitySet);

        assertEquals(entitySet, entities.getEntities());
    }

    @Test
    public void givenValidJSONStringParseReturns() throws JsonProcessingException {
        String validEntitiesJson = """
                [
                    {"uid":{"type":"Photo","id":"pic02"},"parents":[{"type":"PhotoParent","id":"picParent"}],
                    "attrs":{"dummyIP": {"__extn":{"fn":"ip","arg":"199.168.1.130"}}}},
                    {"uid":{"type":"Photo","id":"pic01"},"parents":[{"type":"Photo","id":"pic02"}],"attrs":{}}
                ]
                """;

        String expectedRepresentation = "{\"entities\":[" + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic02\"},"
                + "\"attrs\":{\"dummyIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"199.168.1.130\"}}},"
                + "\"parents\":[{\"type\":\"PhotoParent\",\"id\":\"picParent\"}]," + "\"tags\":{}},"
                + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"}," + "\"attrs\":{},"
                + "\"parents\":[{\"type\":\"Photo\",\"id\":\"pic02\"}]," + "\"tags\":{}}]}";

        Entities entities = Entities.parse(validEntitiesJson);
        String actualRepresentation = objectWriter().writeValueAsString(entities);

        JSONAssert.assertEquals(expectedRepresentation, actualRepresentation, JSONCompareMode.NON_EXTENSIBLE);

        validEntitiesJson = """
                [
                    {"uid":{"type":"Photo","id":"pic01"},"parents":[],"attrs":{}},
                    {"uid":{"type":"Photo","id":"pic02"},"parents":[],"attrs":{}}
                ]
                """;

        expectedRepresentation = "{\"entities\":[" + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"}," + "\"attrs\":{},"
                + "\"parents\":[]," + "\"tags\":{}}," + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic02\"},"
                + "\"attrs\":{}," + "\"parents\":[]," + "\"tags\":{}}]}";

        entities = Entities.parse(validEntitiesJson);
        actualRepresentation = objectWriter().writeValueAsString(entities);

        JSONAssert.assertEquals(expectedRepresentation, actualRepresentation, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void givenInvalidJSONStringParseThrows() throws JsonProcessingException {
        String invalidEntityJson = """
                [{"uid":{"type":"Photo","id":"pic01"}},
                {"uid":{"type":"Photo","id":"pic02"},"parents":[],"attrs":{}}]
                """;

        assertThrows(JsonProcessingException.class, () -> {
            Entities.parse(invalidEntityJson);
        });

        String invalidEntityJson2 = """
                [{"uid":{"type":"Photo","id":"pic02"}, "parents":[{"parent_id":"Alice"}]},
                {"uid":{"type":"Photo","id":"pic01"},"parents":[],"attrs":{}}]
                """;

        assertThrows(JsonProcessingException.class, () -> {
            Entities.parse(invalidEntityJson2);
        });
    }

    @Test
    public void givenValidJSONFileParseReturns() throws JsonProcessingException, IOException {
        Entities entities = Entities.parse(Path.of(TEST_RESOURCES_DIR + "valid_entities.json"));
        String actualRepresentation = objectWriter().writeValueAsString(entities);
        String expectedRepresentation = "{\"entities\":[" + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic02\"},"
                + "\"attrs\":{\"dummyIP\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"199.168.1.130\"}}},"
                + "\"parents\":[{\"type\":\"PhotoParent\",\"id\":\"picParent\"}]," + "\"tags\":{}},"
                + "{\"uid\":{\"type\":\"Photo\",\"id\":\"pic01\"}," + "\"attrs\":{},"
                + "\"parents\":[{\"type\":\"Photo\",\"id\":\"pic02\"}]," + "\"tags\":{}}]}";

        JSONAssert.assertEquals(expectedRepresentation, actualRepresentation, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void givenInvalidJSONFileParseThrows() throws JsonProcessingException, IOException {
        assertThrows(JsonProcessingException.class, () -> {
            Entities.parse(Path.of(TEST_RESOURCES_DIR + "invalid_entities.json"));
        });
    }
}
