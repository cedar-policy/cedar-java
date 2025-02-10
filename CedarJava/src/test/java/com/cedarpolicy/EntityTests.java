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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cedarpolicy.value.*;
import com.cedarpolicy.model.entity.Entity;

public class EntityTests {

    @Test
    public void getAttrTests() {
        PrimString stringAttr = new PrimString("stringAttrValue");
        HashMap<String, Value> attrs = new HashMap<>();
        attrs.put("stringAttr", stringAttr);
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Entity principal = new Entity(principalType.of("Alice"), attrs, new HashSet<>());

        // Test valid attribute key and value
        assertEquals(principal.getAttr("stringAttr"), stringAttr);

        // Test invalid attribute key
        assertThrows(IllegalArgumentException.class, () -> {
            principal.getAttr(null);
        });

        // Test key not found
        assertEquals(principal.getAttr("decimalAttr"), null);
    }

    @Test
    public void newWithEntityUIDTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Entity principal = new Entity(principalType.of("Alice"));

        // Test the Entity's uid
        assertEquals(principal.getEUID(), principalType.of("Alice"));

        // Test that a key is not found
        assertEquals(principal.getAttr("stringAttr"), null);

        // Test the Entity's parents
        assertEquals(principal.getParents().size(), 0);
    }

    @Test
    public void newWithoutAttributesTests() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        HashSet<EntityUID> parents = new HashSet<EntityUID>();
        parents.add(principalType.of("Bob"));

        Entity principal = new Entity(principalType.of("Alice"), parents);

        // Test the Entity's uid
        assertEquals(principal.getEUID(), principalType.of("Alice"));

        // Test that a key is not found
        assertEquals(principal.getAttr("stringAttr"), null);

        // Test the Entity's parents
        assertEquals(principal.getParents(), parents);
    }
}
