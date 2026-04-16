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
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;

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
import java.util.HashSet;
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

    private void setUpSmallScenario() {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName resourceType = EntityTypeName.parse("Resource").get();

        EntityUID principal = userType.of("alice");
        EntityUID action = actionType.of("view");
        EntityUID resource = resourceType.of("doc1");

        smallRequest = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        Set<Policy> policies = new HashSet<>();
        policies.add(new Policy("permit(principal, action, resource);", "policy0"));
        smallPolicySet = new PolicySet(policies);

        smallEntities = new HashSet<>();
        smallEntities.add(new Entity(principal, new HashMap<>(), new HashSet<>()));
        smallEntities.add(new Entity(action, new HashMap<>(), new HashSet<>()));
        smallEntities.add(new Entity(resource, new HashMap<>(), new HashSet<>()));
    }

    private void setUpMediumScenario() throws Exception {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName photoType = EntityTypeName.parse("Photo").get();
        EntityTypeName albumType = EntityTypeName.parse("Album").get();
        EntityTypeName accountType = EntityTypeName.parse("Account").get();

        EntityUID principal = userType.of("alice");
        EntityUID action = actionType.of("View_Photo");
        EntityUID resource = photoType.of("pic01");

        mediumRequest = new AuthorizationRequest(principal, action, resource, new HashMap<>());

        Set<Policy> policies = new HashSet<>();
        policies.add(new Policy(
                "permit(principal == User::\"alice\", action == Action::\"View_Photo\", resource);", "p0"));
        policies.add(new Policy(
                "permit(principal, action == Action::\"View_Photo\", resource in Album::\"vacation\");", "p1"));
        policies.add(new Policy(
                "forbid(principal, action, resource) when { resource.private };", "p2"));
        policies.add(new Policy(
                "permit(principal, action == Action::\"Edit_Photo\", resource) "
                        + "when { principal == resource.owner };",
                "p3"));
        policies.add(new Policy(
                "permit(principal, action == Action::\"Delete_Photo\", resource) "
                        + "when { principal == resource.owner };",
                "p4"));
        mediumPolicySet = new PolicySet(policies);

        // Build entity graph
        mediumEntities = new HashSet<>();

        EntityUID albumId = albumType.of("vacation");
        EntityUID accountId = accountType.of("account1");

        Set<EntityUID> photoParents = new HashSet<>();
        photoParents.add(albumId);

        Set<EntityUID> albumParents = new HashSet<>();
        albumParents.add(accountId);

        mediumEntities.add(new Entity(principal, new HashMap<>(), new HashSet<>()));
        mediumEntities.add(new Entity(action, new HashMap<>(), new HashSet<>()));
        mediumEntities.add(new Entity(resource, new HashMap<>(), photoParents));
        mediumEntities.add(new Entity(albumId, new HashMap<>(), albumParents));
        mediumEntities.add(new Entity(accountId, new HashMap<>(), new HashSet<>()));
    }

    private void setUpValidationScenario() throws Exception {
        String schemaText = new String(
                Files.readAllBytes(Paths.get(
                        AuthorizationBenchmark.class.getResource("/photoflash_schema.json").toURI())),
                StandardCharsets.UTF_8);
        Schema schema = new Schema(JsonOrCedar.Json, Optional.of(schemaText), Optional.empty());

        Set<Policy> policies = new HashSet<>();
        policies.add(new Policy(
                "permit(principal == User::\"alice\", action == Action::\"View_Photo\", resource);", "p0"));
        PolicySet policySet = new PolicySet(policies);

        validationRequest = new ValidationRequest(schema, policySet);
    }

    @Benchmark
    public AuthorizationResponse isAuthorized_small() throws AuthException {
        return engine.isAuthorized(smallRequest, smallPolicySet, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse isAuthorized_medium() throws AuthException {
        return engine.isAuthorized(mediumRequest, mediumPolicySet, mediumEntities);
    }

    @Benchmark
    public ValidationResponse validate_small() throws AuthException {
        return engine.validate(validationRequest);
    }
}
