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

import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.cedarpolicy.value.EntityTypeName;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Cedar authorization engine JNI performance.
 *
 * <p>Run via: ./gradlew jmh
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class AuthorizationBenchmark {

    private BasicAuthorizationEngine engine;

    // Small scenario: minimal request
    private AuthorizationRequest smallRequest;
    private PolicySet smallPolicySet;
    private Set<Entity> smallEntities;

    // Medium scenario: photoflash schema with entities and multiple policies
    private AuthorizationRequest mediumRequest;
    private PolicySet mediumPolicySet;
    private Set<Entity> mediumEntities;

    // Validation scenario
    private ValidationRequest validationRequest;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        engine = new BasicAuthorizationEngine();

        setUpSmallScenario();
        setUpMediumScenario();
        setUpValidationScenario();
    }

    private static String readResource(String resourcePath) throws Exception {
        return new String(
                Files.readAllBytes(Paths.get(
                        AuthorizationBenchmark.class.getResource(resourcePath).toURI())),
                StandardCharsets.UTF_8);
    }

    private void setUpSmallScenario() throws Exception {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName resourceType = EntityTypeName.parse("Resource").get();

        smallRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("view"), resourceType.of("doc1"), new HashMap<>());

        smallPolicySet = PolicySet.parsePolicies(readResource("/small_policies.cedar"));
        smallEntities = Entities.parse(readResource("/small_entities.json")).getEntities();
    }

    private void setUpMediumScenario() throws Exception {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName photoType = EntityTypeName.parse("Photo").get();

        mediumRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("View_Photo"), photoType.of("pic01"), new HashMap<>());

        mediumPolicySet = PolicySet.parsePolicies(readResource("/medium_policies.cedar"));
        mediumEntities = Entities.parse(readResource("/medium_entities.json")).getEntities();
    }

    private void setUpValidationScenario() throws Exception {
        String schemaText = readResource("/photoflash_schema.json");
        Schema schema = new Schema(JsonOrCedar.Json, Optional.of(schemaText), Optional.empty());

        PolicySet policySet = PolicySet.parsePolicies(
                "permit(principal == User::\"alice\", action == Action::\"View_Photo\", resource);");

        validationRequest = new ValidationRequest(schema, policySet);
    }

    @Benchmark
    public AuthorizationResponse isAuthorizedSmall() throws AuthException {
        return engine.isAuthorized(smallRequest, smallPolicySet, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse isAuthorizedMedium() throws AuthException {
        return engine.isAuthorized(mediumRequest, mediumPolicySet, mediumEntities);
    }

    @Benchmark
    public ValidationResponse validateSmall() throws AuthException {
        return engine.validate(validationRequest);
    }
}
