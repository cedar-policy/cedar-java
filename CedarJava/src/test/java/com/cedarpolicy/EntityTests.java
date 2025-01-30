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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.cedarpolicy.value.*;
import com.cedarpolicy.model.entity.Entity;

import com.fasterxml.jackson.databind.JsonNode;

public class EntityTests {

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
    public void toJsonTests() {
        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));
        Entity principal = new Entity(principalType.of("Alice"), attrs, parents);
        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}]}");

        Entity parentlessPrincipal = new Entity(principalType.of("Alice"), attrs, new HashSet<>());
        JsonNode parentlessEntityJson = Assertions.assertDoesNotThrow(() -> {
            return parentlessPrincipal.toJsonValue();
        });

        assertEquals(parentlessEntityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[]}");

        Entity principalWithEuid = new Entity(principalType.of("Alice"), new HashMap<>(), new HashSet<>());
        JsonNode entityWithEuidJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithEuid.toJsonValue();
        });

        assertEquals(entityWithEuidJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{},"
                        + "\"parents\":[]}");

    }

    @Test
    public void toJsonWithTagsTests() {
        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));

        PrimString strTag = new PrimString("strTagValue");
        HashMap<String, Value> tags = new HashMap<>();
        tags.put("tag", strTag);

        Entity principal = new Entity(principalType.of("Alice"), attrs, parents, tags);

        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}],"
                        + "\"tags\":{\"tag\":\"strTagValue\"}}");
    }

    public void toJsonMultipleAttributesTests() {
        HashMap<String, Value> attrs = new HashMap<>();
        PrimString stringAttr = new PrimString("stringAttrValue");
        attrs.put("stringAttr", stringAttr);

        PrimString stringAttr2 = new PrimString("stringAttrValue2");
        attrs.put("stringAttr2", stringAttr2);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));
        Entity principal = new Entity(principalType.of("Alice"), attrs, parents);
        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonValue();
        });

        String entityJsonStr = entityJson.toString();
        boolean entityJsonIsExpected = entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                + "\"attrs\":{\"stringAttr\":\"stringAttrValue\",\"stringAttr2\":\"stringAttrValue2\"},"
                + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}]}")
                || entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr2\":\"stringAttrValue2\",\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}]}");

        assertTrue(entityJsonIsExpected, entityJsonStr);
    }

    public void toJsonMultipleParentsTests() {
        HashMap<String, Value> attrs = new HashMap<>();
        PrimString stringAttr = new PrimString("stringAttrValue");
        attrs.put("stringAttr", stringAttr);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Alice"));
        parents.add(principalType.of("Bob"));
        Entity principal = new Entity(principalType.of("Alice"), attrs, parents);
        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonValue();
        });

        String entityJsonStr = entityJson.toString();
        boolean entityJsonIsExpected = entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                + "\"parents\":[{\"type\":\"User\",\"id\":\"Alice\"},{\"type\":\"User\",\"id\":\"Bob\"}]}")
                || entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\",{\"type\":\"User\",\"id\":\"Alice\"}}]}");

        assertTrue(entityJsonIsExpected, entityJsonStr);
    }

    public void toJsonMultipleTagsTests() {
        HashMap<String, Value> attrs = new HashMap<>();
        PrimString stringAttr = new PrimString("stringAttrValue");
        attrs.put("stringAttr", stringAttr);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Alice"));

        HashMap<String, Value> tags = new HashMap<>();
        PrimString strTag = new PrimString("strTagValue");
        tags.put("tag", strTag);
        PrimBool boolTag = new PrimBool(true);
        tags.put("tag2", boolTag);

        Entity principal = new Entity(principalType.of("Alice"), attrs, parents, tags);
        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonValue();
        });

        String entityJsonStr = entityJson.toString();
        boolean entityJsonIsExpected = entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}],"
                + "\"tags\":{\"tag\":\"strTagValue\",\"tag2\":\"true\"}}")
                || entityJsonStr.equals("{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}],"
                        + "\"tags\":{\"tag2\":\"tue\",\"tag\":\"strTagValue\"}}");

        assertTrue(entityJsonIsExpected, entityJsonStr);
    }

    @Test
    public void toJsonAllTypesTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        EntityUID aliceType = principalType.of("Alice");

        HashMap<String, Value> attrs = new HashMap<>();
        PrimBool boolAttr = new PrimBool(false);
        attrs.put("boolAttr", boolAttr);

        final Entity principalWithBool = new Entity(aliceType, attrs, new HashSet<>());
        JsonNode entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithBool.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"boolAttr\":false},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        PrimLong longAttr = new PrimLong(5);
        attrs.put("longAttr", longAttr);

        final Entity principalWithLong = new Entity(aliceType, attrs, new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithLong.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"longAttr\":5},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        IpAddress ipAttr = new IpAddress("0.1.2.3");
        attrs.put("ipAttr", ipAttr);

        final Entity principalWithIpAddress = new Entity(aliceType, attrs, new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithIpAddress.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"ipAttr\":{\"__extn\":{\"fn\":\"ip\",\"arg\":\"0.1.2.3\"}}},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        EntityTypeName typeName = EntityTypeName.parse("User").get();
        EntityIdentifier id = new EntityIdentifier("testId");

        EntityUID entityAttr = new EntityUID(typeName, id);
        attrs.put("entityAttr", entityAttr);

        final Entity principalWithEntity = new Entity(aliceType, attrs, new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithEntity.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"entityAttr\":{\"__entity\":{\"type\":\"User\",\"id\":\"testId\"}}},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        Decimal decimalAttr = new Decimal("1.234");
        attrs.put("decimalAttr", decimalAttr);

        final Entity principalWithDecimal = new Entity(aliceType, attrs,
                new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithDecimal.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"decimalAttr\":{\"__extn\":{\"fn\":\"decimal\",\"arg\":\"1.234\"}}},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        List<Value> valueList = new ArrayList<Value>();
        valueList.add(boolAttr);
        CedarList listAttr = new CedarList(valueList);
        attrs.put("listAttr", listAttr);

        final Entity principalWithList = new Entity(aliceType, attrs, new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithList.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"listAttr\":[false]},"
                        + "\"parents\":[]}");

        attrs = new HashMap<>();
        HashMap<String, Value> valueMap = new HashMap<String, Value>();
        valueMap.put("boolAttr", boolAttr);
        CedarMap mapAttr = new CedarMap(valueMap);
        attrs.put("mapAttr", mapAttr);

        final Entity principalWithMap = new Entity(aliceType, attrs, new HashSet<>());
        entityJson = Assertions.assertDoesNotThrow(() -> {
            return principalWithMap.toJsonValue();
        });

        assertEquals(entityJson.toString(),
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"mapAttr\":{\"boolAttr\":false}},"
                        + "\"parents\":[]}");
    }

    @Test
    public void toJsonStringTests() {
        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);

        EntityTypeName principalType = EntityTypeName.parse("User").get();

        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));

        PrimString longTag = new PrimString("longTagValue");
        HashMap<String, Value> tags = new HashMap<>();
        tags.put("tag", longTag);

        Entity principal = new Entity(principalType.of("Alice"), attrs, parents, tags);

        String entityJson = Assertions.assertDoesNotThrow(() -> {
            return principal.toJsonString();
        });

        assertEquals(entityJson,
                "{\"uid\":{\"type\":\"User\",\"id\":\"Alice\"},"
                        + "\"attrs\":{\"stringAttr\":\"stringAttrValue\"},"
                        + "\"parents\":[{\"type\":\"User\",\"id\":\"Bob\"}],"
                        + "\"tags\":{\"tag\":\"longTagValue\"}}");
    }
}
