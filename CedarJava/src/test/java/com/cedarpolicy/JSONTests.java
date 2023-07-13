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

package com.cedarpolicy;

import static com.cedarpolicy.CedarJson.objectReader;
import static com.cedarpolicy.CedarJson.objectWriter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.value.CedarList;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Test. JSON (de)serialization */
public class JSONTests {
    private static final String ESCAPE_SEQ = "__expr";

    private static void assertJSONEqual(JsonNode expectedJSON, Object obj) {
        String objJson = assertDoesNotThrow(() -> objectWriter().writeValueAsString(obj));
        assertEquals(expectedJSON.toString(), objJson);
    }

    /** Test. */
    @Test
    public void testAuthResult() {
        String src =
                "{ \"decision\":\"Allow\", \"diagnostics\": { \"reason\":[], \"errors\": [] } }";
        try {
            AuthorizationResponse r = objectReader().forType(AuthorizationResponse.class).readValue(src);
            assertTrue(r.isAllowed());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    /** Test. */
    @Test
    public void testQuery() {
        AuthorizationRequest q = new AuthorizationRequest("gandalf", "opens", "moria");
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        ObjectNode c = JsonNodeFactory.instance.objectNode();
        n.set("context", c);
        n.put("schema", JsonNodeFactory.instance.nullNode());
        n.put("principal", "gandalf");
        n.put("action", "opens");
        n.put("resource", "moria");
        assertJSONEqual(n, q);
    }

    /** Test. */
    @Test
    public void testPrimitiveLong() {
        PrimLong p = new PrimLong(3000000000L);
        JsonNode longJson = JsonNodeFactory.instance.numberNode(3000000000L);
        assertJSONEqual(longJson, p);
    }

    /** Test. */
    @Test
    public void testPrimitiveBool() {
        PrimBool p = new PrimBool(true);
        JsonNode boolJson = JsonNodeFactory.instance.booleanNode(true);
        assertJSONEqual(boolJson, p);

        p = new PrimBool(false);
        boolJson = JsonNodeFactory.instance.booleanNode(false);
        assertJSONEqual(boolJson, p);
    }

    /** Test. */
    @Test
    public void testPrimitiveString() {
        PrimString p = new PrimString("hello world");
        TextNode stringJSON = JsonNodeFactory.instance.textNode("hello world");
        assertJSONEqual(stringJSON, p);
    }

    /** Test. */
    @Test
    public void testEntityUID() {
        String text = "silver::\"jakob\"";
        EntityUID uid = new EntityUID(text);
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put(ESCAPE_SEQ, text);
        assertJSONEqual(n, uid);

        String invalidNamespace = "Us,er::\"alice\"";
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new EntityUID(invalidNamespace),
                        "Expected EntityUID() to throw, but it didn't");
        assertTrue(
                thrown.getMessage()
                        .contentEquals(
                                "Input string is not a valid EntityUID " + invalidNamespace));

        String invalidEID = "User::\"ali\"ce\"";
        thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new EntityUID(invalidEID),
                        "Expected EntityUID() to throw, but it didn't");
        assertTrue(
                thrown.getMessage()
                        .contentEquals("Input string is not a valid EntityUID " + invalidEID));

        String validEID = "User::\"ali\\\"ce\"";
        uid = new EntityUID(validEID);
        n = JsonNodeFactory.instance.objectNode();
        n.put(ESCAPE_SEQ, validEID);
        assertJSONEqual(n, uid);
    }

    /** Test. */
    @Test
    public void testLongEntityUID() {
        String text = "long::john::silver::\"donut\"";
        EntityUID uid = new EntityUID(text);
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put(ESCAPE_SEQ, text);
        assertJSONEqual(n, uid);
    }

    /** Test. */
    @Test
    public void testEmptyList() {
        CedarList l = new CedarList(new ArrayList<>());
        ArrayNode listJSON = JsonNodeFactory.instance.arrayNode();
        assertJSONEqual(listJSON, l);
    }

    /** Test. */
    @Test
    public void testList() {
        // Cedar
        Value[] values = new Value[2];
        values[0] = new PrimLong(5L);
        values[1] = new PrimBool(false);
        CedarList l = new CedarList(Arrays.asList(values));

        // JSON
        ArrayNode listJson = JsonNodeFactory.instance.arrayNode();
        listJson.add(5L);
        listJson.add(false);

        assertJSONEqual(listJson, l);
    }
}
