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

    /** The template ID. */
    @JsonProperty("template_id")
    public final String templateId;

    /** The resulting policy id after slots in the template are filled. */
    @JsonProperty("result_policy_id")
    public final String resultPolicyId;

    /** The instantiations to fill the slots. */
    public final List<Instantiation> instantiations;

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
        this.instantiations = instantiations;
    }
}
