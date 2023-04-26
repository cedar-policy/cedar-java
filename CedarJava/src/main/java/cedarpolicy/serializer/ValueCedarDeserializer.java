package cedarpolicy.serializer;

import cedarpolicy.model.exception.InvalidValueDeserializationException;
import cedarpolicy.value.CedarList;
import cedarpolicy.value.CedarMap;
import cedarpolicy.value.Decimal;
import cedarpolicy.value.EntityUID;
import cedarpolicy.value.IpAddress;
import cedarpolicy.value.PrimBool;
import cedarpolicy.value.PrimLong;
import cedarpolicy.value.PrimString;
import cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/** Deserialize Json to Value. */
public class ValueCedarDeserializer extends JsonDeserializer<Value> {
    private static final String ESCAPE_SEQ =
            "__expr"; // Not depricated yet but should never be passed from Cedar
    private static final String ENTITY_ESCAPE_SEQ = "__entity";
    private static final String EXTENSION_ESCAPE_SEQ = "__extn";

    private enum EscapeType {
        ENTITY,
        EXTENSION,
        UNRECOGNIZED
    }

    /** Deserialize Json to Value. */
    @Override
    public Value deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        final JsonNode node = parser.getCodec().readTree(parser);
        final ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        if (node.canConvertToLong()) {
            return new PrimLong(node.asLong());
        } else if (node.isBoolean()) {
            return new PrimBool(node.asBoolean());
        } else if (node.isTextual()) {
            return new PrimString(node.asText());
        } else if (node.isArray()) {
            CedarList myNode = new CedarList();
            Iterator<JsonNode> iter = node.elements();
            while (iter.hasNext()) {
                myNode.add(mapper.treeToValue(iter.next(), Value.class));
            }
            return myNode;
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
            // Do two passes, one to check if it is an escaped entity or extension and a second to
            // write into a
            // map
            EscapeType escapeType = EscapeType.UNRECOGNIZED;
            int count = 0;
            while (iter.hasNext()) {
                count++;
                Map.Entry<String, JsonNode> entry = iter.next();
                if (entry.getKey().equals(ENTITY_ESCAPE_SEQ)) {
                    escapeType = EscapeType.ENTITY;
                } else if (entry.getKey().equals(EXTENSION_ESCAPE_SEQ)) {
                    escapeType = EscapeType.EXTENSION;
                } else if (!entry.getKey()
                        .equals(ESCAPE_SEQ)) { // Not depricated yet, but we don't use this
                    // internally anymore. Will just be treated as a map.
                    throw new InvalidValueDeserializationException(
                            "Use __entity or __extn: " + node.toString());
                }
            }
            if (escapeType != EscapeType.UNRECOGNIZED) {
                if (count == 1) {
                    if (escapeType == EscapeType.ENTITY) {
                        JsonNode val = node.get(ENTITY_ESCAPE_SEQ);
                        if (!val.isTextual()) {
                            throw new InvalidValueDeserializationException(
                                    "Not textual node: " + node.toString());
                        }
                        return new EntityUID(val.textValue());
                    } else {
                        JsonNode val = node.get(EXTENSION_ESCAPE_SEQ);
                        JsonNode fn = val.get("fn");
                        if (!fn.isTextual()) {
                            throw new InvalidValueDeserializationException(
                                    "Not textual node: " + fn.toString());
                        }
                        JsonNode arg = val.get("arg");
                        if (!arg.isTextual()) {
                            throw new InvalidValueDeserializationException(
                                    "Not textual node: " + arg.toString());
                        }

                        if (fn.textValue().equals("ip")) {
                            return new IpAddress(arg.textValue());
                        } else if (fn.textValue().equals("decimal")) {
                            return new Decimal(arg.textValue());
                        } else {
                            throw new InvalidValueDeserializationException(
                                    "Invalid function type: " + fn.toString());
                        }
                    }
                } else {
                    throw new InvalidValueDeserializationException(
                            "More than one K,V pair with {__entity, __extn}: " + node.toString());
                }
            }
            CedarMap myMap = new CedarMap();
            iter = node.fields();
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                myMap.put(entry.getKey(), mapper.treeToValue(entry.getValue(), Value.class));
            }
            return myMap;
        } else {
            throw new InvalidValueDeserializationException(node.toString());
        }
    }
}
