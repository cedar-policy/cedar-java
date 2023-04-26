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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Map;

/** Serialize Value to Json. */
public class ValueCedarSerializer extends JsonSerializer<Value> {
    private static final String ESCAPE_SEQ = "__expr";
    private static final String ENTITY_ESCAPE_SEQ = "__entity";
    private static final String EXTENSION_ESCAPE_SEQ = "__extn";

    /** Serialize Value to Json. */
    @Override
    public void serialize(
            Value value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        if (value instanceof EntityUID) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName(ESCAPE_SEQ);
            jsonGenerator.writeString(value.toString());
            jsonGenerator.writeEndObject();
        } else if (value instanceof PrimString) {
            jsonGenerator.writeString(value.toString());
        } else if (value instanceof PrimBool) {
            jsonGenerator.writeBoolean(((PrimBool) value).value);
        } else if (value instanceof PrimLong) {
            jsonGenerator.writeNumber(((PrimLong) value).value);
        } else if (value instanceof CedarList) {
            jsonGenerator.writeStartArray();
            for (Value v : ((CedarList) value).list) {
                jsonGenerator.writeObject(v);
            }
            jsonGenerator.writeEndArray();
        } else if (value instanceof CedarMap) {
            jsonGenerator.writeStartObject();
            for (Map.Entry<String, Value> entry : ((CedarMap) value).map.entrySet()) {
                jsonGenerator.writeObjectField(entry.getKey(), entry.getValue());
            }
            jsonGenerator.writeEndObject();
        } else if (value instanceof IpAddress) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName(EXTENSION_ESCAPE_SEQ);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName("fn");
            jsonGenerator.writeString("ip");
            jsonGenerator.writeFieldName("arg");
            jsonGenerator.writeString(value.toString());
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndObject();
        } else if (value instanceof Decimal) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName(EXTENSION_ESCAPE_SEQ);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeFieldName("fn");
            jsonGenerator.writeString("decimal");
            jsonGenerator.writeFieldName("arg");
            jsonGenerator.writeString(value.toString());
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndObject();
        } else {
            // It is recommended that you extend the Value classes in
            // main.java.cedarpolicy.model.value or that you convert your class to a CedarMap
            throw new InvalidValueDeserializationException(
                    "Error serializing Value: " + value.toString());
        }
    }
}
