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

package com.cedarpolicy.pbt;

import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityIdentifier;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.jqwik.api.Arbitraries;

/** Generate random actions for testing. */
public final class EntityGen {

    private List<EntityIdentifier> ids;
    private EntityTypeName type;

    public EntityGen(EntityTypeName type) {
        this.type = type;
        ids = new ArrayList<>();
    }

    private EntityUID arbitraryEntityId() {
        // Generate Id's until we find one not in the generated set
        EntityIdentifier id;
        while (true) {
            id = new EntityIdentifier(Utils.strings());
            if (isUnique(id)) {
                break;
            }
        }
        this.ids.add(id);
        return new EntityUID(type, id);
    }

    private boolean isUnique(EntityIdentifier id) {
        return !ids.contains(id);
    }

    // Return an arbitrary action w/ no attributes or parents
    public Entity arbitraryEntity() {
        return new Entity(arbitraryEntityId(), new HashMap<>(), new HashSet<>());
    }

    public List<Entity> arbitraryEntities() {
        List<Entity> actions = new ArrayList<>();
        actions.add(arbitraryEntity());

        var count = Arbitraries.integers().between(10, 100).sample();

        for (int i = 0; i < count; i++) {
            actions.add(arbitraryEntity());
        }


        return actions;
    }
}
