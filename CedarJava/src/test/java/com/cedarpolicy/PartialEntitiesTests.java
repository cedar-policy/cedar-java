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

import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.entity.PartialEntities;
import com.cedarpolicy.model.entity.PartialEntity;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static com.cedarpolicy.TestUtil.assertMessageContains;
import static com.cedarpolicy.TestUtil.buildEuidObject;
import static com.cedarpolicy.TestUtil.buildUidObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link PartialEntities}, a collection of partially known entities. */
public class PartialEntitiesTests {
    /** See {@link PartialEntityTests} for what this schema is shaped to catch. */
    private static final Schema TPE_SCHEMA = TestUtil.loadSchemaResource("/tpe_schema.json");

    /** The same schema in Cedar format, which reaches the other branch of the FFI's schema parsing. */
    private static final Schema TPE_SCHEMA_CEDAR = TestUtil.loadCedarSchemaResource("/tpe_schema.cedarschema");

    @Test
    public void testChecksAgainstCedarFormatSchema() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var entities = Set.of(
                new PartialEntity(alice, Optional.empty(), Optional.empty(), Optional.empty(), TPE_SCHEMA_CEDAR));
        assertEquals(1, new PartialEntities(entities, TPE_SCHEMA_CEDAR).getEntities().size());

        // A collection-level rule, which only the collection validator can catch.
        var duplicated = Set.of(
                new PartialEntity(alice, Optional.empty(), Optional.empty(), Optional.empty(), TPE_SCHEMA_CEDAR),
                new PartialEntity(alice, Optional.of(Map.of()), Optional.empty(), Optional.empty(), TPE_SCHEMA_CEDAR));
        InternalException e =
                assertThrows(InternalException.class, () -> new PartialEntities(duplicated, TPE_SCHEMA_CEDAR));
        assertMessageContains(e, "duplicate entity entry", "User::\"alice\"");
    }

    @Test
    public void testRejectsParentWithUnknownParents() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");
        // `admins` is in the collection with its own parents unknown, so Cedar cannot close the hierarchy over it.
        var entities = Set.of(
                new PartialEntity(alice, Optional.empty(), Optional.of(Set.of(admins)), Optional.empty(), TPE_SCHEMA),
                new PartialEntity(admins, Optional.empty(), Optional.empty(), Optional.empty(), TPE_SCHEMA));
        InternalException e =
                assertThrows(InternalException.class, () -> new PartialEntities(entities, TPE_SCHEMA));
        assertMessageContains(e, "ancestor `Group::\"admins\"`", "of `User::\"alice\"`", "has unknown ancestors");
    }

    @Test
    public void testAllowsParentAbsentFromTheCollection() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");
        var child =
                new PartialEntity(alice, Optional.empty(), Optional.of(Set.of(admins)), Optional.empty(), TPE_SCHEMA);

        // Nothing is claimed about `admins`, so naming it as a parent is fine.
        assertEquals(1, new PartialEntities(Set.of(child), TPE_SCHEMA).getEntities().size());

        // Stating that `admins` has no parents is also fine: the hierarchy can be closed.
        var noParents =
                new PartialEntity(admins, Optional.empty(), Optional.of(Set.of()), Optional.empty(), TPE_SCHEMA);
        var withParent = new PartialEntities(Set.of(child, noParents), TPE_SCHEMA);
        assertEquals(2, withParent.getEntities().size());
    }

    @Test
    public void testRejectsDuplicateUid() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var entities = Set.of(
                new PartialEntity(alice, Optional.empty(), Optional.empty(), Optional.empty(), TPE_SCHEMA),
                new PartialEntity(alice, Optional.of(Map.of()), Optional.empty(), Optional.empty(), TPE_SCHEMA));
        InternalException e =
                assertThrows(InternalException.class, () -> new PartialEntities(entities, TPE_SCHEMA));
        assertMessageContains(e, "duplicate entity entry", "User::\"alice\"");
    }

    @Test
    public void testOfConcreteEntitiesLiftsEveryField() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");
        var entities = new Entities(Set.of(
                new Entity(alice, Map.of("department", new PrimString("eng")), Set.of(admins),
                        Map.of("stage", new PrimString("beta"))),
                new Entity(admins, Map.of(), Set.of(), Map.of())));

        var lifted = new PartialEntities(entities, TPE_SCHEMA);
        assertEquals(2, lifted.getEntities().size());

        // A concrete entity is fully known, so every field must come across as present rather than unknown.
        PartialEntity liftedAlice = lifted.getEntities().stream()
                .filter(e -> e.getEUID().equals(alice))
                .findFirst()
                .orElseThrow();
        assertEquals(Map.of("department", new PrimString("eng")), liftedAlice.getAttrs().orElseThrow());
        assertEquals(Set.of(admins), liftedAlice.getParents().orElseThrow());
        assertEquals(Map.of("stage", new PrimString("beta")), liftedAlice.getTags().orElseThrow());

        // `admins` is present with no parents, which is what lets Cedar close the hierarchy above.
        PartialEntity liftedAdmins = lifted.getEntities().stream()
                .filter(e -> e.getEUID().equals(admins))
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of(), liftedAdmins.getParents().orElseThrow());
        assertEquals(Map.of(), liftedAdmins.getAttrs().orElseThrow());
    }

    @Test
    public void testOfConcreteEntitiesChecksAgainstSchema() {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var entities = new Entities(Set.of(
                new Entity(alice, Map.of("department", new PrimLong(3L)), Set.of(), Map.of())));
        InternalException e =
                assertThrows(InternalException.class, () -> new PartialEntities(entities, TPE_SCHEMA));
        assertMessageContains(e, "attribute `department`", "User::\"alice\"", "type mismatch",
                "expected to have type string", "actually has type long");
    }

    @Test
    public void testFromJsonRoundTrip() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var admins = new EntityUID(EntityTypeName.parse("Group").get(), "admins");

        ObjectNode attrs = JsonNodeFactory.instance.objectNode();
        attrs.put("department", "eng");
        ObjectNode tags = JsonNodeFactory.instance.objectNode();
        tags.put("stage", "beta");
        ArrayNode parents = JsonNodeFactory.instance.arrayNode();
        // Cedar accepts either uid encoding, so exercise the escaped one here and the flat one for the uid itself.
        parents.add(buildEuidObject("Group", "admins"));
        ObjectNode aliceJson = JsonNodeFactory.instance.objectNode();
        aliceJson.set("uid", buildUidObject("User", "alice"));
        aliceJson.set("attrs", attrs);
        aliceJson.set("parents", parents);
        aliceJson.set("tags", tags);
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        json.add(aliceJson);

        var entities = PartialEntities.fromJson(json, TPE_SCHEMA).getEntities();
        assertEquals(1, entities.size());
        PartialEntity parsed = entities.iterator().next();
        assertEquals(alice, parsed.getEUID());
        assertEquals(Map.of("department", new PrimString("eng")), parsed.getAttrs().orElseThrow());
        assertEquals(Set.of(admins), parsed.getParents().orElseThrow());
        assertEquals(Map.of("stage", new PrimString("beta")), parsed.getTags().orElseThrow());

        // A field left out of the encoding stays unknown rather than becoming empty.
        ObjectNode existsOnlyJson = JsonNodeFactory.instance.objectNode();
        existsOnlyJson.set("uid", buildUidObject("User", "alice"));
        var existsOnly = PartialEntities
                .fromJson(JsonNodeFactory.instance.arrayNode().add(existsOnlyJson), TPE_SCHEMA)
                .getEntities()
                .iterator()
                .next();
        assertTrue(existsOnly.getAttrs().isEmpty());
        assertTrue(existsOnly.getParents().isEmpty());
        assertTrue(existsOnly.getTags().isEmpty());

        // An explicit null is what serde maps to None, so it must also stay unknown rather than becoming empty.
        ObjectNode nullFieldsJson = JsonNodeFactory.instance.objectNode();
        nullFieldsJson.set("uid", buildUidObject("User", "alice"));
        nullFieldsJson.putNull("attrs");
        nullFieldsJson.putNull("parents");
        nullFieldsJson.putNull("tags");
        var nullFields = PartialEntities
                .fromJson(JsonNodeFactory.instance.arrayNode().add(nullFieldsJson), TPE_SCHEMA)
                .getEntities()
                .iterator()
                .next();
        assertTrue(nullFields.getAttrs().isEmpty());
        assertTrue(nullFields.getParents().isEmpty());
        assertTrue(nullFields.getTags().isEmpty());
    }

    @Test
    public void testFromJsonRejectsEntityThatDoesNotConformToSchema() {
        // An attribute of the wrong type. Unlike a non-array payload, this is only detectable by Cedar, so it exercises
        // the native validation that `fromJson` runs before it parses anything.
        ObjectNode attrs = JsonNodeFactory.instance.objectNode();
        attrs.put("department", 3);
        ObjectNode wrongType = JsonNodeFactory.instance.objectNode();
        wrongType.set("uid", buildUidObject("User", "alice"));
        wrongType.set("attrs", attrs);
        InternalException e = assertThrows(InternalException.class, () -> PartialEntities
                .fromJson(JsonNodeFactory.instance.arrayNode().add(wrongType), TPE_SCHEMA));
        assertMessageContains(e, "attribute `department`", "User::\"alice\"", "type mismatch",
                "expected to have type string", "actually has type long");

        // An entity type the schema does not declare.
        ObjectNode undeclaredType = JsonNodeFactory.instance.objectNode();
        undeclaredType.set("uid", buildUidObject("Album", "trip"));
        InternalException undeclared = assertThrows(InternalException.class, () -> PartialEntities
                .fromJson(JsonNodeFactory.instance.arrayNode().add(undeclaredType), TPE_SCHEMA));
        assertMessageContains(undeclared, "entity `Album::\"trip\"`", "type `Album`",
                "not declared in the schema");
    }

    @Test
    public void testFromJsonRejectsNonArrayJson() {
        assertThrows(InternalException.class,
                () -> PartialEntities.fromJson(JsonNodeFactory.instance.objectNode(), TPE_SCHEMA));
    }
}
