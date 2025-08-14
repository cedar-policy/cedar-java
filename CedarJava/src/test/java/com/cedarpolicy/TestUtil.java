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

import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/** Utils to help with tests. */
public final class TestUtil {
    private TestUtil() {
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
