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

import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.model.schema.Schema.JsonOrCedar;
import com.cedarpolicy.model.policy.TemplateLink;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.policy.LinkValue;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static com.cedarpolicy.CedarJson.objectWriter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Utils to help with tests. */
public final class TestUtil {
    /** The escape sequence Cedar uses for an entity reference nested inside a value. */
    private static final String ENTITY_ESCAPE_SEQ = "__entity";

    private TestUtil() {
    }

    /**
     * Assert that an object serializes to the expected JSON. Compared semantically rather than as strings: neither the
     * order of an object's fields nor the order of an array's elements is part of Cedar's encoding, and both are in fact
     * unspecified here, because parents are serialized from a {@code Set} and attributes and tags from an immutable map
     * whose iteration order varies between JVM runs.
     *
     * @param expectedJSON The expected encoding.
     * @param obj          The object to serialize.
     */
    public static void assertJSONEqual(JsonNode expectedJSON, Object obj) {
        String objJson = assertDoesNotThrow(() -> objectWriter().writeValueAsString(obj));
        try {
            JSONAssert.assertEquals(expectedJSON.toString(), objJson, JSONCompareMode.NON_EXTENSIBLE);
        } catch (JSONException e) {
            throw new AssertionError("Failed to compare JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Assert that an exception's message mentions every one of the given fragments. Checking several fragments rather
     * than one keeps the assertion specific enough to distinguish the rule that was violated, while still tolerating
     * rewording elsewhere in the message.
     *
     * @param e         The exception to inspect.
     * @param fragments The fragments the message must contain.
     */
    public static void assertMessageContains(Throwable e, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(e.getMessage() != null && e.getMessage().contains(fragment),
                    "Expected the error to mention '%s' but was: '%s'".formatted(fragment, e.getMessage()));
        }
    }

    /**
     * Build an entity UID in the escaped {@code __entity} form.
     *
     * @param type The entity type name.
     * @param id   The entity id.
     * @return The encoded UID.
     */
    public static ObjectNode buildEuidObject(String type, String id) {
        var n = JsonNodeFactory.instance.objectNode();
        var inner = JsonNodeFactory.instance.objectNode();
        inner.put("id", id);
        inner.put("type", type);
        n.replace(ENTITY_ESCAPE_SEQ, inner);
        return n;
    }

    /**
     * Build a partial entity UID whose id is unknown.
     *
     * @param type The entity type name.
     * @return The encoded UID.
     */
    public static ObjectNode buildUidObject(String type) {
        var n = JsonNodeFactory.instance.objectNode();
        n.put("type", type);
        return n;
    }

    /**
     * Build an entity UID in the bare {@code {"type", "id"}} form.
     *
     * @param type The entity type name.
     * @param id   The entity id.
     * @return The encoded UID.
     */
    public static ObjectNode buildUidObject(String type, String id) {
        var n = buildUidObject(type);
        n.put("id", id);
        return n;
    }

    /**
     * Load schema file.
     *
     * @param schemaFile Schema file name
     */
    public static Schema loadSchemaResource(String schemaFile) {
        try {
            String text = new String(Files.readAllBytes(
                    Paths.get(
                            ValidationTests.class.getResource(schemaFile).toURI())),
                    StandardCharsets.UTF_8);
            return new Schema(JsonOrCedar.Json, Optional.of(text), Optional.empty());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test schema file " + schemaFile, e);
        }
    }

    public static Schema loadCedarSchemaResource(String schemaFile) {
        try {
            String text = new String(Files.readAllBytes(
                    Paths.get(
                            ValidationTests.class.getResource(schemaFile).toURI())),
                    StandardCharsets.UTF_8);
            return new Schema(JsonOrCedar.Cedar, Optional.empty(), Optional.of(text));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test schema file " + schemaFile, e);
        }
    }

    public static PolicySet buildValidPolicySet() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Set<Policy> policies = new HashSet<>();
        Set<Policy> templates = new HashSet<>();
        ArrayList<TemplateLink> templateLinks = new ArrayList<TemplateLink>();
        ArrayList<LinkValue> linkValueList = new ArrayList<>();

        String fullPolicy =
                "permit(principal == User::\"Bob\", action == Action::\"View_Photo\", resource in Album::\"Vacation\");";
        Policy newPolicy = new Policy(fullPolicy, "p1");
        policies.add(newPolicy);

        String template = "permit(principal == ?principal, action == Action::\"View_Photo\", resource in Album::\"Vacation\");";
        Policy policyTemplate = new Policy(template, "t0");
        templates.add(policyTemplate);

        Entity principal = new Entity(principalType.of("Alice"), new HashMap<>(), new HashSet<>());
        LinkValue principalLinkValue = new LinkValue("?principal", principal.getEUID());
        linkValueList.add(principalLinkValue);

        TemplateLink templateLink = new TemplateLink("t0", "tl0", linkValueList);
        templateLinks.add(templateLink);

        return new PolicySet(policies, templates, templateLinks);
    }

    public static PolicySet buildInvalidPolicySet() {
        EntityTypeName principalType = EntityTypeName.parse("User").get();
        Set<Policy> policies = new HashSet<>();
        Set<Policy> templates = new HashSet<>();
        ArrayList<TemplateLink> templateLinks = new ArrayList<TemplateLink>();
        ArrayList<LinkValue> linkValueList = new ArrayList<>();

        String fullPolicy =
                "permit(prinipal == User::\"Bob\", action == Action::\"View_Photo\", resource in Album::\"Vacation\");";
        Policy newPolicy = new Policy(fullPolicy, "p1");
        policies.add(newPolicy);

        String template = "permit(principal, action == Action::\"View_Photo\", resource in Album::\"Vacation\");";
        Policy policyTemplate = new Policy(template, "t0");
        templates.add(policyTemplate);

        Entity principal = new Entity(principalType.of("Alice"), new HashMap<>(), new HashSet<>());
        LinkValue principalLinkValue = new LinkValue("?principal", principal.getEUID());
        linkValueList.add(principalLinkValue);

        TemplateLink templateLink = new TemplateLink("t0", "tl0", linkValueList);
        templateLinks.add(templateLink);

        return new PolicySet(policies, templates, templateLinks);
    }

}
