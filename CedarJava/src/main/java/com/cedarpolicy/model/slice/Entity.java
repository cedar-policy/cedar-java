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

import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An entity is the kind of object about which authorization decisions are made; principals,
 * actions, and resources are all a kind of entity. Each entity is defined by its entity type, a
 * unique identifier (UID), zero or more attributes mapped to values, and zero or more parent
 * entities.
 */
public class Entity {
    private final EntityUID euid;

    /** Key/Value attribute map. */
    public final Map<String, Value> attrs;

    /** Set of entity EUIDs that are parents to this entity. */
    public final Set<EntityUID> parentsEUIDs;

    /**
     * Create an entity from JsonEUID and unwrapped JSON values.
     *
     * @param uid EUID of the Entity.
     * @param attributes Key/Value map of attributes.
     * @param parentsEUID Set of parent entities' EUIDs.
     */
    public Entity(EntityUID uid, Map<String, Value> attributes, Set<EntityUID> parentsEUIDs) {
        this.attrs = new HashMap<>(attributes);
        this.euid = uid;
        this.parentsEUIDs = parentsEUIDs;
    }

    @Override
    public String toString() {
        String parentStr = "";
        if (!parentsEUIDs.isEmpty()) {
            List<String> parentStrs = new ArrayList<String>(parentsEUIDs.stream().map(euid -> euid.toString()).collect(Collectors.toList()));
            parentStr = "\n\tparents:\n\t\t" + String.join("\n\t\t", parentStrs);
        }
        String attributeStr = "";
        if (!attrs.isEmpty()) {
            attributeStr =
                    "\n\tattrs:\n\t\t"
                            + attrs.entrySet().stream()
                                    .map(e -> e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n\t\t"));
        }
        return euid.toString() + parentStr + attributeStr;
    }


    /**
     * Get entity uid in JsonEUID format
     * @return Entity UID in JsonEUID format
     */
    public EntityUID getEUID() {
        return euid;
    }

    /**
     * Get entity uid in JsonEUID format
     * @return Entity UID in JsonEUID format
     */
    public Set<EntityUID> getParents() {
        return parentsEUIDs;
    }
}
