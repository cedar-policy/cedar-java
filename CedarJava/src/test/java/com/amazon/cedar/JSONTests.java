package cedarpolicy;

import static cedarpolicy.CedarJson.objectMapper;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cedarpolicy.model.AuthorizationQuery;
import cedarpolicy.model.AuthorizationResult;
import cedarpolicy.value.CedarList;
import cedarpolicy.value.EntityUID;
import cedarpolicy.value.PrimBool;
import cedarpolicy.value.PrimLong;
import cedarpolicy.value.PrimString;
import cedarpolicy.value.Value;
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
        String objJson = assertDoesNotThrow(() -> objectMapper().writeValueAsString(obj));
        assertEquals(expectedJSON.toString(), objJson);
    }

    /** Test. */
    @Test
    public void testAuthResult() {
        String src =
                "{ \"decision\":\"Allow\", \"diagnostics\": { \"reason\":[], \"errors\": [] } }";
        try {
            AuthorizationResult r = objectMapper().readValue(src, AuthorizationResult.class);
            assertTrue(r.isAllowed());
        } catch (JsonProcessingException e) {
            fail(e);
        }
    }

    /** Test. */
    @Test
    public void testQuery() {
        AuthorizationQuery q = new AuthorizationQuery("gandalf", "opens", "moria");
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
