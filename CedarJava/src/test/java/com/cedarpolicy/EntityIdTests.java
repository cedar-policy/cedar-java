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

import com.cedarpolicy.value.EntityIdentifier;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

public class EntityIdTests {

    @Property
    void anyString(@ForAll String s) {
        var id = new EntityIdentifier(s);
        var asStr = id.getRepr();
        assertTrue(asStr.length() >= s.length());
    }

    @Test
    void equalsSameId() {
        EntityIdentifier a = new EntityIdentifier("alice");
        EntityIdentifier b = new EntityIdentifier("alice");
        assertEquals(a, b);
    }

    @Test
    void equalsSameInstance() {
        EntityIdentifier a = new EntityIdentifier("alice");
        assertEquals(a, a);
    }

    @Test
    void notEqualsDifferentId() {
        EntityIdentifier a = new EntityIdentifier("alice");
        EntityIdentifier b = new EntityIdentifier("bob");
        assertNotEquals(a, b);
    }

    @Test
    void notEqualsNull() {
        EntityIdentifier a = new EntityIdentifier("alice");
        assertNotEquals(a, null);
    }

    @Test
    void notEqualsDifferentType() {
        EntityIdentifier a = new EntityIdentifier("alice");
        assertNotEquals(a, "alice");
    }

    @Test
    void equalsEmptyStrings() {
        EntityIdentifier a = new EntityIdentifier("");
        EntityIdentifier b = new EntityIdentifier("");
        assertEquals(a, b);
    }

    @Test
    void equalsIsSymmetric() {
        EntityIdentifier a = new EntityIdentifier("test");
        EntityIdentifier b = new EntityIdentifier("test");
        assertEquals(a, b);
        assertEquals(b, a);
    }
}
