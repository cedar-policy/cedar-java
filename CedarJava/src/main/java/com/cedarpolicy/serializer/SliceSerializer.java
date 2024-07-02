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

import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.slice.Slice;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Serialize a slice. Only used internally by CedarJson */
public class SliceSerializer extends JsonSerializer<Slice> {

    /** Serialize a slice. */
    @Override
    public void serialize(
            Slice slice, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("policies", slice.getPolicies());
        jsonGenerator.writeObjectField(
                "entities", convertEntitiesToJsonEntities(slice.getEntities()));
        jsonGenerator.writeObjectField("templates", slice.getTemplates());
        jsonGenerator.writeObjectField(
                "templateInstantiations", slice.getTemplateLinks());
        jsonGenerator.writeEndObject();
    }

    private static class JsonEntity {
        /** Entity uid for the entity. */
        @SuppressFBWarnings public final JsonEUID uid;

        /** Entity attributes, where the value string is a Cedar literal value. */
        @SuppressFBWarnings public final Map<String, Value> attrs;

        /** Set of direct parent entities of this entity. */
        public final Set<JsonEUID> parents;

        JsonEntity(Entity e) {
            this.uid = e.getEUID().asJson();
            this.attrs = e.attrs;
            this.parents = e.getParents().stream().map(euid -> euid.asJson()).collect(Collectors.toSet());
        }
    }

    Set<JsonEntity> convertEntitiesToJsonEntities(Set<Entity> entities) {
        Set<JsonEntity> ret = new HashSet<JsonEntity>();
        for (Entity entity : entities) {
            ret.add(new JsonEntity(entity));
        }
        return ret;
    }
}
