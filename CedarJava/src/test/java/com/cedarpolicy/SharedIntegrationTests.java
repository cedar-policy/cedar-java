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

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.slice.BasicSlice;
import com.cedarpolicy.model.slice.Entity;
import com.cedarpolicy.model.slice.Policy;
import com.cedarpolicy.model.slice.Slice;
import com.cedarpolicy.serializer.JsonEUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String CEDAR_INTEGRATION_TESTS_ROOT =
            Objects.requireNonNull(
                    System.getenv("CEDAR_INTEGRATION_TESTS_ROOT"),
                    "Environment variable CEDAR_INTEGRATION_TESTS_ROOT is required "
                            + "for shared integration tests but is not present.");

    /**
     * For relative paths, return an absolute path rooted in the shared integration test root. For
     * absolute paths, return them unchanged.
     *
     * @param path Path string representing either a relative path for a file in the shared
     *     integration tests, or an absolute path.
     * @return A Path object containing an absolute path.
     */
    private Path resolveIntegrationTestPath(String path) {
        if (Paths.get(path).isAbsolute()) {
            return Paths.get(path);
        } else {
            return Paths.get(CEDAR_INTEGRATION_TESTS_ROOT, path);
        }
    }

    /**
     * Directly corresponds to the structure of the JSON formatted tests files. The fields are
     * populated by Jackson when the test files are deserialized.
     */
    @SuppressWarnings("visibilitymodifier")
    @JsonDeserialize
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
        @JsonProperty("should_validate")
        public boolean shouldValidate;

        /** List of queries with their expected result. */
        public List<JsonQuery> queries;
    }

    /** Directly corresponds to the structure of a query in the JSON formatted tests files. */
    @SuppressWarnings("visibilitymodifier")
    @JsonDeserialize
    private static class JsonQuery {
        /** Textual description of the query. */
        public String desc;

        /** Principal entity uid used for the query. */
        public String principal;

        /** Action entity uid used for the query. */
        public String action;

        /** Resource entity uid used for the query. */
        public String resource;

        /** Context map used for the query. */
        public Map<String, Value> context;

        /** The expected decision that should be returned by the authorization engine. */
        public AuthorizationResponse.Decision decision;

        /** The expected reason list that should be returned by the authorization engine. */
        public List<String> reasons;

        /** The expected error list that should be returned by the authorization engine. */
        public List<String> errors;
    }

    /**
     * Directly corresponds to the structure of an entity in JSON entity file. Note that it is not
     * quite the same as the Entity class in the main Java API. The attrs map is from String to
     * String, rather than String to Values.
     */
    @SuppressWarnings("visibilitymodifier")
    @JsonDeserialize
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
        "tests/example_use_cases_doc/1a.json_typo",
        "tests/example_use_cases_doc/2a.json",
        "tests/example_use_cases_doc/2b.json",
        "tests/example_use_cases_doc/2c.json",
        "tests/example_use_cases_doc/3a.json",
        "tests/example_use_cases_doc/3b.json",
        "tests/example_use_cases_doc/3c.json",
        // "tests/example_use_cases_doc/4a.json", // currently disabled due to action attributes
        // "tests/example_use_cases_doc/4c.json", // currently disabled due to action attributes
        // "tests/example_use_cases_doc/4d.json", // currently disabled due to action attributes
        // "tests/example_use_cases_doc/4e.json", // currently disabled due to action attributes
        // "tests/example_use_cases_doc/4f.json", // currently disabled due to action attributes
        // "tests/example_use_cases_doc/5b.json", // currently disabled due to action attributes
        // Need to change extension handling to match natural JSON CRs
        "tests/ip/1.json",
        "tests/ip/2.json",
        "tests/ip/3.json",
        "tests/multi/1.json",
        "tests/multi/2.json",
        // "tests/multi/3.json", // currently disabled because it uses action attributes
        // "tests/multi/4.json", // currently disabled because it uses action attributes
        // "tests/multi/5.json", // currently disabled because it uses action attributes
    };

    /**
     * This method is the main entry point for JUnit. It returns a list of containers, which contain
     * tests for junit to run. JUnit will run all the test returned from this method.
     */
    @TestFactory
    public List<DynamicContainer> integrationTestsFromJson() throws IOException {
        List<DynamicContainer> tests = new ArrayList<>();
        //If we can't find the `cedar` package, don't try to load integration tests.
        //In CI, MUST_RUN_CEDAR_INTEGRATION_TESTS is set
        if(System.getenv("MUST_RUN_CEDAR_INTEGRATION_TESTS") == null && Files.notExists(Paths.get(CEDAR_INTEGRATION_TESTS_ROOT, "corpus_tests"))) {
            return tests;
        }
        // tests other than corpus tests
        for (String testFile : JSON_TEST_FILES) {
            tests.add(loadJsonTests(testFile));
        }
        // corpus tests
        try (Stream<Path> stream =
                Files.list(Paths.get(CEDAR_INTEGRATION_TESTS_ROOT, "corpus_tests"))) {
            stream
                    // ignore non-JSON files
                    .filter(path -> path.endsWith(".json"))
                    // ignore files that start with policies_, entities_, or schema_
                    .filter(
                            path ->
                                    !path.startsWith("policies_")
                                            && !path.startsWith("entities_")
                                            && !path.startsWith("schema_"))
                    // add the test
                    .forEach(
                            path -> {
                                try {
                                    tests.add(loadJsonTests(path.toAbsolutePath().toString()));
                                } catch (final IOException e) {
                                    // inside the forEach we can't throw checked exceptions, but we
                                    // can throw this unchecked exception
                                    throw new UncheckedIOException(e);
                                }
                            });
        }
        return tests;
    }

    /**
     * Generates a test container for all the test queries in a json file. Each query is its own
     * test, and all the test in the json file are grouped into the returned container.
     */
    private DynamicContainer loadJsonTests(String jsonFile) throws IOException {
        JsonTest test;
        try (InputStream jsonIn =
                new FileInputStream(resolveIntegrationTestPath(jsonFile).toFile())) {
            test = OBJECT_MAPPER.reader().readValue(jsonIn, JsonTest.class);
        }
        Set<Entity> entities = loadEntities(test.entities);
        Set<Policy> policies = loadPolicies(test.policies);
        Schema schema = loadSchema(test.schema);

        return DynamicContainer.dynamicContainer(
                jsonFile,
                test.queries.stream()
                        .map(
                                query ->
                                        DynamicTest.dynamicTest(
                                                jsonFile + ": " + query.desc,
                                                () ->
                                                        executeJsonQueryTest(
                                                                entities, policies, query,
                                                                schema))));
    }

    /**
     * Load all policies from the policy file. The policy file path must be relative to the shared
     * integration test root. This should be the case if the path was obtained from a JsonTest
     * object. Extra processing is required because the test format does not include policy ids, and
     * does not explicit separate policies in a file other than by semicolons.
     */
    private Set<Policy> loadPolicies(String policiesFile) throws IOException {
        // Get a list of the policy sources for the individual policies in the file by splitting the
        // full policy source
        // on semicolons. This will break if a semicolon shows up in a string or comment.
        String[] policiesStrings =
                String.join("\n", Files.readAllLines(resolveIntegrationTestPath(policiesFile)))
                        .split(";");
        Set<Policy> policies = new HashSet<>();
        for (int i = 0; i < policiesStrings.length; i++) {
            // The policy source doesn't include an explicit policy id, but the expected output
            // implicitly assumes policies are numbered by their position in file.
            String policyId = "policy" + i;
            String policySrc = policiesStrings[i];
            if (!policySrc.trim().isEmpty()) {
                policies.add(new Policy(policySrc + ";", policyId));
            }
        }
        return policies;
    }

    /** Load the schema file. */
    private Schema loadSchema(String schemaFile) throws IOException {
        try (InputStream schemaIn =
                new FileInputStream(resolveIntegrationTestPath(schemaFile).toFile())) {
            return new Schema(OBJECT_MAPPER.reader().readValue(schemaIn, JsonNode.class));
        }
    }

    /**
     * Create an entity from escaped Euids. The escape sequence "__expr" is used as the key for each
     * Euid.
     */
    @SuppressFBWarnings(
            value = "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
            justification = "Initialized by Jackson.")
    private Entity loadEntity(JsonEntity je) {
        HashSet<String> parents = new HashSet<String>();

        je.parents.forEach(p -> parents.add(p.toString()));
        return new Entity(je.uid.toString(), je.attrs, parents);
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
     * This method implements the main test logic and assertions for each query. Given a set of
     * entities, set of policies, and a JsonQuery object, it executes the described query and checks
     * that the result is equal to the excepted result.
     */
    private void executeJsonQueryTest(
            Set<Entity> entities, Set<Policy> policies, JsonQuery query, Schema schema) {
        AuthorizationEngine auth = new WrapperAuthorizationEngine();
        AuthorizationRequest authQuery =
                new AuthorizationRequest(
                        query.principal,
                        query.action,
                        query.resource,
                        query.context,
                        Optional.of(schema));
        Slice slice = new BasicSlice(policies, entities);
        AuthorizationResponse result = assertDoesNotThrow(() -> auth.isAuthorized(authQuery, slice));

        assertEquals(query.decision, result.getDecision());
        assertEquals(query.errors, result.getErrors());
        assertEquals(new HashSet<>(query.reasons), result.getReasons());
    }
}
