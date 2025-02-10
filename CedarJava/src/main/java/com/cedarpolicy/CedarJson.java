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

import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.policy.TemplateLink;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.serializer.EntitySerializer;
import com.cedarpolicy.serializer.PolicySetSerializer;
import com.cedarpolicy.serializer.TemplateLinkSerializer;
import com.cedarpolicy.serializer.SchemaSerializer;
import com.cedarpolicy.serializer.ValueDeserializer;
import com.cedarpolicy.serializer.ValueSerializer;
import com.cedarpolicy.serializer.EntityDeserializer;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class CedarJson {
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private CedarJson() {
        throw new IllegalStateException("Utility class");
    }

    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER.copy();
    }

    public static ObjectWriter objectWriter() {
        return OBJECT_MAPPER.writer();
    }

    public static ObjectReader objectReader() {
        return OBJECT_MAPPER.reader();
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();

        final SimpleModule module = new SimpleModule();
        module.addSerializer(Entity.class, new EntitySerializer());
        module.addSerializer(Schema.class, new SchemaSerializer());
        module.addSerializer(TemplateLink.class, new TemplateLinkSerializer());
        module.addSerializer(PolicySet.class, new PolicySetSerializer());
        module.addSerializer(Value.class, new ValueSerializer());
        module.addDeserializer(Value.class, new ValueDeserializer());
        module.addDeserializer(Entity.class, new EntityDeserializer());
        mapper.registerModule(module);
        mapper.registerModule(new Jdk8Module());

        return mapper;
    }
}
