/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cedarpolicy.model.ValidationQuery;
import com.cedarpolicy.model.ValidationResult;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.schema.Schema;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the validator. */
public class ValidationTests {
    private Schema schema;
    private HashMap<String, String> policies;

    private static AuthorizationEngine engine;

    /** Test. */
    @Test
    public void givenEmptySchemaAndNoPolicyReturnsValid() {
        givenSchema(EMPTY_SCHEMA);
        ValidationResult result = whenValidated();
        thenIsValid(result);
    }

    /** Test. */
    @Test
    public void givenExampleSchemaAndCorrectPolicyReturnsValid() {
        givenSchema(PHOTOFLASH_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal == User::\"alice\","
                        + "    action == Action::\"viewPhoto\","
                        + "    resource == Photo::\"VacationPhoto94.jpg\""
                        + ");");
        ValidationResult result = whenValidated();
        thenIsValid(result);
    }

    /** Test. */
    @Test
    public void givenExampleSchemaAndIncorrectPolicyReturnsValid() {
        givenSchema(PHOTOFLASH_SCHEMA);
        givenPolicy(
                "policy0",
                "permit(\n"
                        + "    principal == User::\"alice\","
                        + "    action == Action::\"viewPhoto\","
                        + "    resource == User::\"bob\""
                        + ");");
        ValidationResult result = whenValidated();
        thenIsNotValid(result);
    }

    /** Test. */
    @Test
    public void givenInvalidPolicyThrowsBadRequestError() {
        givenSchema(EMPTY_SCHEMA);
        givenPolicy("policy0", "permit { }");
        AuthException result = whenValidatingThrows();
        thenTheErrorIsABadRequest(result);
    }

    private void givenSchema(Schema schema) {
        this.schema = schema;
    }

    private void givenPolicy(String id, String policy) {
        this.policies.put(id, policy);
    }

    private ValidationResult whenValidated() {
        ValidationQuery query = new ValidationQuery(schema, policies);
        return assertDoesNotThrow(() -> engine.validate(query));
    }

    private void thenIsValid(ValidationResult result) {
        assertTrue(
                result.getNotes().isEmpty(),
                () -> {
                    String notes =
                            result.getNotes().stream()
                                    .map(
                                            note ->
                                                    String.format(
                                                            "in policy %s: %s",
                                                            note.getPolicyId(), note.getNote()))
                                    .collect(Collectors.joining("\n"));
                    return "Expected valid result but got an invalid one with notes:\n" + notes;
                });
    }

    private void thenIsNotValid(ValidationResult result) {
        assertFalse(result.getNotes().isEmpty());
    }

    private AuthException whenValidatingThrows() {
        ValidationQuery query = new ValidationQuery(schema, policies);
        try {
            engine.validate(query);
        } catch (AuthException e) {
            return e;
        }
        return fail("The validation succeeded, but expected it to throw.");
    }

    private void thenTheErrorIsABadRequest(AuthException e) {
        assertTrue(e instanceof BadRequestException);
    }

    @BeforeAll
    private static void setUp() {
        engine = new BasicAuthorizationEngine();
    }

    @BeforeEach
    private void reset() {
        this.schema = null;
        this.policies = new HashMap<>();
    }

    private static final Schema EMPTY_SCHEMA = loadSchemaResource("/empty_schema.json");
    private static final Schema PHOTOFLASH_SCHEMA = loadSchemaResource("/photoflash_schema.json");

    private static <T> List<T> listOf(T... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
