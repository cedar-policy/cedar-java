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

import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A basic implementation of the Slice interface that holds the policies, attributes, and parents
 * maps in memory.
 */
public class BasicSlice implements Slice {
    private final Map<String, String> policies;
    private final Map<String, Map<String, Value>> attributes;
    private final Map<String, List<String>> parents;

    private final Set<Entity> entities;

    @JsonProperty("template_policies")
    private final Map<String, String> templatePolicies;

    @JsonProperty("template_instantiations")
    private final List<TemplateInstantiation> templateInstantiations;

    /**
     * Construct a Slice from Entity and Policy objects.
     *
     * @param policies Set of policies.
     * @param entities Set of entities.
     * @param templates Set of policy templates.
     * @param templateInstantiations List of TemplateInstantiations.
     */
    @SuppressFBWarnings
    public BasicSlice(
            Set<Policy> policies,
            Set<Entity> entities,
            Set<Policy> templates,
            List<TemplateInstantiation> templateInstantiations) {   
        // Copy of previous constructor. We can't call the previous constructor because fields are
        // final
        this.policies = new HashMap<>();
        for (Policy p : policies) {
            this.policies.put(p.policyId, p.policySrc);
        }
        HashMap<String, Map<String, Value>> attributes = new HashMap<>();
        HashMap<String, List<String>> parents = new HashMap<>();

        for (Entity entity : entities) {
            attributes.put(entity.getEuid().toString(), entity.attrs);
            List<String> parentList = new ArrayList<String>(entity.getParents().stream().map(euid -> euid.toString()).collect(Collectors.toList()));
            parents.put(entity.getEuid().toString(), parentList);
        }

        this.attributes = attributes;
        this.parents = parents;
        this.entities = entities;

        this.templatePolicies =
                templates.stream().collect(Collectors.toMap(p -> p.policyId, p -> p.policySrc));
        this.templateInstantiations = new ArrayList<TemplateInstantiation>(templateInstantiations);
    }


    /**
     * Construct a Slice from Entity and Policy objects.
     *
     * @param policies Set of policies.
     * @param entities Set of entities.
     */
    @SuppressFBWarnings
    public BasicSlice(Set<Policy> policies, Set<Entity> entities) {
        this(policies, entities, Collections.emptySet(), Collections.emptyList());
    }



    @Override
    @SuppressFBWarnings
    public Map<String, String> getPolicies() {
        return policies;
    }

    @Override
    @SuppressFBWarnings
    public Map<String, Map<String, Value>> getAttributes() {
        return attributes;
    }

    @Override
    @SuppressFBWarnings
    public Map<String, List<String>> getParents() {
        return parents;
    }

    @Override
    @SuppressFBWarnings
    public Set<Entity> getEntities() {
        return entities;
    }

    @Override
    @SuppressFBWarnings
    public Map<String, String> getTemplates() {
        return templatePolicies;
    }

    @Override
    @SuppressFBWarnings
    public List<TemplateInstantiation> getTemplateInstantiations() {
        return templateInstantiations;
    }

    @Override
    public String toString() {
        return "BasicSlice{"
                + "policies="
                + policies
                + ", attributes="
                + attributes
                + ", parents="
                + parents
                + '}';
    }
}
