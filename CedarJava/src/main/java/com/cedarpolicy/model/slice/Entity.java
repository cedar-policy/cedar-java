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

import com.cedarpolicy.serializer.JsonEuid;
import com.cedarpolicy.value.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An entity is the kind of object about which authorization decisions are made; principals,
 * actions, and resources are all a kind of entity. Each entity is defined by its entity type, a
 * unique identifier (uid), zero or more attributes mapped to values, and zero or more parent
 * entities.
 */
public class Entity {
    private final JsonEuid euid;

    /** Key/Value attribute map. */
    public final Map<String, Value> attrs;

    /** Set of entity uids that are parents to this entity. */
    public final Set<JsonEuid> parentsEuids;

    /**
     * Create an entity from JsonEuid and unwrapped JSON values.
     *
     * @param uid euid of the Entity.
     * @param attributes Key/Value map of attributes.
     * @param parentsEuid Set of parent entities' euids.
     */
    public Entity(JsonEuid uid, Map<String, Value> attributes, Set<JsonEuid> parentsEuids) {
        this.attrs = new HashMap<>(attributes);
        this.euid = uid;
        this.parentsEuids = parentsEuids;
    }

    @Override
    public String toString() {
        String parentStr = "";
        if (!parentsEuids.isEmpty()) {
            List<String> parentStrs = new ArrayList<String>(parentsEuids.stream().map(euid -> euid.toString()).collect(Collectors.toList()));
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
     * Get entity uid in JsonEuid format
     * @return Entity uid in JsonEuid format
     */
    public JsonEuid getEuid() {
        return euid;
    }

    /**
     * Get entity uid in JsonEuid format
     * @return Entity uid in JsonEuid format
     */
    public Set<JsonEuid> getParents() {
        return parentsEuids;
    }
}
