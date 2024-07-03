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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse.Decision;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.exception.BadRequestException;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.serializer.JsonEUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Integration tests Used by Cedar / corpus tests saved from the fuzzer. */
public class SharedIntegrationTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * For relative paths, return an absolute path rooted in the shared integration test root. For
     * absolute paths, return them unchanged.
     *
     * @param path Path string representing either a relative path for a file in the shared
     *     integration tests, or an absolute path.
     * @return A Path object containing an absolute path.
     */
    private Path resolveIntegrationTestPath(String path) {
        final Path resolved = Paths.get(path);
        if (resolved.isAbsolute()) {
            return resolved;
        } else {
            final URL integrationTestsLocation = getClass().getResource("/cedar-integration-tests-main");
            return integrationTestsLocation == null ? resolved : Paths.get(integrationTestsLocation.getPath(), path);
        }
    }

    /**
     * Directly corresponds to the structure of the JSON formatted tests files. The fields are
     * populated by Jackson when the test files are deserialized.
     */
    @SuppressWarnings("visibilitymodifier")
    private static class JsonTest {
        /**
         * File name of the file containing policies. Path is relative to the integration tests
         * root.
         */
        public String policies;

        /**
         * File name of the file containing entities. Path is relative to the integration tests
         * root.
         */
        public String entities;

        /**
         * File name of the schema file. Path is relative to the integration tests root. Note: This
         * field is currently unused by these tests. The tests should be updated to take advantage
         * of it once there is a Java interface to the validator.
         */
        public String schema;

        /**
         * Whether the given policies are expected to pass the validator with this schema, or not
         */
        public boolean shouldValidate;

        /** List of requests with their expected result. */
        public List<JsonRequest> requests;
    }

    /** Directly corresponds to the structure of a request in the JSON formatted tests files. */
    @SuppressWarnings("visibilitymodifier")
    private static class JsonRequest {
        /** Textual description of the request. */
        public String description;

        /** Principal entity uid used for the request. */
        public JsonEUID principal;

        /** Action entity uid used for the request. */
        public JsonEUID action;

        /** Resource entity uid used for the request. */
        public JsonEUID resource;

        /** Context map used for the request. */
        public Map<String, Value> context;

        /** Whether to enable request validation for this request. Default true */
        public boolean validateRequest = true;

        /** The expected decision that should be returned by the authorization engine. */
        public Decision decision;

        /** The expected reason list that should be returned by the authorization engine. */
        public List<String> reason;

        /** The expected error list that should be returned by the authorization engine. */
        public List<String> errors;
    }

    /**
     * Directly corresponds to the structure of an entity in JSON entity file. Note that it is not
     * quite the same as the Entity class in the main Java API. The attrs map is from String to
     * String, rather than String to Values.
     */
    @SuppressWarnings("visibilitymodifier")
    private static class JsonEntity {
        /** Entity uid for the entity. */
        @SuppressFBWarnings(
                value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
                justification = "Initialized by Jackson.")
        public JsonEUID uid;

        /** Entity attributes, where the value string is a Cedar literal value. */
        @SuppressFBWarnings(
                value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
                justification = "Initialized by Jackson.")
        public Map<String, Value> attrs;

        /** List of direct parent entities of this entity. */
        @SuppressFBWarnings(
                value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
                justification = "Initialized by Jackson.")
        public List<JsonEUID> parents;
    }

    /**
     * An array of all the shared test json files (not counting corpus tests). The contents of the
     * files in this array will be executed as integration tests.
     */
    private static final String[] JSON_TEST_FILES = {
       "tests/decimal/1.json",
       "tests/decimal/2.json",
       "tests/example_use_cases/1a.json",
       "tests/example_use_cases/2a.json",
       "tests/example_use_cases/2b.json",
       "tests/example_use_cases/2c.json",
       "tests/example_use_cases/3a.json",
       "tests/example_use_cases/3b.json",
       "tests/example_use_cases/3c.json",
       "tests/example_use_cases/4a.json",
       "tests/example_use_cases/4d.json",
       "tests/example_use_cases/4e.json",
       "tests/example_use_cases/4f.json",
       "tests/example_use_cases/5b.json",
       "tests/ip/1.json",
       "tests/ip/2.json",
       "tests/ip/3.json",
       "tests/multi/1.json",
       "tests/multi/2.json",
       "tests/multi/3.json",
       "tests/multi/4.json",
       "tests/multi/5.json",
    };


    /**
     * This method is the main entry point for JUnit. It returns a list of containers, which contain
     * tests for junit to run. JUnit will run all the tests returned from this method.
     */
    @TestFactory
    public List<DynamicContainer> integrationTestsFromJson() throws InternalException, IOException {
        List<DynamicContainer> tests = new ArrayList<>();
        // handwritten integration tests
        for (String testFile : JSON_TEST_FILES) {
            tests.add(loadJsonTests(testFile));
        }
        // autogenerated corpus tests
        try (Stream<Path> stream = Files.list(resolveIntegrationTestPath("corpus-tests"))) {
           stream
                // ignore non-JSON files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                // ignore files that end with `.entities.json`
                .filter(path -> !path.getFileName().toString().endsWith(".entities.json"))
                // add the test
                .forEach(
                        path -> {
                            try {
                                tests.add(loadJsonTests(path.toAbsolutePath().toString()));
                            } catch (final IOException e) {
                                // inside the forEach we can't throw checked exceptions, but we
                                // can throw this unchecked exception
                                throw new UncheckedIOException(e);
                            } catch (final InternalException e) {
                                throw new RuntimeException(e);
                            }
                        });
       }
       return tests;
    }

    /**
     * Generates a test container for all the test requests in a json file. Each request is its own
     * test, and all the test in the json file are grouped into the returned container.
     */
    @SuppressFBWarnings("NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
    private DynamicContainer loadJsonTests(String jsonFile) throws InternalException, IOException {
        JsonTest test;
        try (InputStream jsonIn =
                new FileInputStream(resolveIntegrationTestPath(jsonFile).toFile())) {
            test = OBJECT_MAPPER.reader().readValue(jsonIn, JsonTest.class);
        }
        Set<Entity> entities = loadEntities(test.entities);
        PolicySet policySet = PolicySet.parsePolicies(resolveIntegrationTestPath(test.policies));
        Schema schema = loadSchema(test.schema);

        return DynamicContainer.dynamicContainer(
                jsonFile,
                Stream.concat(
                    Stream.of(DynamicTest.dynamicTest(
                                jsonFile + ": validate",
                                () ->
                                    executeJsonValidationTest(policySet.policies, schema, test.shouldValidate))),
                    test.requests.stream()
                        .map(
                                request ->
                                        DynamicTest.dynamicTest(
                                                jsonFile + ": " + request.description,
                                                () ->
                                                        executeJsonRequestTest(
                                                                entities, policySet, request,
                                                                schema)))));
    }

    /** Load the schema file. */
    private Schema loadSchema(String schemaFile) throws IOException {
        try (InputStream schemaStream =
                new FileInputStream(resolveIntegrationTestPath(schemaFile).toFile())) {
            String schemaText = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            return new Schema(schemaText);
        }
    }

    /**
     * Create an Entity from a (possibly escaped) EUID. If needed, the escape sequence "__entity" is used as the key for
     * each EUID.
     */
    @SuppressFBWarnings(
            value = "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
            justification = "Initialized by Jackson.")
    private Entity loadEntity(JsonEntity je) {

        Set<EntityUID> parents = je.parents
            .stream()
            .map(euid -> EntityUID.parseFromJson(euid).get())
            .collect(Collectors.toSet());


        return new Entity(EntityUID.parseFromJson(je.uid).get(), je.attrs, parents);
    }

    /**
     * Load all entities from the entity file. The entity file path must be relative to the shared
     * integration test root. This should be the case if the path was obtained from a JsonTest
     * object. The entities loaded directly from JSON require some processing to transform them into
     * Entity objects from the main Java API. The attributes map must be converted to a map to Value
     * objects instead of strings.
     */
    private Set<Entity> loadEntities(String entitiesFile) throws IOException {
        try (InputStream entitiesIn =
                new FileInputStream(resolveIntegrationTestPath(entitiesFile).toFile())) {
            return Arrays.stream(OBJECT_MAPPER.reader().readValue(entitiesIn, JsonEntity[].class))
                    .map(je -> loadEntity(je))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Check that the outcome of validation matches the expected result.
     */
    private void executeJsonValidationTest(Set<Policy> policies, Schema schema, Boolean shouldValidate) throws AuthException {
        AuthorizationEngine auth = new BasicAuthorizationEngine();
        ValidationRequest validationQuery = new ValidationRequest(schema, policies);
        try {
            ValidationResponse result = auth.validate(validationQuery);
            assertEquals(result.type, ValidationResponse.SuccessOrFailure.Success);
            if (shouldValidate) {
                assertTrue(result.validationPassed());
            }
        } catch (BadRequestException e) {
            // A `BadRequestException` is the results of a parsing error.
            // Some of our corpus tests fail to parse, so this is safe to ignore.
            assertFalse(shouldValidate);
        }
    }

    /**
     * This method implements the main test logic and assertions for each request. Given a set of
     * entities, set of policies, and a JsonRequest object, it executes the described request and checks
     * that the result is equal to the expected result.
     */
    private void executeJsonRequestTest(
            Set<Entity> entities, PolicySet policySet, JsonRequest request, Schema schema) throws AuthException {
        AuthorizationEngine auth = new BasicAuthorizationEngine();
        AuthorizationRequest authRequest =
                new AuthorizationRequest(
                    EntityUID.parseFromJson(request.principal).get(),
                    EntityUID.parseFromJson(request.action).get(),
                    EntityUID.parseFromJson(request.resource).get(),
                    Optional.of(request.context),
                    Optional.of(schema),
                    request.validateRequest);

        try {
            final AuthorizationResponse response = auth.isAuthorized(authRequest, policySet, entities);
            if (response.type == AuthorizationResponse.SuccessOrFailure.Success) {
                final var success = assertDoesNotThrow(() -> response.success.get());
                assertEquals(request.decision, success.getDecision());
                // convert to a HashSet to allow reordering
                assertEquals(new HashSet<>(request.reason), success.getReason());
                // The integration tests only record the id of the erroring policy,
                // not the full error message. So only check that the list lengths match.
                assertEquals(request.errors.size(), success.getErrors().size());
            } else {
                fail(String.format("Expected a success response but got %s", response));
            }
        } catch (BadRequestException e) {
            // In the case of parse errors, errors may disagree but the expected
            // decision should be `Deny`.
            assertEquals(request.decision, Decision.Deny);
        }
    }
}
