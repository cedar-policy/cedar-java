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

package com.cedarpolicy.model.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import com.google.common.collect.ImmutableList;

/** Template-linked policy. */
public class TemplateLink {

    @JsonProperty("templateId")
    private final String templateId;

    @JsonProperty("resultPolicyId")
    private final String resultPolicyId;

    @JsonProperty("instantiations")
    private final List<LinkValue> linkValues;

    /**
     * Template-linked policy.
     *
     * @param templateId the template ID.
     * @param resultPolicyId the id of the resulting policy.
     * @param linkValues the link values.
     */
    @JsonCreator
    public TemplateLink(
            @JsonProperty("templateId") String templateId,
            @JsonProperty("resultPolicyId") String resultPolicyId,
            @JsonProperty("instantiations") List<LinkValue> linkValues) {
        this.templateId = templateId;
        this.resultPolicyId = resultPolicyId;
        this.linkValues = ImmutableList.copyOf(linkValues);
    }

    /** Get the template ID. */
    public String getTemplateId() {
        return templateId;
    }

    /** Get the resulting policy id after slots in the template are filled. */
    public String getResultPolicyId() {
        return resultPolicyId;
    }

    /** Get the link values, which map slots to EUIDs. */
    public List<LinkValue> getLinkValues() {
        return linkValues;
    }
}
