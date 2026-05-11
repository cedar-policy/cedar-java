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
import com.cedarpolicy.value.Unknown;
import com.cedarpolicy.value.Value;

public class CedarExprEscapingTests {

    @Test
    public void testPrimStringEscapesDoubleQuotes() {
        PrimString s = new PrimString("x\" && false && \"x");
        assertEquals("\"x\\\" && false && \\\"x\"", s.toCedarExpr());
    }

    @Test
    public void testPrimStringEscapesBackslash() {
        PrimString s = new PrimString("path\\to\\file");
        assertEquals("\"path\\\\to\\\\file\"", s.toCedarExpr());
    }

    @Test
    public void testPrimStringEscapesControlCharacters() {
        PrimString s = new PrimString("line1\nline2\ttab\r\0");
        assertEquals("\"line1\\nline2\\ttab\\r\\0\"", s.toCedarExpr());
    }

    @Test
    public void testPrimStringPlainStringUnchanged() {
        PrimString s = new PrimString("hello world");
        assertEquals("\"hello world\"", s.toCedarExpr());
    }

    @Test
    public void testPrimStringEmptyString() {
        PrimString s = new PrimString("");
        assertEquals("\"\"", s.toCedarExpr());
    }

    @Test
    public void testCedarMapEscapesKeys() {
        Map<String, Value> source = new HashMap<>();
        source.put("key\"injection", new PrimString("value"));
        CedarMap map = new CedarMap(source);
        String expr = map.toCedarExpr();
        assertTrue(expr.contains("key\\\"injection"));
        assertFalse(expr.contains("key\"injection"));
    }

    @Test
    public void testUnknownEscapesArg() {
        Unknown u = new Unknown("arg\"injection");
        assertEquals("Unknown(\"arg\\\"injection\")", u.toCedarExpr());
    }
}
