package com.cedarpolicy.benchmark;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.entity.Entities;
import com.cedarpolicy.model.exception.AuthException;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.wasm.CedarEngine;

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
import java.util.Set;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CedarBenchmark {

    // --- JNI ---
    private BasicAuthorizationEngine jniEngine;
    private AuthorizationRequest jniSmallRequest;
    private PolicySet jniSmallPolicySet;
    private Set<Entity> jniSmallEntities;

    private AuthorizationRequest jniMediumRequest;
    private PolicySet jniMediumPolicySet;
    private Set<Entity> jniMediumEntities;

    private AuthorizationRequest jniLargeRequest;
    private PolicySet jniLargePolicySet;
    private Set<Entity> jniLargeEntities;

    private PolicySet jniXLargePolicySet;

    // --- JNI Cached ---
    private PolicySet jniSmallPolicySetCached;
    private PolicySet jniMediumPolicySetCached;
    private PolicySet jniLargePolicySetCached;
    private PolicySet jniXLargePolicySetCached;

    // --- Wasm ---
    private CedarEngine wasmEngine;
    private String wasmSmallRequest;
    private String wasmMediumRequest;
    private String wasmLargeRequest;
    private String wasmXLargeRequest;

    // --- Wasm Cached ---
    private String wasmCachedSmallRequest;
    private String wasmCachedMediumRequest;
    private String wasmCachedLargeRequest;
    private String wasmCachedXLargeRequest;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        setUpJni();
        setUpWasm();
    }

    // ── JNI setup ──────────────────────────────────────────────────────

    private void setUpJni() throws Exception {
        jniEngine = new BasicAuthorizationEngine();

        EntityTypeName userType = EntityTypeName.parse("User").get();
        EntityTypeName actionType = EntityTypeName.parse("Action").get();
        EntityTypeName resourceType = EntityTypeName.parse("Resource").get();
        EntityTypeName photoType = EntityTypeName.parse("Photo").get();
        EntityTypeName docType = EntityTypeName.parse("Document").get();

        jniSmallRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("view"), resourceType.of("doc1"), new HashMap<>());
        jniSmallPolicySet = PolicySet.parsePolicies(readResource("/small_policies.cedar"));
        jniSmallEntities = Entities.parse(readResource("/small_entities.json")).getEntities();

        jniMediumRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("View_Photo"), photoType.of("pic01"), new HashMap<>());
        jniMediumPolicySet = PolicySet.parsePolicies(readResource("/medium_policies.cedar"));
        jniMediumEntities = Entities.parse(readResource("/medium_entities.json")).getEntities();

        HashMap<String, com.cedarpolicy.value.Value> context = new HashMap<>();
        context.put("sourceIp", new com.cedarpolicy.value.IpAddress("10.1.2.3"));
        context.put("requestId", new com.cedarpolicy.value.PrimString("req-bench-001"));
        jniLargeRequest = new AuthorizationRequest(
                userType.of("alice"), actionType.of("ViewDocument"), docType.of("doc1"), context);
        jniLargePolicySet = PolicySet.parsePolicies(readResource("/large_policies.cedar"));
        jniLargeEntities = Entities.parse(readResource("/large_entities.json")).getEntities();

        jniXLargePolicySet = buildJniXLargePolicies();

        jniSmallPolicySetCached = PolicySet.parsePolicies(readResource("/small_policies.cedar"));
        jniSmallPolicySetCached.cache();
        jniMediumPolicySetCached = PolicySet.parsePolicies(readResource("/medium_policies.cedar"));
        jniMediumPolicySetCached.cache();
        jniLargePolicySetCached = PolicySet.parsePolicies(readResource("/large_policies.cedar"));
        jniLargePolicySetCached.cache();
        jniXLargePolicySetCached = buildJniXLargePolicies();
        jniXLargePolicySetCached.cache();
    }

    private static PolicySet buildJniXLargePolicies() throws Exception {
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

    // ── Wasm setup ─────────────────────────────────────────────────────

    private void setUpWasm() throws Exception {
        wasmEngine = CedarEngine.create();

        String smallPolicies = readResource("/small_policies.cedar");
        String smallEntities = readResource("/small_entities.json");
        String mediumPolicies = readResource("/medium_policies.cedar");
        String mediumEntities = readResource("/medium_entities.json");
        String largePolicies = readResource("/large_policies.cedar");
        String largeEntities = readResource("/large_entities.json");
        String xlargePolicies = buildXLargePoliciesCedar();

        String largeContext = "{\"sourceIp\": {\"__extn\": {\"fn\": \"ip\", \"arg\": \"10.1.2.3\"}}, \"requestId\": \"req-bench-001\"}";

        wasmSmallRequest = wasmAuthRequest(
                entity("User", "alice"), entity("Action", "view"), entity("Resource", "doc1"),
                "{}", smallPolicies, smallEntities);
        wasmMediumRequest = wasmAuthRequest(
                entity("User", "alice"), entity("Action", "View_Photo"), entity("Photo", "pic01"),
                "{}", mediumPolicies, mediumEntities);
        wasmLargeRequest = wasmAuthRequest(
                entity("User", "alice"), entity("Action", "ViewDocument"), entity("Document", "doc1"),
                largeContext, largePolicies, largeEntities);
        wasmXLargeRequest = wasmAuthRequest(
                entity("User", "alice"), entity("Action", "view"), entity("Resource", "doc1"),
                "{}", xlargePolicies, smallEntities);

        // Pre-parse policy sets for cached benchmarks
        wasmEngine.preParsePolicySet("small", policySetFromCedar(smallPolicies));
        wasmEngine.preParsePolicySet("medium", policySetFromCedar(mediumPolicies));
        wasmEngine.preParsePolicySet("large", policySetFromCedar(largePolicies));
        wasmEngine.preParsePolicySet("xlarge", policySetFromCedar(xlargePolicies));

        wasmCachedSmallRequest = statefulRequest(
                entity("User", "alice"), entity("Action", "view"), entity("Resource", "doc1"),
                "{}", "small", smallEntities);
        wasmCachedMediumRequest = statefulRequest(
                entity("User", "alice"), entity("Action", "View_Photo"), entity("Photo", "pic01"),
                "{}", "medium", mediumEntities);
        wasmCachedLargeRequest = statefulRequest(
                entity("User", "alice"), entity("Action", "ViewDocument"), entity("Document", "doc1"),
                largeContext, "large", largeEntities);
        wasmCachedXLargeRequest = statefulRequest(
                entity("User", "alice"), entity("Action", "view"), entity("Resource", "doc1"),
                "{}", "xlarge", smallEntities);
    }

    private static String buildXLargePoliciesCedar() {
        String[] roles = {"admin", "editor", "viewer", "auditor", "deployer",
                          "security", "billing", "support", "developer", "manager"};
        String[] resources = {"Document", "Folder", "Project", "Comment", "Webhook",
                              "ApiKey", "AuditLog", "Setting", "Integration", "Pipeline",
                              "Environment", "Secret", "Deployment", "Report", "Dashboard",
                              "Alert", "Ticket", "Release", "Config", "Template"};
        var sb = new StringBuilder();
        for (String role : roles) {
            for (String resource : resources) {
                sb.append(String.format(
                        "permit(principal in UserGroup::\"%s-group\", action, resource is %s);%n",
                        role, resource));
            }
        }
        for (String resource : resources) {
            sb.append(String.format(
                    "forbid(principal, action, resource is %s) when { resource.archived };%n",
                    resource));
        }
        return sb.toString();
    }

    // ── Wasm helpers ───────────────────────────────────────────────────

    private static String entity(String type, String id) {
        return "{\"type\": \"" + type + "\", \"id\": \"" + id + "\"}";
    }

    private static String policySetFromCedar(String cedarText) {
        String[] policyTexts = cedarText.split("(?<=;)\\s*\n");
        var sb = new StringBuilder("{\"staticPolicies\": {");
        int idx = 0;
        for (String p : policyTexts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue;
            if (idx > 0) sb.append(", ");
            sb.append("\"p").append(idx).append("\": ");
            sb.append("\"").append(trimmed.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")).append("\"");
            idx++;
        }
        sb.append("}, \"templates\": {}, \"templateLinks\": []}");
        return sb.toString();
    }

    private static String wasmAuthRequest(String principal, String action, String resource,
                                           String context, String policiesCedar, String entitiesJson) {
        String policySet = policySetFromCedar(policiesCedar);
        return "{\"principal\": " + principal + ", \"action\": " + action + ", \"resource\": " + resource
                + ", \"context\": " + context + ", \"policies\": " + policySet + ", \"entities\": " + entitiesJson + "}";
    }

    private static String statefulRequest(String principal, String action, String resource,
                                           String context, String policySetId, String entitiesJson) {
        return "{\"principal\": " + principal + ", \"action\": " + action + ", \"resource\": " + resource
                + ", \"context\": " + context + ", \"preparsedPolicySetId\": \"" + policySetId
                + "\", \"validateRequest\": false, \"entities\": " + entitiesJson + "}";
    }

    // ── Utilities ──────────────────────────────────────────────────────

    private static String readResource(String path) throws Exception {
        return new String(
                Files.readAllBytes(Paths.get(CedarBenchmark.class.getResource(path).toURI())),
                StandardCharsets.UTF_8);
    }

    // ═══════════════════════════════════════════════════════════════════
    // JNI Benchmarks
    // ═══════════════════════════════════════════════════════════════════

    @Benchmark
    public AuthorizationResponse jniUncachedSmall() throws AuthException {
        return jniEngine.isAuthorized(jniSmallRequest, jniSmallPolicySet, jniSmallEntities);
    }

    @Benchmark
    public AuthorizationResponse jniUncachedMedium() throws AuthException {
        return jniEngine.isAuthorized(jniMediumRequest, jniMediumPolicySet, jniMediumEntities);
    }

    @Benchmark
    public AuthorizationResponse jniUncachedLarge() throws AuthException {
        return jniEngine.isAuthorized(jniLargeRequest, jniLargePolicySet, jniLargeEntities);
    }

    @Benchmark
    public AuthorizationResponse jniUncachedXLarge() throws AuthException {
        return jniEngine.isAuthorized(jniSmallRequest, jniXLargePolicySet, jniSmallEntities);
    }

    // ═══════════════════════════════════════════════════════════════════
    // JNI Cached Benchmarks
    // ═══════════════════════════════════════════════════════════════════

    @Benchmark
    public AuthorizationResponse jniCachedSmall() throws AuthException {
        return jniEngine.isAuthorized(jniSmallRequest, jniSmallPolicySetCached, jniSmallEntities);
    }

    @Benchmark
    public AuthorizationResponse jniCachedMedium() throws AuthException {
        return jniEngine.isAuthorized(jniMediumRequest, jniMediumPolicySetCached, jniMediumEntities);
    }

    @Benchmark
    public AuthorizationResponse jniCachedLarge() throws AuthException {
        return jniEngine.isAuthorized(jniLargeRequest, jniLargePolicySetCached, jniLargeEntities);
    }

    @Benchmark
    public AuthorizationResponse jniCachedXLarge() throws AuthException {
        return jniEngine.isAuthorized(jniSmallRequest, jniXLargePolicySetCached, jniSmallEntities);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Wasm Benchmarks
    // ═══════════════════════════════════════════════════════════════════

    @Benchmark
    public String wasmUncachedSmall() {
        return wasmEngine.authorize( wasmSmallRequest);
    }

    @Benchmark
    public String wasmUncachedMedium() {
        return wasmEngine.authorize( wasmMediumRequest);
    }

    @Benchmark
    public String wasmUncachedLarge() {
        return wasmEngine.authorize( wasmLargeRequest);
    }

    @Benchmark
    public String wasmUncachedXLarge() {
        return wasmEngine.authorize( wasmXLargeRequest);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Wasm Cached Benchmarks
    // ═══════════════════════════════════════════════════════════════════

    @Benchmark
    public String wasmCachedSmall() {
        return wasmEngine.statefulAuthorize(wasmCachedSmallRequest);
    }

    @Benchmark
    public String wasmCachedMedium() {
        return wasmEngine.statefulAuthorize(wasmCachedMediumRequest);
    }

    @Benchmark
    public String wasmCachedLarge() {
        return wasmEngine.statefulAuthorize(wasmCachedLargeRequest);
    }

    @Benchmark
    public String wasmCachedXLarge() {
        return wasmEngine.statefulAuthorize(wasmCachedXLargeRequest);
    }
}
