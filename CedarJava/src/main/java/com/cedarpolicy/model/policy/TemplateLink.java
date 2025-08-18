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

package com.cedarpolicy.model.policy;

import com.cedarpolicy.value.EntityUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/** Template-linked policy. */
public class TemplateLink {

    private final String templateId;

    private final String resultPolicyId;

    private final List<LinkValue> linkValues;

    /**
     * Template-linked policy.
     *
     * @param templateId     the template ID.
     * @param resultPolicyId the id of the resulting policy.
     * @param linkValues     the link values.
     */
    public TemplateLink(String templateId, String resultPolicyId, List<LinkValue> linkValues) {
        this.templateId = templateId;
        this.resultPolicyId = resultPolicyId;
        this.linkValues = List.copyOf(linkValues);
    }

    /** Get the template ID. */
    public String getTemplateId() {
        return templateId;
    }

    /** Get the resulting policy id after slots in the template are filled. */
    public String getResultPolicyId() {
        return resultPolicyId;
    }

    /**
     * Get the link values.
     *
     * @return A map from slot id to `EntityUID` object
     */
    public Map<String, EntityUID> getLinkValues() {
        return linkValues.stream().collect(Collectors.toMap(LinkValue::getSlot, LinkValue::getValue));
    }
}
