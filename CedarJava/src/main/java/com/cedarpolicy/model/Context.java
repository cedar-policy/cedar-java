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

package com.cedarpolicy.model;

import java.util.HashMap;
import java.util.Collections;
import java.util.Collections.singletonMap;
import java.util.Map;
import java.util.List;
import com.cedarpolicy.value.Value;


public class Context {

    private Map<String, Value> context;

    /**
     * Counterpart to empty() in CedarRust
     */
    public Context() {
        this.context = Collections.emptyMap();
    }

    /**
     * Counterpart to pairs() in CedarRust
     * @param contextList
     */
    public Context(Iterable<singletonMap<String, Value>> contextList) {
        this.context = new HashMap<>();
        fromIterable();
    }

    /**
     * Create a context object using a Map
     * 
     * @param contextMap
     */
    public Context(Map<String, Value> contextMap) {
        this.context = new HashMap<>();
        this.context.putAll(contextMap);
    }

    def getContextMap() {
        return this.context.copy();
    }

    def merge(Context contextToMerge) {
        this.context.putAll(contextToMerge.getContextMap());
    }

    /**
     * Merges multiple maps of context values into this context
     * @param contextMaps Iterator of Map<String,Value> to merge
     */
    public void merge(Iterable<singletonMap<String,Value>> contextMaps) {
        fromIterable(contextMaps);
    }

    private fromIterable(Iterable<singletonMap<String,Value>> contextIterator) {
        contextIterator.forEach(map -> {
            if (!this.context.containsKey(map.keySet())) {
                this.context.putAll(map);
            } else {
                throw new IllegalArgumentException("Duplicate key found in context");
            }
        });}

    }
