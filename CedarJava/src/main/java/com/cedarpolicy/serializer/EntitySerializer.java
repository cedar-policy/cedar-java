/*
 * Copyright 2022-2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.value.EntityUID;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.stream.Collectors;

/** Serialize an entity. */
public class EntitySerializer extends JsonSerializer<Entity> {

    /** Serialize an entity. */
    @Override
    public void serialize(
            Entity entity, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("uid", entity.getEUID().asJson());
        jsonGenerator.writeObjectField("attrs", entity.attrs);
        jsonGenerator.writeObjectField("parents",
                entity.getParents().stream().map(EntityUID::asJson).collect(Collectors.toSet()));
        jsonGenerator.writeEndObject();
    }
}
