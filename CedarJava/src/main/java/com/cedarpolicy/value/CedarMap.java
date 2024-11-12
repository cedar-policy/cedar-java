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

package com.cedarpolicy.value;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Represents a Cedar Map value. Maps support mapping strings to arbitrary values. */
public final class CedarMap extends Value implements Map<String, Value> {
    /** Internal map data. */
    private final java.util.Map<String, Value> map;

    /**
     * Create a Cedar map by copy.
     *
     * @param source map to copy from
     */
    public CedarMap(java.util.Map<String, Value> source) {
        this.map = new HashMap<>(source);
    }

    /** Create an empty Cedar map. */
    public CedarMap() {
        this.map = new HashMap<>();
    }

    /** Equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CedarMap map1 = (CedarMap) o;
        return map.equals(map1.map);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(map);
    }

    /** To Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "{"
                + map.entrySet().stream()
                        .map(e -> '\"' + e.getKey() + "\": " + e.getValue().toCedarExpr())
                        .collect(Collectors.joining(", "))
                + "}";
    }

    // Overrides to HashMap.

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean containsKey(Object k) {
        return map.containsKey(k);
    }

    @Override
    public boolean containsValue(Object v) {
        return map.containsValue(v);
    }

    @Override
    public Set<Map.Entry<String, Value>> entrySet() {
        return map.entrySet();
    }
    // @Override
    // public boolean equals(Object o) { return map.equals(o);}
    @Override
    public Value get(Object k) {
        return map.get(k);
    }
    // @Override
    // public int hashcode() { return map.hashcode();}
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Value put(String k, Value v) throws NullPointerException {
        if (k == null) {
            throw new NullPointerException("Attempt to put null key in CedarMap");
        }
        if (v == null) {
            throw new NullPointerException("Attempt to put null value in CedarMap");
        }

        return map.put(k, v);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Value> m) {
        map.putAll(m);
    }

    @Override
    public Value remove(Object k) {
        return map.remove(k);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Collection<Value> values() {
        return map.values();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
