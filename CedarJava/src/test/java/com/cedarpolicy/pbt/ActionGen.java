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

import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.serializer.JsonEUID;
import com.cedarpolicy.value.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;

/** Generate random actions for testing. */
public final class ActionGen {
    /** Generate a random Action Group. */
    public static List<Entity> getEntities() {
        List<Entity> actions = new ArrayList<>();
        String actionType = "Action";
        String actionId = Utils.strings();
        String actionEUID = actionType+"::\"" + actionId + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<JsonEUID> actionParents = new HashSet<>();
        Entity e = new Entity(new JsonEUID(actionType, actionId), actionAttributes, actionParents);
        actions.add(e);
        int count = Arbitraries.integers().between(10, 100).sample();

        for (int i = 0; i < count; i++) {
            actionId = Utils.strings();
            actionEUID = "Action::\"" + actionId + "\"";
            if (!e.getEUID().toString().equals(actionEUID)) {
                e.parentsEUIDs.add(new JsonEUID(actionType, actionId));
            }
            actionAttributes = new HashMap<>();
            actionParents = new HashSet<>();
            actions.add(new Entity(new JsonEUID(actionType, actionId), actionAttributes, actionParents));
        }
        return actions;
    }

    private ActionGen() {
        throw new IllegalStateException("Utility class");
    }
}
