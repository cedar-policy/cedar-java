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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Map;
import com.cedarpolicy.value.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Context {

    private Map<String, Value> context;

    /**
     * Constructs a new empty Context with no key-value pairs. Initializes the internal context map as an empty
     * immutable map.
     */
    public Context() {
        context = Collections.emptyMap();
    }

    public boolean isEmpty() {
        return context.isEmpty();
    }

    /**
     * Constructs a new Context from an Iterable of key-value pairs. Creates a new HashMap and populates it with the
     * provided entries. Equivalent to from_pairs in Cedar Rust.
     *
     * @param contextList An Iterable containing key-value pairs to initialize this context with
     * @throws IllegalStateException    if a duplicate key is found within the iterable
     * @throws IllegalArgumentException if the contextList parameter is null
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Context(Iterable<Map.Entry<String, Value>> contextList) {
        context = new HashMap<>();
        mergeContextFromIterable(contextList);
    }

    /**
     * Constructs a new Context with the provided map of key-value pairs. Creates a defensive copy of the input map to
     * maintain immutability.
     *
     * @param contextMap The map of key-value pairs to initialize this context with
     * @throws IllegalArgumentException if the contextMap parameter is null
     */
    public Context(Map<String, Value> contextMap) {
        context = new HashMap<>();
        context.putAll(contextMap);
    }

    /**
     * Returns a defensive copy of the internal context map.
     *
     * @return A new HashMap containing all key-value pairs from the internal context
     */
    public Map<String, Value> getContext() {
        return new HashMap<>(context);
    }

    /**
     * Merges another Context object into the current context.
     *
     * @param contextToMerge The Context object to merge into this context
     * @throws IllegalStateException    if a duplicate key is found while merging the context
     * @throws IllegalArgumentException if the contextToMerge parameter is null
     */
    public void merge(Context contextToMerge) throws IllegalStateException, IllegalArgumentException {
        mergeContextFromIterable(contextToMerge.getContext().entrySet());
    }

    /**
     * Merges the provided key-value pairs into the current context.
     *
     * @param contextMaps An Iterable containing key-value pairs to merge into this context
     * @throws IllegalStateException    if a duplicate key is found in the existing context or duplicate key found
     *                                  within the iterable
     * @throws IllegalArgumentException if the contextMaps parameter is null
     */
    public void merge(Iterable<Map.Entry<String, Value>> contextMaps)
            throws IllegalStateException, IllegalArgumentException {
        mergeContextFromIterable(contextMaps);
    }

    /**
     * Retrieves the Value associated with the specified key from the context.
     *
     * @param key The key whose associated Value is to be returned
     * @return The Value associated with the specified key, or null if the key is not found replicating Cedar Rust
     *         behavior
     * @throws IllegalArgumentException if the key parameter is null
     */
    public Value get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        return context.getOrDefault(key, null);
    }

    /**
     * Processes an Iterable of Map entries and adds them to the context.
     *
     * @param contextIterator The Iterable containing key-value pairs to add to the context
     * @throws IllegalStateException    if a duplicate key is found in the existing context or duplicate key found
     *                                  within the iterable
     * @throws IllegalArgumentException if the contextIterator is null
     */
    private void mergeContextFromIterable(Iterable<Map.Entry<String, Value>> contextIterator)
            throws IllegalStateException, IllegalArgumentException {
        if (contextIterator == null) {
            throw new IllegalArgumentException("Context iterator cannot be null");
        }

        Map<String, Value> newEntries = StreamSupport.stream(contextIterator.spliterator(), false).peek(entry -> {
            if (context.containsKey(entry.getKey())) {
                throw new IllegalStateException(
                        String.format("Duplicate key '%s' in existing context", entry.getKey()));
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        context.putAll(newEntries);
    }

    /** Readable string representation. */
    @Override
    public String toString() {
        return context.toString();
    }
}
