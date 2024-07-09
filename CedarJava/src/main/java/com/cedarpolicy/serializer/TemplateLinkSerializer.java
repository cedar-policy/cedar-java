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

import com.cedarpolicy.model.policy.TemplateLink;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/** Serialize a template-linked policy. */
public class TemplateLinkSerializer extends JsonSerializer<TemplateLink> {

    /** Serialize a template-linked policy. */
    @Override
    public void serialize(
            TemplateLink link, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("templateId", link.getTemplateId());
        jsonGenerator.writeObjectField("newId", link.getResultPolicyId());
        jsonGenerator.writeObjectField("values", link.getLinkValues().entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().asJson())));
        jsonGenerator.writeEndObject();
    }
}
