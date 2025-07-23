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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.cedarpolicy.value.EntityIdentifier;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

public class EntityUIDTests {

    @Test
    public void simple() {
        var src = "Foo::\"alice\"";
        var euid = EntityUID.parse(src).get();
        assertEquals(euid.getId(), new EntityIdentifier("alice"));
        assertEquals(euid.getType(), EntityTypeName.parse("Foo").get());
    }

    @Test
    public void simpleNested() {
        var src = "Foo::Bar::\"alice\"";
        var euid = EntityUID.parse(src).get();
        assertEquals(euid.getId(), new EntityIdentifier("alice"));
        assertEquals(euid.getType(), EntityTypeName.parse("Foo::Bar").get());
    }

    @Test
    void simpleBadNameSpace() {
        var src = "Foo:Bar::\"alice\"";
        assertFalse(EntityUID.parse(src).isPresent());
    }

    @Test
    void simpleBadId() {
        var src = "Foo::bar::\"alice";
        assertFalse(EntityUID.parse(src).isPresent());
    }

    @Test
    void noId() {
        var src = "Foo::Bar::Baz";
        assertFalse(EntityUID.parse(src).isPresent());
    }

    @Test
    void nullSafety() {
        assertThrows(NullPointerException.class, () -> EntityUID.parse(null), "should throw NPE");
        EntityIdentifier nullID = null;
        assertThrows(NullPointerException.class, () -> new EntityUID(null, nullID).toString(), "should throw NPE");
        var id = new EntityIdentifier("alice");
        assertThrows(NullPointerException.class, () -> new EntityUID(null, id).toString(), "should throw NPE");
        var type = EntityTypeName.parse("Foo").get();
        assertThrows(NullPointerException.class, () -> new EntityUID(type, nullID).toString(), "should throw NPE");
    }

    @Test
    void emptyParsing() {
        assertFalse(EntityUID.parse("").isPresent());
        assertFalse(EntityUID.parse("\"test\"").isPresent());
        var x = EntityUID.parse("Foo::\"\"").get();
        assertEquals(x.getType(), EntityTypeName.parse("Foo").get());
        assertEquals(x.getId(), new EntityIdentifier(""));
    }

    @Test
    void emptyConstructing() {
        var x = new EntityUID(EntityTypeName.parse("Foo").get(), "");
        var y = EntityUID.parse(x.toString()).get();
        assertEquals(x, y);
    }



    @Property
    void roundTrip(@ForAll @From("euids") EntityUID euid) {
        var s = euid.toString();
        var euid2 = EntityUID.parse(s).get();
        assertEquals(euid, euid2);
    }

    @Property
    void roundTripStrs(@ForAll @From("euidStrings") EntityUID euid) {
        var s = euid.toString();
        var euid2 = EntityUID.parse(s).get();
        assertEquals(euid, euid2);
    }


    @Provide
    public Arbitrary<EntityUID> euids() {
        return Combinators.combine(EntityTypeNameTests.multiLevelName(), ids())
            .as((type, id) -> new EntityUID(type, id));
    }

    @Provide
    public Arbitrary<EntityUID> euidStrings() {
        return Combinators.combine(EntityTypeNameTests.multiLevelName(), idStrings())
            .as((type, id) -> new EntityUID(type, id));
    }

    public Arbitrary<EntityIdentifier> ids() {
        return Arbitraries.strings().alpha().numeric().map(s -> new EntityIdentifier(s));
    }

    public Arbitrary<String> idStrings() {
        return Arbitraries.strings().alpha().numeric();
    }


}
