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

package com.cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Template instantiation. */
public class TemplateInstantiation {

    @JsonProperty("template_id")
    private final String templateId;

    @JsonProperty("result_policy_id")
    private final String resultPolicyId;

    private final List<Instantiation> instantiations;

    /**
     * Template Instantiation.
     *
     * @param templateId the template ID.
     * @param resultPolicyId the id of the resulting policy.
     * @param instantiations the instantiations.
     */
    @JsonCreator
    public TemplateInstantiation(
            @JsonProperty("template_id") String templateId,
            @JsonProperty("result_policy_id") String resultPolicyId,
            @JsonProperty("instantiations") List<Instantiation> instantiations) {
        this.templateId = templateId;
        this.resultPolicyId = resultPolicyId;
        this.instantiations = List.copyOf(instantiations);
    }

    /** Get the template ID. */
    public String getTemplateId() {
        return templateId;
    }

    /** Get the resulting policy id after slots in the template are filled. */
    public String getResultPolicyId() {
        return resultPolicyId;
    }

    /** Get the instantiations to fill the slots. */
    public List<Instantiation> getInstantiations() {
        return instantiations;
    }
}
