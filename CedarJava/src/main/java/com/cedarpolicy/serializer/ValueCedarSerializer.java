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

package com.cedarpolicy.serializer;

import com.cedarpolicy.model.exception.InvalidValueDeserializationException;
import com.cedarpolicy.value.CedarList;
import com.cedarpolicy.value.CedarMap;
import com.cedarpolicy.value.Decimal;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.IpAddress;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
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
            // main.java.com.cedarpolicy.model.value or that you convert your class to a CedarMap
            throw new InvalidValueDeserializationException(
                    "Error serializing Value: " + value.toString());
        }
    }
}
