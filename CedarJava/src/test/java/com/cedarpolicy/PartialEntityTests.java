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

import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.entity.PartialEntity;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static com.cedarpolicy.TestUtil.assertJSONEqual;
import static com.cedarpolicy.TestUtil.assertMessageContains;
import static com.cedarpolicy.TestUtil.buildUidObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link PartialEntity}, an entity whose attributes, parents, and tags may be unknown. */
public class PartialEntityTests {
    /**
     * Schema for the type-aware partial evaluation tests. {@code User} has a typed attribute, a declared tag type, and a
     * declared parent type, so an entity can be made to fail against it for one specific reason at a time. Both the
     * attribute and the context attribute are optional so that a field stated to be empty still checks out.
     */
    private static final Schema TPE_SCHEMA = TestUtil.loadSchemaResource("/tpe_schema.json");

    /** The same schema in Cedar format, which reaches the other branch of the FFI's schema parsing. */
    private static final Schema TPE_SCHEMA_CEDAR = TestUtil.loadCedarSchemaResource("/tpe_schema.cedarschema");

    @Test
    public void testSerialization() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");

        ObjectNode attrs = JsonNodeFactory.instance.objectNode();
        attrs.put("department", "eng");
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        n.set("attrs", attrs);
        assertJSONEqual(n, new PartialEntity(alice, Optional.of(Map.of("department", new PrimString("eng"))),
                Optional.empty(), Optional.empty(), TPE_SCHEMA));

        ArrayNode parents = JsonNodeFactory.instance.arrayNode();
        parents.add(buildUidObject("Group", "admins"));
        n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        n.set("parents", parents);
        assertJSONEqual(n, new PartialEntity(alice, Optional.empty(), Optional.of(Set.of(admins)),
                Optional.empty(), TPE_SCHEMA));

        ObjectNode tags = JsonNodeFactory.instance.objectNode();
        tags.put("stage", "beta");
        n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        n.set("tags", tags);
        assertJSONEqual(n, new PartialEntity(alice, Optional.empty(), Optional.empty(),
                Optional.of(Map.of("stage", new PrimString("beta"))), TPE_SCHEMA));

        n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        assertJSONEqual(n, new PartialEntity(alice, Optional.empty(), Optional.empty(), Optional.empty(),
                TPE_SCHEMA));

        n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        n.set("attrs", JsonNodeFactory.instance.objectNode());
        n.set("parents", JsonNodeFactory.instance.arrayNode());
        n.set("tags", JsonNodeFactory.instance.objectNode());
        assertJSONEqual(n, new PartialEntity(alice, Optional.of(Map.of()), Optional.of(Set.of()),
                Optional.of(Map.of()), TPE_SCHEMA));
    }

    @Test
    public void testOfEntityMatchesEntitySerializer() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");
        var entity = new Entity(alice, Map.of("department", new PrimString("eng")), Set.of(admins),
                Map.of("stage", new PrimString("beta")));

        ObjectNode attrs = JsonNodeFactory.instance.objectNode();
        attrs.put("department", "eng");
        ArrayNode parents = JsonNodeFactory.instance.arrayNode();
        parents.add(buildUidObject("Group", "admins"));
        ObjectNode tags = JsonNodeFactory.instance.objectNode();
        tags.put("stage", "beta");
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("uid", buildUidObject("User", "alice"));
        n.set("attrs", attrs);
        n.set("parents", parents);
        n.set("tags", tags);

        assertJSONEqual(n, entity);
        assertJSONEqual(n, new PartialEntity(entity, TPE_SCHEMA));
    }

    @Test
    public void testToStringLabelsUnknownFieldsAndOmitsEmptyOnes() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");

        // A fully known entity renders like its concrete counterpart, so compare against one.
        var known = new PartialEntity(alice, Optional.of(Map.of("department", new PrimString("eng"))),
                Optional.of(Set.of(admins)), Optional.of(Map.of("stage", new PrimString("beta"))), TPE_SCHEMA);
        assertEquals(new Entity(alice, Map.of("department", new PrimString("eng")), Set.of(admins),
                Map.of("stage", new PrimString("beta"))).toString(), known.toString());
        assertEquals("User::\"alice\""
                + "\n\tparents:\n\t\tGroup::\"admins\""
                + "\n\tattrs:\n\t\tdepartment: eng"
                + "\n\ttags:\n\t\tstage: beta", known.toString());

        // An unknown field is labelled, which is the one thing a concrete entity cannot express.
        var unknown = new PartialEntity(alice, Optional.empty(), Optional.empty(), Optional.empty(), TPE_SCHEMA);
        assertEquals("User::\"alice\""
                + "\n\tparents: unknown"
                + "\n\tattrs: unknown"
                + "\n\ttags: unknown", unknown.toString());

        // A field known to be empty is omitted rather than labelled, matching a concrete entity with nothing set.
        var empty = new PartialEntity(alice, Optional.of(Map.of()), Optional.of(Set.of()), Optional.of(Map.of()),
                TPE_SCHEMA);
        assertEquals("User::\"alice\"", empty.toString());
        assertEquals(new Entity(alice).toString(), empty.toString());
    }

    @Test
    public void testRejectsAttributeOfTheWrongType() {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        Map<String, Value> attrs = Map.of("department", new PrimLong(3L));
        InternalException e = assertThrows(InternalException.class,
                () -> new PartialEntity(alice, Optional.of(attrs), Optional.empty(), Optional.empty(), TPE_SCHEMA));
        assertMessageContains(e, "attribute `department`", "User::\"alice\"", "type mismatch",
                "expected to have type string", "actually has type long");
    }

    @Test
    public void testChecksAgainstCedarFormatSchema() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");

        new PartialEntity(alice, Optional.of(Map.of("department", new PrimString("eng"))),
                Optional.of(Set.of(admins)), Optional.of(Map.of("stage", new PrimString("beta"))), TPE_SCHEMA_CEDAR);

        // The same violation the JSON-format schema catches, to show the Cedar-format schema is applied and not merely
        // parsed into something permissive.
        Map<String, Value> wrongType = Map.of("department", new PrimLong(3L));
        InternalException e = assertThrows(InternalException.class, () -> new PartialEntity(alice,
                Optional.of(wrongType), Optional.empty(), Optional.empty(), TPE_SCHEMA_CEDAR));
        assertMessageContains(e, "attribute `department`", "User::\"alice\"", "type mismatch",
                "expected to have type string", "actually has type long");
    }
}
