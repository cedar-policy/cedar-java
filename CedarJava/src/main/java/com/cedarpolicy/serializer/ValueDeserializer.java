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

package com.cedarpolicy.serializer;

import com.cedarpolicy.model.exception.DeserializationRecursionDepthException;
import com.cedarpolicy.model.exception.InvalidValueDeserializationException;
import com.cedarpolicy.value.CedarList;
import com.cedarpolicy.value.CedarMap;
import com.cedarpolicy.value.DateTime;
import com.cedarpolicy.value.Decimal;
import com.cedarpolicy.value.Duration;
import com.cedarpolicy.value.EntityIdentifier;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.IpAddress;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Unknown;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.value.functions.Offset;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/** Deserialize Json to Value. This is mostly an implementation detail, but you may need to modify it if you extend the
 * `Value` class. */
public class ValueDeserializer extends JsonDeserializer<Value> {
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
        try {
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
                // write into a map
                EscapeType escapeType = EscapeType.UNRECOGNIZED;
                int count = 0;
                while (iter.hasNext()) {
                    count++;
                    Map.Entry<String, JsonNode> entry = iter.next();
                    if (entry.getKey().equals(ENTITY_ESCAPE_SEQ)) {
                        escapeType = EscapeType.ENTITY;
                    } else if (entry.getKey().equals(EXTENSION_ESCAPE_SEQ)) {
                        escapeType = EscapeType.EXTENSION;
                    }
                }
                if (escapeType != EscapeType.UNRECOGNIZED) {
                    if (count == 1) {
                        if (escapeType == EscapeType.ENTITY) {
                            JsonNode val = node.get(ENTITY_ESCAPE_SEQ);
                            if (val.isObject() && val.has("id") && val.has("type")) {
                                int numFields = 0;
                                for (Iterator<String> it = val.fieldNames(); it.hasNext(); it.next()) {
                                    numFields++;
                                }
                                if (numFields == 2) {
                                    EntityIdentifier id = new EntityIdentifier(val.get("id").textValue());
                                    Optional<EntityTypeName> type = EntityTypeName.parse(val.get("type").textValue());
                                    if (type.isPresent()) {
                                        return new EntityUID(type.get(), id);
                                    } else {
                                        String msg = "Invalid Entity Type" + val.get("type").textValue();
                                        throw new InvalidValueDeserializationException(parser, msg, node.asToken(), Map.class);
                                    }
                                }
                            }
                            throw new InvalidValueDeserializationException(parser,
                                    "Not textual node: " + node.toString(), node.asToken(), Map.class);
                        } else {
                            JsonNode val = node.get(EXTENSION_ESCAPE_SEQ);
                            JsonNode fn = val.get("fn");
                            if (!fn.isTextual()) {
                                throw new InvalidValueDeserializationException(parser,
                                        "Not textual node: " + fn.toString(), node.asToken(), Map.class);
                            }
                            // Handle offset function first since it uses "args" instead of "arg"
                            if (fn.textValue().equals("offset")) {
                                return deserializeOffset(val, mapper, parser, node);
                            }
                            JsonNode arg = val.get("arg");
                            if (!arg.isTextual()) {
                                throw new InvalidValueDeserializationException(parser,
                                        "Not textual node: " + arg.toString(), node.asToken(), Map.class);
                            }
                            if (fn.textValue().equals("ip")) {
                                return new IpAddress(arg.textValue());
                            } else if (fn.textValue().equals("decimal")) {
                                return new Decimal(arg.textValue());
                            } else if (fn.textValue().equals("unknown")) {
                                return new Unknown(arg.textValue());
                            } else if (fn.textValue().equals("datetime")) {
                                return new DateTime(arg.textValue());
                            } else if (fn.textValue().equals("duration")) {
                                return new Duration(arg.textValue());
                            } else {
                                throw new InvalidValueDeserializationException(parser,
                                        "Invalid function type: " + fn.toString(), node.asToken(), Map.class);
                            }
                        }
                    } else {
                        throw new InvalidValueDeserializationException(parser,
                                "More than one K,V pair with {__entity, __extn}: " + node.toString(), node.asToken(), Map.class);
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
                throw new InvalidValueDeserializationException(parser, node.toString(), node.asToken(), Object.class);
            }
        } catch (StackOverflowError e) {
            throw new DeserializationRecursionDepthException("Stack overflow while deserializing value. " + e.toString());
        }
    }

    private Offset deserializeOffset(JsonNode val, ObjectMapper mapper, JsonParser parser, JsonNode node) throws IOException {

        JsonNode args = val.get("args");
        if (args == null || !args.isArray() || args.size() != 2) {
            String message = args == null ? "Offset missing 'args' field"
                    : !args.isArray() ? "Offset 'args' must be an array"
                            : "Offset requires exactly two arguments but got: " + args.size();
            throw new InvalidValueDeserializationException(parser, message, node.asToken(), Offset.class);
        }

        try {
            Value dateTimeValue = mapper.treeToValue(args.get(0), Value.class);
            Value durationValue = mapper.treeToValue(args.get(1), Value.class);

            if (!(dateTimeValue instanceof DateTime)) {
                throw new InvalidValueDeserializationException(parser,
                        "Offset first argument must be DateTime but got: " + dateTimeValue.getClass().getSimpleName(),
                        node.asToken(), Offset.class);
            }

            if (!(durationValue instanceof Duration)) {
                throw new InvalidValueDeserializationException(parser,
                        "Offset second argument must be Duration but got: " + durationValue.getClass().getSimpleName(),
                        node.asToken(), Offset.class);
            }

            return new Offset((DateTime) dateTimeValue, (Duration) durationValue);

        } catch (IOException e) {
            throw new InvalidValueDeserializationException(parser,
                    "Failed to deserialize Offset arguments: " + e.getMessage(), node.asToken(), Offset.class);
        }
    }
}
