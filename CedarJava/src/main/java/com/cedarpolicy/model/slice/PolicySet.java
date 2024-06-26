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

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Policy Set containing policies in the Cedar language. */
public class PolicySet {

    /** Policy set. */
    public Set<Policy> policies;

    /** Template Instantiations. */
    public List<TemplateInstantiation> templateInstantiations;

    /** Templates. */
    public Set<Policy> templates;

    public PolicySet() {
        this.policies = Collections.emptySet();
        this.templates = Collections.emptySet();
        this.templateInstantiations = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies) {
        this.policies = policies;
        this.templates = Collections.emptySet();
        this.templateInstantiations = Collections.emptyList();
    }

    public PolicySet(Set<Policy> policies, Set<Policy> templates, List<TemplateInstantiation> templateInstantiations) {
        this.policies = policies;
        this.templates = templates;
        this.templateInstantiations = templateInstantiations;
    }
}
