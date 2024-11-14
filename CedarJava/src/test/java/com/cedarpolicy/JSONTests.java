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

import static com.cedarpolicy.CedarJson.objectReader;
import static com.cedarpolicy.CedarJson.objectWriter;
import static org.junit.jupiter.api.Assertions.*;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.PartialAuthorizationRequest;
import com.cedarpolicy.model.PartialAuthorizationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.value.CedarList;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

/** Test. JSON (de)serialization */
public class JSONTests {
    private static final String ENTITY_ESCAPE_SEQ = "__entity";

    private static void assertJSONEqual(JsonNode expectedJSON, Object obj) {
        String objJson = assertDoesNotThrow(() -> objectWriter().writeValueAsString(obj));
        assertEquals(expectedJSON.toString(), objJson);
    }

    /** Test. */
    @Test
    public void testAuthSuccessResponse() {
        String src =
                "{ \"response\": { \"decision\":\"allow\", \"diagnostics\": { \"reason\":[], \"errors\": [] } } }";
        try {
            AuthorizationResponse r = objectReader().forType(AuthorizationResponse.class).readValue(src);
            assertTrue(r.success.get().isAllowed());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testAuthConcretePartialResponse() {
        String src =
                "{ \"response\": { \"decision\":\"allow\", \"satisfied\": [], \"errored\": [\"p0\"], \"mayBeDetermining\": [], \"mustBeDetermining\": [\"p1\"], \"residuals\": {\"p2\": 3}, \"nontrivialResiduals\": [] } }";
        try {
            PartialAuthorizationResponse r = objectReader().forType(PartialAuthorizationResponse.class).readValue(src);
            assertTrue(r.success.orElseThrow().getDecision() == Decision.Allow);
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    @Test
    public void testAuthResidualPartialResponse() {
        final String policy = "{ \"effect\": \"permit\", \"principal\": { \"op\": \"All\" }, \"action\": { \"op\": \"All\" }, \"resource\": { \"op\": \"All\" }, \"conditions\": [ { \"kind\": \"when\", \"body\": { \"==\": { \"left\": { \"unknown\": [ { \"Value\": \"principal\" } ] }, \"right\": { \"Value\": { \"__entity\": { \"type\": \"User\", \"id\": \"alice\" } } } } } } ] }";
        final String src = "{ \"response\": { \"decision\":\"allow\", \"satisfied\": [], \"errored\": [\"p0\"], \"mayBeDetermining\": [], \"mustBeDetermining\": [\"p1\"], \"residuals\": {\"p0\": " + policy + " }, \"nontrivialResiduals\": [] } }";
        try {
            PartialAuthorizationResponse r = objectReader().forType(PartialAuthorizationResponse.class).readValue(src);
            var residuals = r.success.orElseThrow().getResiduals();
            assertEquals(1, residuals.size());
            assertEquals("p0", residuals.entrySet().iterator().next().getKey());
            assertJSONEqual(CedarJson.objectMapper().readTree(policy),
                    residuals.entrySet().iterator().next().getValue());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    /** Test. */
    @Test
    public void testRequest() {
        var gandalf = new EntityUID(EntityTypeName.parse("Wizard").get(), "gandalf");
        var opens = new EntityUID(EntityTypeName.parse("Action").get(), "opens");
        var moria = new EntityUID(EntityTypeName.parse("Mines").get(), "moria");
        AuthorizationRequest q = new AuthorizationRequest(gandalf, opens, moria, new HashMap<String, Value>());
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("context", JsonNodeFactory.instance.objectNode());
        n.set("schema", JsonNodeFactory.instance.nullNode());
        n.set("principal", buildEuidObject("Wizard", "gandalf"));
        n.set("action", buildEuidObject("Action", "opens"));
        n.set("resource", buildEuidObject("Mines", "moria"));
        n.set("validateRequest", JsonNodeFactory.instance.booleanNode(false));
        assertJSONEqual(n, q);
    }

    @Test
    public void testPartialRequest() {
        var opens = new EntityUID(EntityTypeName.parse("Action").get(), "opens");
        var moria = new EntityUID(EntityTypeName.parse("Mines").get(), "moria");
        var q = PartialAuthorizationRequest.builder()
            .action(opens)
            .resource(moria)
            .emptyContext()
            .build();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("action", buildEuidObject("Action", "opens"));
        n.set("resource", buildEuidObject("Mines", "moria"));
        n.set("context", JsonNodeFactory.instance.objectNode());
        n.set("validateRequest", JsonNodeFactory.instance.booleanNode(false));
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
        EntityUID uid = EntityUID.parse(text).get();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        ObjectNode inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", "jakob");
        inner.put("type", "silver");
        n.replace(ENTITY_ESCAPE_SEQ, inner);
        assertJSONEqual(n, uid);

        String invalidNamespace = "Us,er::\"alice\"";
        assertFalse(EntityUID.parse(invalidNamespace).isPresent());

        String invalidEID = "User::\"ali\"ce\"";
        assertFalse(EntityUID.parse(invalidEID).isPresent());

        String validEID = "User::\"ali\\\"ce\"";
        uid = EntityUID.parse(validEID).get();
        n = JsonNodeFactory.instance.objectNode();
        inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", "ali\"ce");
        inner.put("type", "User");
        n.replace(ENTITY_ESCAPE_SEQ, inner);
        assertJSONEqual(n, uid);

        String weirdType = "a";
        String weirdId = "";
        String weirdEID = weirdType + "::\"" + weirdId + "\"";
        uid = EntityUID.parse(weirdEID).get();
        inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", weirdId);
        inner.put("type", weirdType);
        n.replace(ENTITY_ESCAPE_SEQ, inner);
        assertJSONEqual(n, uid);
    }

    private ObjectNode buildEuidObject(String type, String id) {
        var n = JsonNodeFactory.instance.objectNode();
        var inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", id);
        inner.put("type", type);
        n.replace(ENTITY_ESCAPE_SEQ, inner);
        return n;
    }

    /** Test. */
    @Test
    public void testLongEntityUID() {
        String text = "long::john::silver::\"donut\"";
        EntityUID uid = EntityUID.parse(text).get();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        ObjectNode inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", "donut");
        inner.put("type", "long::john::silver");
        n.replace(ENTITY_ESCAPE_SEQ, inner);
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

    /** Tests deserialization of value that causes stack overflow */
    @Test
    public void testDeserializationStackOverflow() {
        String json = "{\"\":" + "[".repeat(1024) + "]".repeat(1024) + "}";
        try {
            CedarJson.objectMapper().readValue(json, Value.class);
        } catch (JsonProcessingException e) {
            System.out.println("class: " + e.getClass());
            assertTrue(e instanceof StreamConstraintsException);
        }
    }
}
