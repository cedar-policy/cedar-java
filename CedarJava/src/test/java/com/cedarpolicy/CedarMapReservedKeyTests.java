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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cedarpolicy.value.CedarMap;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;

public class CedarMapReservedKeyTests {

    @Test
    public void testPutRejectsEntityKey() {
        CedarMap map = new CedarMap();
        assertThrows(IllegalArgumentException.class, () -> {
            map.put("__entity", new PrimString("spoofed"));
        });
    }

    @Test
    public void testPutRejectsExtnKey() {
        CedarMap map = new CedarMap();
        assertThrows(IllegalArgumentException.class, () -> {
            map.put("__extn", new PrimString("spoofed"));
        });
    }

    @Test
    public void testConstructorRejectsEntityKey() {
        Map<String, Value> source = new HashMap<>();
        source.put("__entity", new PrimString("spoofed"));
        assertThrows(IllegalArgumentException.class, () -> {
            new CedarMap(source);
        });
    }

    @Test
    public void testConstructorRejectsExtnKey() {
        Map<String, Value> source = new HashMap<>();
        source.put("__extn", new PrimString("spoofed"));
        assertThrows(IllegalArgumentException.class, () -> {
            new CedarMap(source);
        });
    }

    @Test
    public void testPutAllRejectsEntityKey() {
        CedarMap map = new CedarMap();
        Map<String, Value> source = new HashMap<>();
        source.put("safe", new PrimString("ok"));
        source.put("__entity", new PrimString("spoofed"));
        assertThrows(IllegalArgumentException.class, () -> {
            map.putAll(source);
        });
    }

    @Test
    public void testPutAllRejectsExtnKey() {
        CedarMap map = new CedarMap();
        Map<String, Value> source = new HashMap<>();
        source.put("__extn", new PrimString("spoofed"));
        assertThrows(IllegalArgumentException.class, () -> {
            map.putAll(source);
        });
    }

    @Test
    public void testNormalKeysStillWork() {
        CedarMap map = new CedarMap();
        assertDoesNotThrow(() -> {
            map.put("entity", new PrimString("ok"));
            map.put("__other", new PrimString("ok"));
            map.put("_entity", new PrimString("ok"));
        });
    }
}
