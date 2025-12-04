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

import static com.cedarpolicy.TestUtil.loadSchemaResource;
import static com.cedarpolicy.TestUtil.loadCedarSchemaResource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;

import java.util.List;


import com.cedarpolicy.model.EntityValidationRequest;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.pbt.EntityGen;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimString;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Tests for entity validator
 */
public class EntityValidationTests {
    private static EntityGen entityGen;
    private static AuthorizationEngine engine;

    /**
     * Test that a valid entity is accepted.
     */
    @Test
    public void testValidEntity() throws AuthException {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();

        EntityValidationRequest request = new EntityValidationRequest(ROLE_SCHEMA, List.of(entity));
        engine.validateEntities(request);
}

    /**
     * Test that a valid entity with the schema in Cedar format is accepted.
     */
    @Test
    public void testValidEntityWithCedarSchema() throws AuthException {
            Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        EntityValidationRequest cedarFormatRequest = new EntityValidationRequest(ROLE_SCHEMA_CEDAR, List.of(entity));

        engine.validateEntities(cedarFormatRequest);
    }

    /**
     * Test that an entity with an attribute not specified in the schema throws an exception.
     */
    @Test
    public void testEntityWithUnknownAttribute() throws AuthException {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        entity.attrs.put("test", new PrimBool(true));

        EntityValidationRequest request = new EntityValidationRequest(ROLE_SCHEMA, List.of(entity));

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.matches(
                "attribute `test` on `Role::\".*\"` should not exist according to the schema"),
                "Expected to match regex but was: '%s'".formatted(errMsg));
}

/**
 * Test that an entity with an attribute not specified in the schema in Cedar format throws an
 * exception.
 */
@Test
public void testEntityWithUnknownAttributeWithCedarSchema() throws AuthException {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        entity.attrs.put("test", new PrimBool(true));

        EntityValidationRequest cedarFormatRequest = new EntityValidationRequest(ROLE_SCHEMA_CEDAR, List.of(entity));

        BadRequestException exception =
                        assertThrows(BadRequestException.class, () -> engine.validateEntities(cedarFormatRequest));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.matches("attribute `test` on `Role::\".*\"` should not exist according to the schema"),
                        "Expected to match regex but was: '%s'".formatted(errMsg));
    }

    /**
     * Test that entities with a cyclic parent relationship throw an exception.
     */
    @Test
    public void testEntitiesWithCyclicParentRelationship() throws AuthException {
        // Arrange
        Entity childEntity = EntityValidationTests.entityGen.arbitraryEntity();
        Entity parentEntity = EntityValidationTests.entityGen.arbitraryEntity();

        // Create a cyclic parent relationship between the entities
        childEntity.parentsEUIDs.add(parentEntity.getEUID());
        parentEntity.parentsEUIDs.add(childEntity.getEUID());

        EntityValidationRequest request =
                new EntityValidationRequest(ROLE_SCHEMA, List.of(parentEntity, childEntity));

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.matches("input graph has a cycle containing vertex `Role::\".*\"`"),
                "Expected to match regex but was: '%s'".formatted(errMsg));
}

/**
 * Test that entities with a cyclic parent relationship throw an exception with the schema in Cedar
 * format.
 */
@Test
public void testEntitiesWithCyclicParentRelationshipWithCedarSchema() throws AuthException {
        // Arrange
        Entity childEntity = EntityValidationTests.entityGen.arbitraryEntity();
        Entity parentEntity = EntityValidationTests.entityGen.arbitraryEntity();

        // Create a cyclic parent relationship between the entities
        childEntity.parentsEUIDs.add(parentEntity.getEUID());
        parentEntity.parentsEUIDs.add(childEntity.getEUID());

        EntityValidationRequest cedarFormatRequest =
                        new EntityValidationRequest(ROLE_SCHEMA_CEDAR, List.of(parentEntity, childEntity));

        BadRequestException exception =
                        assertThrows(BadRequestException.class, () -> engine.validateEntities(cedarFormatRequest));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.matches("input graph has a cycle containing vertex `Role::\".*\"`"),
                        "Expected to match regex but was: '%s'".formatted(errMsg));
    }

    /**
     * Test that an entity with a tag not specified in the schema throws an exception.
     */
    @Test
    public void testEntityWithUnknownTag() throws AuthException {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        entity.tags.put("test", new PrimString("value"));

        EntityValidationRequest request = new EntityValidationRequest(ROLE_SCHEMA, List.of(entity));

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(
                errMsg.matches("found a tag `test` on `Role::\".*\"`, "
                        + "but no tags should exist on `Role::\".*\"` according to the schema"),
                "Expected to match regex but was: '%s'".formatted(errMsg));
}

/**
 * Test that an entity with a tag not specified in the schema in Cedar format throws an exception.
 */
@Test
public void testEntityWithUnknownTagWithCedarSchema() throws AuthException {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        entity.tags.put("test", new PrimString("value"));

        EntityValidationRequest cedarFormatRequest = new EntityValidationRequest(ROLE_SCHEMA_CEDAR, List.of(entity));

        BadRequestException exception =
                        assertThrows(BadRequestException.class, () -> engine.validateEntities(cedarFormatRequest));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.matches("found a tag `test` on `Role::\".*\"`, "
                        + "but no tags should exist on `Role::\".*\"` according to the schema"),
                        "Expected to match regex but was: '%s'".formatted(errMsg));
    }

    /**
     * Test that valid enum entities are accepted.
     */
    @Test
    public void testValidEnumEntities() throws AuthException {
        // Create valid entities using enum types
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName taskType = EntityTypeName.parse("Task").get();
        EntityTypeName colorType = EntityTypeName.parse("Color").get();

        Entity user = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

        Entity task = new Entity(taskType.of("task1"), new HashMap<>() {
            {
                put("owner", user.getEUID());
                put("name", new PrimString("Complete project"));
                put("status", new EntityUID(colorType, "Red"));
            }
        }, new HashSet<>());

        EntityValidationRequest request = new EntityValidationRequest(ENUM_SCHEMA, List.of(user, task));
        engine.validateEntities(request);
    }

    /**
     * Test that enum entities with invalid enum values are rejected.
     */
    @Test
    public void testEnumEntitiesWithInvalidValues() throws AuthException {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName taskType = EntityTypeName.parse("Task").get();
        EntityTypeName colorType = EntityTypeName.parse("Color").get();

        Entity user = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

        // Create task with invalid enum value "Purple" (not in Color enum)
        Entity task = new Entity(taskType.of("task1"), new HashMap<>() {
            {
                put("owner", user.getEUID());
                put("name", new PrimString("Complete project"));
                put("status", new EntityUID(colorType, "Purple")); // Invalid enum value
            }
        }, new HashSet<>());

        EntityValidationRequest request = new EntityValidationRequest(ENUM_SCHEMA, List.of(user, task));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.contains("Purple") || errMsg.contains("Color"),
                "Expected error about invalid enum value but was: '%s'".formatted(errMsg));
    }

    /**
     * Test that enum entities cannot have attributes.
     */
    @Test
    public void testEnumEntitiesCannotHaveAttributes() throws AuthException {
        EntityTypeName colorType = EntityTypeName.parse("Color").get();

        // Try to create enum entity with attributes (should fail)
        Entity enumEntity = new Entity(colorType.of("Red"), new HashMap<>() {
            {
                put("shade", new PrimString("Dark")); // Enum entities shouldn't have attributes
            }
        }, new HashSet<>());

        EntityValidationRequest request = new EntityValidationRequest(ENUM_SCHEMA, List.of(enumEntity));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.contains("attribute") && (errMsg.contains("Color") || errMsg.contains("Red")),
                "Expected error about enum entity having attributes but was: '%s'".formatted(errMsg));
    }

    /**
     * Test that enum entities cannot have parents.
     */
    @Test
    public void testEnumEntitiesCannotHaveParents() throws AuthException {
        EntityTypeName colorType = EntityTypeName.parse("Color").get();
        EntityTypeName userType = EntityTypeName.parse("User").get();

        Entity user = new Entity(userType.of("alice"), new HashMap<>() {
            {
                put("name", new PrimString("Alice"));
            }
        }, new HashSet<>());

        // Try to create enum entity with parent (should fail)
        Entity enumEntity = new Entity(colorType.of("Red"), new HashMap<>(), new HashSet<>() {
            {
                add(user.getEUID()); // Enum entities shouldn't have parents
            }
        });

        EntityValidationRequest request = new EntityValidationRequest(ENUM_SCHEMA, List.of(user, enumEntity));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> engine.validateEntities(request));

        String errMsg = exception.getErrors().get(0);
        assertTrue(errMsg.contains("parent") || errMsg.contains("ancestor") || errMsg.contains("Color"),
                "Expected error about enum entity having parents but was: '%s'".formatted(errMsg));
    }

    /**
     * Test enum entity validation with Cedar schema format.
     */
    @Test
    public void testEnumEntitiesWithCedarSchema() throws AuthException {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName taskType = EntityTypeName.parse("Task").get();
        EntityTypeName colorType = EntityTypeName.parse("Color").get();

        Entity user = new Entity(userType.of("bob"), new HashMap<>() {
            {
                put("name", new PrimString("Bob"));
            }
        }, new HashSet<>());

        Entity task = new Entity(taskType.of("task2"), new HashMap<>() {
            {
                put("owner", user.getEUID());
                put("name", new PrimString("Review code"));
                put("status", new EntityUID(colorType, "Green"));
            }
        }, new HashSet<>());

        EntityValidationRequest cedarRequest = new EntityValidationRequest(ENUM_SCHEMA_CEDAR, List.of(user, task));
        engine.validateEntities(cedarRequest);
    }

    @BeforeAll
    public static void setUp() {

        engine = new BasicAuthorizationEngine();
        EntityTypeName user = EntityTypeName.parse("Role").get();

        EntityValidationTests.entityGen = new EntityGen(user);
    }

    private static final Schema ROLE_SCHEMA = loadSchemaResource("/role_schema.json");
    private static final Schema ROLE_SCHEMA_CEDAR = loadCedarSchemaResource("/role_schema.cedarschema");
    private static final Schema ENUM_SCHEMA = loadSchemaResource("/enum_schema.json");
    private static final Schema ENUM_SCHEMA_CEDAR = loadCedarSchemaResource("/enum_schema.cedarschema");
}
