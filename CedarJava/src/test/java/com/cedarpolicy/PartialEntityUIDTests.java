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

import com.cedarpolicy.value.EntityIdentifier;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PartialEntityUID;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static com.cedarpolicy.TestUtil.assertJSONEqual;
import static com.cedarpolicy.TestUtil.buildUidObject;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests for {@link PartialEntityUID}, an entity UID whose id may be unknown. */
public class PartialEntityUIDTests {

    @Test
    public void testSerialization() {
        var unknownUser = new PartialEntityUID(EntityTypeName.parse("User").get());
        assertJSONEqual(buildUidObject("User"), unknownUser);

        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        assertJSONEqual(buildUidObject("User", "alice"), new PartialEntityUID(alice));

        var quoted = EntityUID.parse("User::\"ali\\\"ce\"").get();
        assertJSONEqual(buildUidObject("User", "ali\"ce"), new PartialEntityUID(quoted));
    }

    @Test
    public void testConstructorsAreInterchangeable() {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        assertEquals(new PartialEntityUID(alice),
                new PartialEntityUID(EntityTypeName.parse("User").get(), new EntityIdentifier("alice")));
        assertEquals(new PartialEntityUID(EntityTypeName.parse("User").get()),
                new PartialEntityUID(EntityTypeName.parse("User").get(), Optional.empty()));
    }
}
