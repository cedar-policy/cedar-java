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

    // Small scenario
    private AuthorizationRequest smallRequest;
    private PolicySet smallPolicySet;
    private PolicySet smallPolicySetCached;
    private Set<Entity> smallEntities;

    // Medium scenario
    private AuthorizationRequest mediumRequest;
    private PolicySet mediumPolicySet;
    private PolicySet mediumPolicySetCached;
    private Set<Entity> mediumEntities;
    private Schema mediumSchema;

    // Large scenario
    private AuthorizationRequest largeRequest;
    private PolicySet largePolicySet;
    private PolicySet largePolicySetCached;
    private Set<Entity> largeEntities;
    private Schema largeSchema;

    // XLarge scenario (220 policies)
    private PolicySet xlargePolicySet;
    private PolicySet xlargePolicySetCached;

    // Validation
    private ValidationRequest validationRequest;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        engine = new BasicAuthorizationEngine();

        setUpSmallScenario();
        setUpMediumScenario();
        setUpLargeScenario();
        setUpXLargeScenario();
        setUpValidationScenario();

        // Create cached copies
        smallPolicySetCached = PolicySet.parsePolicies(readResource("/small_policies.cedar"));
        smallPolicySetCached.cache();

        mediumPolicySetCached = PolicySet.parsePolicies(readResource("/medium_policies.cedar"));
        mediumPolicySetCached.cache();
        mediumSchema.cache();

        largePolicySetCached = PolicySet.parsePolicies(readResource("/large_policies.cedar"));
        largePolicySetCached.cache();
        largeSchema.cache();

        xlargePolicySetCached = buildXLargePolicies();
        xlargePolicySetCached.cache();
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

        String schemaText = readResource("/photoflash_schema.json");
        mediumSchema = new Schema(JsonOrCedar.Json, Optional.of(schemaText), Optional.empty());
    }

    private void setUpLargeScenario() throws Exception {
        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName docType = EntityTypeName.parse("Document").get();

        HashMap<String, com.cedarpolicy.value.Value> context = new HashMap<>();
        context.put("sourceIp", new com.cedarpolicy.value.IpAddress("10.1.2.3"));
        context.put("requestId", new com.cedarpolicy.value.PrimString("req-bench-001"));
        largeRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("ViewDocument"), docType.of("doc1"), context);

        largePolicySet = PolicySet.parsePolicies(readResource("/large_policies.cedar"));
        largeEntities = Entities.parse(readResource("/large_entities.json")).getEntities();

        String schemaText = readResource("/large_schema.json");
        largeSchema = new Schema(JsonOrCedar.Json, Optional.of(schemaText), Optional.empty());
    }

    private static PolicySet buildXLargePolicies() throws Exception {
        String[] roles = {"admin", "editor", "viewer", "auditor", "deployer",
                          "security", "billing", "support", "developer", "manager"};
        String[] resources = {"Document", "Folder", "Project", "Comment", "Webhook",
                              "ApiKey", "AuditLog", "Setting", "Integration", "Pipeline",
                              "Environment", "Secret", "Deployment", "Report", "Dashboard",
                              "Alert", "Ticket", "Release", "Config", "Template"};

        StringBuilder policies = new StringBuilder();
        for (String role : roles) {
            for (String resource : resources) {
                policies.append(String.format(
                        "permit(principal in UserGroup::\"%s-group\", action, resource is %s);%n",
                        role, resource));
            }
        }
        for (String resource : resources) {
            policies.append(String.format(
                    "forbid(principal, action, resource is %s) when { resource.archived };%n",
                    resource));
        }
        return PolicySet.parsePolicies(policies.toString());
    }

    private void setUpXLargeScenario() throws Exception {
        xlargePolicySet = buildXLargePolicies();
    }

    private void setUpValidationScenario() throws Exception {
        PolicySet policySet = PolicySet.parsePolicies(
                "permit(principal == User::\"alice\", action == Action::\"View_Photo\", resource);");
        validationRequest = new ValidationRequest(mediumSchema, policySet);
    }

    // --- Uncached baselines ---

    @Benchmark
    public AuthorizationResponse uncachedSmall() throws AuthException {
        return engine.isAuthorized(smallRequest, smallPolicySet, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedMedium() throws AuthException {
        return engine.isAuthorized(mediumRequest, mediumPolicySet, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedLarge() throws AuthException {
        return engine.isAuthorized(largeRequest, largePolicySet, largeEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedXLarge() throws AuthException {
        return engine.isAuthorized(smallRequest, xlargePolicySet, smallEntities);
    }

    // --- Cached (policies + schema via .cache() API) ---

    @Benchmark
    public AuthorizationResponse cachedSmall() throws AuthException {
        return engine.isAuthorized(smallRequest, smallPolicySetCached, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedMedium() throws AuthException {
        return engine.isAuthorized(mediumRequest, mediumPolicySetCached, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedLarge() throws AuthException {
        return engine.isAuthorized(largeRequest, largePolicySetCached, largeEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedXLarge() throws AuthException {
        return engine.isAuthorized(smallRequest, xlargePolicySetCached, smallEntities);
    }

    // --- Cross-product: policy size x entity size ---

    // Small policies x {medium, large} entities
    @Benchmark
    public AuthorizationResponse uncachedSmallPolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, smallPolicySet, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedSmallPolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, smallPolicySetCached, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedSmallPolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, smallPolicySet, largeEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedSmallPolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, smallPolicySetCached, largeEntities);
    }

    // Medium policies x {small, large} entities
    @Benchmark
    public AuthorizationResponse uncachedMediumPolicySmallEntities() throws AuthException {
        return engine.isAuthorized(smallRequest, mediumPolicySet, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedMediumPolicySmallEntities() throws AuthException {
        return engine.isAuthorized(smallRequest, mediumPolicySetCached, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedMediumPolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, mediumPolicySet, largeEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedMediumPolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, mediumPolicySetCached, largeEntities);
    }

    // Large policies x {small, medium} entities
    @Benchmark
    public AuthorizationResponse uncachedLargePolicySmallEntities() throws AuthException {
        return engine.isAuthorized(smallRequest, largePolicySet, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedLargePolicySmallEntities() throws AuthException {
        return engine.isAuthorized(smallRequest, largePolicySetCached, smallEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedLargePolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, largePolicySet, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedLargePolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, largePolicySetCached, mediumEntities);
    }

    // XLarge policies x {small, medium, large} entities
    // (XLarge x small is already 'cachedXLarge' / 'uncachedXLarge' above)
    @Benchmark
    public AuthorizationResponse uncachedXLargePolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, xlargePolicySet, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedXLargePolicyMediumEntities() throws AuthException {
        return engine.isAuthorized(mediumRequest, xlargePolicySetCached, mediumEntities);
    }

    @Benchmark
    public AuthorizationResponse uncachedXLargePolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, xlargePolicySet, largeEntities);
    }

    @Benchmark
    public AuthorizationResponse cachedXLargePolicyLargeEntities() throws AuthException {
        return engine.isAuthorized(largeRequest, xlargePolicySetCached, largeEntities);
    }

    // --- Validation ---

    @Benchmark
    public ValidationResponse validateSmall() throws AuthException {
        return engine.validate(validationRequest);
    }
}
