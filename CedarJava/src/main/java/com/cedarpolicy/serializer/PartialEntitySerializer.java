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

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;
import com.cedarpolicy.model.entity.PartialEntity;
import com.cedarpolicy.value.EntityUID;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.stream.Collectors;

/** Serialize a partial entity. An omitted field is the wire encoding of unknown. */
@Experimental(ExperimentalFeature.TYPE_AWARE_PARTIAL_EVALUATION)
public class PartialEntitySerializer extends JsonSerializer<PartialEntity> {

    /** Serialize a partial entity. */
    @Override
    public void serialize(
            PartialEntity entity, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("uid", entity.getEUID().asJson());
        if (entity.getAttrs().isPresent()) {
            jsonGenerator.writeObjectField("attrs", entity.getAttrs().get());
        }
        if (entity.getParents().isPresent()) {
            jsonGenerator.writeObjectField("parents",
                    entity.getParents().get().stream().map(EntityUID::asJson).collect(Collectors.toSet()));
        }
        if (entity.getTags().isPresent()) {
            jsonGenerator.writeObjectField("tags", entity.getTags().get());
        }
        jsonGenerator.writeEndObject();
    }
}
