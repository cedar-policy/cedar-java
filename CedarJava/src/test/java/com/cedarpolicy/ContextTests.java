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

package com.cedarpolicy;

import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.cedarpolicy.model.Context;

import org.junit.jupiter.api.Test;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContextTests {

    private Map<String, Value> getValidMap() {
        Map<String, Value> expectedContextMap = new HashMap<>();
        expectedContextMap.put("key1", new PrimString("value1"));
        expectedContextMap.put("key2", new PrimLong(999));
        expectedContextMap.put("key3", new PrimBool(true));

        return expectedContextMap;
    }

    private Map<String, Value> getValidMergedMap() {
        Map<String, Value> expectedContextMap = new HashMap<>();
        expectedContextMap.put("key1", new PrimString("value1"));
        expectedContextMap.put("key2", new PrimLong(999));
        expectedContextMap.put("key3", new PrimBool(true));
        expectedContextMap.put("key4", new PrimString("value1"));
        expectedContextMap.put("key5", new PrimLong(999));

        return expectedContextMap;
    }

    private Iterable<Map.Entry<String, Value>> getValidIterable() {
        Set<Map.Entry<String, Value>> iterableSet = new HashSet<>();
        iterableSet.add(new AbstractMap.SimpleEntry<>("key1", new PrimString("value1")));
        iterableSet.add(new AbstractMap.SimpleEntry<>("key2", new PrimLong(999)));
        iterableSet.add(new AbstractMap.SimpleEntry<>("key3", new PrimBool(true)));

        return iterableSet;
    }

    private Iterable<Map.Entry<String, Value>> getInvalidIterable() {
        Set<Map.Entry<String, Value>> iterableSet = new HashSet<>();
        iterableSet.add(new AbstractMap.SimpleEntry<>("key1", new PrimString("value1")));
        iterableSet.add(new AbstractMap.SimpleEntry<>("key1", new PrimLong(999)));
        iterableSet.add(new AbstractMap.SimpleEntry<>("key3", new PrimBool(true)));

        return iterableSet;
    }


    @Test
    public void givenValidIterableConstructorConstructs() throws IllegalStateException {
        Context validContext = new Context(getValidIterable());
        assertEquals(getValidMap(), validContext.getContext());
    }

    @Test
    public void givenDuplicateKeyIterableConstructorThrows() throws IllegalStateException {
        assertThrows(IllegalStateException.class, () -> {
            Context validContext = new Context(getInvalidIterable());
        });
    }

    @Test
    public void givenValidMapConstructorConstructs() throws IllegalStateException {
        Context validContext = new Context(getValidMap());

        assertEquals(getValidMap(), validContext.getContext());
    }

    @Test
    public void givenValidIterableMergeMerges() throws IllegalStateException {
        Context validContext = new Context(getValidMap());
        Map<String, Value> contextToMergeMap = new HashMap<>();
        contextToMergeMap.put("key4", new PrimString("value1"));
        contextToMergeMap.put("key5", new PrimLong(999));
        validContext.merge(contextToMergeMap.entrySet());

        assertEquals(getValidMergedMap(), validContext.getContext());
    }

    @Test
    public void givenExistingKeyIterableMergeThrows() throws IllegalStateException {
        Context context = new Context(getValidMap());
        Map<String, Value> contextToMergeMap = new HashMap<>();
        contextToMergeMap.put("key3", new PrimString("value1"));
        contextToMergeMap.put("key5", new PrimLong(999));

        assertThrows(IllegalStateException.class, () -> {
            context.merge(contextToMergeMap.entrySet());
        });
    }

    @Test
    public void givenValidContextMergeMerges() throws IllegalStateException {
        Context validContext = new Context(getValidMap());
        Map<String, Value> contextToMergeMap = new HashMap<>();
        contextToMergeMap.put("key4", new PrimString("value1"));
        contextToMergeMap.put("key5", new PrimLong(999));
        Context contextToMerge = new Context(contextToMergeMap);
        validContext.merge(contextToMerge);

        assertEquals(getValidMergedMap(), validContext.getContext());
    }

    @Test
    public void givenExistingKeyContextMergeThrows() throws IllegalStateException {
        Context validContext = new Context(getValidMap());
        Map<String, Value> contextToMergeMap = new HashMap<>();
        contextToMergeMap.put("key3", new PrimString("value1"));
        contextToMergeMap.put("key5", new PrimLong(999));
        Context contextToMerge = new Context(contextToMergeMap);

        assertThrows(IllegalStateException.class, () -> {
            validContext.merge(contextToMerge);
        });
    }

    @Test
    public void givenValidKeyGetReturnsValue() {
        Context validContext = new Context(getValidMap());

        assertEquals(getValidMap().get("key1"), validContext.get("key1"));
    }

    @Test
    public void givenInvalidKeyGetReturnsNull() {
        Context validContext = new Context(getValidMap());

        assertEquals(null, validContext.get("invalidKey"));
    }
}
