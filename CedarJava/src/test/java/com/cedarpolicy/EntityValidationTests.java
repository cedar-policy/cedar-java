/*
 * Copyright 2022-2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;

import java.util.List;


import com.cedarpolicy.model.EntityValidationRequest;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.pbt.EntityGen;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.PrimBool;


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

        EntityValidationRequest r = new EntityValidationRequest(
                ROLE_SCHEMA, List.of(entity));

        engine.validateEntities(r);
    }

    /**
     * Test that an entity with an attribute not specified in the schema throws an exception.
     */
    @Test
    public void testEntityWithUnknownAttribute() {
        Entity entity = EntityValidationTests.entityGen.arbitraryEntity();
        entity.attrs.put("test", new PrimBool(true));

        EntityValidationRequest request = new EntityValidationRequest(
                ROLE_SCHEMA, List.of(entity));


        BadRequestException exception = assertThrows(BadRequestException.class, () -> engine.validateEntities(request));
        assertTrue(
                exception.message.orElse("")
                        .startsWith("Bad request: error during entity deserialization: attribute `test` on "),
                "Expected exception message to start with 'Bad request: error during entity deserialization: attribute `test` on'");
    }

    /**
     * Test that entities with a cyclic parent relationship throw an exception.
     */
    @Test
    public void testEntitiesWithCyclicParentRelationship() {
        // Arrange
        Entity childEntity = EntityValidationTests.entityGen.arbitraryEntity();
        Entity parentEntity = EntityValidationTests.entityGen.arbitraryEntity();

        // Create a cyclic parent relationship between the entities
        childEntity.parentsEUIDs.add(parentEntity.getEUID());
        parentEntity.parentsEUIDs.add(childEntity.getEUID());

        EntityValidationRequest request = new EntityValidationRequest(
                ROLE_SCHEMA,
                List.of(parentEntity, childEntity));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> engine.validateEntities(request));
        assertTrue(
                exception.message.orElse("")
                        .startsWith("Bad request: transitive closure computation/enforcement error:"),
                "Expected exception message to start with 'Bad request: transitive closure computation/enforcement error:'");
    }

    @BeforeAll
    public static void setUp() {

        engine = new BasicAuthorizationEngine();
        EntityTypeName user = EntityTypeName.parse("Role").get();

        EntityValidationTests.entityGen = new EntityGen(user);
    }

    private static final Schema ROLE_SCHEMA = loadSchemaResource("/role_schema.json");
}
