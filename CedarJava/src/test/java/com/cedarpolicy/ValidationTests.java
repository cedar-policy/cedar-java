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

import static com.cedarpolicy.TestUtil.loadSchemaResource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cedarpolicy.model.DetailedError;
import com.cedarpolicy.model.ValidationRequest;
import com.cedarpolicy.model.ValidationResponse;
import com.cedarpolicy.model.ValidationResponse.SuccessOrFailure;
import com.cedarpolicy.model.ValidationResponse.ValidationSuccessResponse;
import com.cedarpolicy.model.policy.LinkValue;
import com.cedarpolicy.model.policy.Policy;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.model.policy.TemplateLink;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityUID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the validator. */
public class ValidationTests {
    private Schema schema;
    private PolicySet policies;

    private static AuthorizationEngine engine;

    /** Test. */
    @Test
    public void givenEmptySchemaAndNoPolicyReturnsValid() {
        givenSchema(EMPTY_SCHEMA);
        ValidationResponse response = whenValidated();
        thenIsValid(response);
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
        ValidationResponse response = whenValidated();
        thenIsValid(response);
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
        ValidationResponse response = whenValidated();
        thenIsNotValid(response);
    }

    /** Test. */
    @Test
    public void givenInvalidPolicyThrowsBadRequestError() {
        givenSchema(EMPTY_SCHEMA);
        givenPolicy("policy0", "permit { }");
        ValidationResponse response = whenValidated();
        thenValidationFailed(response);
    }

    @Test
    public void validateTemplateLinkedPolicySuccessTest() {
        givenSchema(LIBRARY_SCHEMA);

        Set<Policy> templates = new HashSet<>();
        templates.add(new Policy("permit(principal == ?principal, action, resource in ?resource);", "template0"));
        templates.add(new Policy("permit(principal, action, resource in ?resource);", "template1"));
        templates.add(new Policy("permit(principal == ?principal, action, resource);", "template2"));

        List<TemplateLink> templateLinks = new ArrayList<>();
        LinkValue principalLink = new LinkValue("?principal", EntityUID.parse("Library::User::\"Victor\"").get());
        LinkValue resourceLink1 = new LinkValue("?resource",
                EntityUID.parse("Library::Book::\"The black Swan\"").get());
        LinkValue resourceLink2 = new LinkValue("?resource",
                EntityUID.parse("Library::Book::\"Thinking Fast and Slow\"").get());
        templateLinks.add(new TemplateLink("template0", "policy0", List.of(principalLink, resourceLink1)));
        templateLinks.add(new TemplateLink("template1", "policy1", List.of(resourceLink2)));
        templateLinks.add(new TemplateLink("template2", "policy2", List.of(principalLink)));

        this.policies = new PolicySet(new HashSet<>(), templates, templateLinks);
        ValidationResponse response = whenValidated();
        thenIsValid(response);
    }

    @Test
    public void validateTemplateLinkedPolicyFailsWhenExpected() {
        givenSchema(LIBRARY_SCHEMA);

        Set<Policy> templates = new HashSet<>();
        templates.add(new Policy("permit(principal == ?principal, action, resource in ?resource);", "template0"));
        templates.add(new Policy("permit(principal, action, resource in ?resource);", "template1"));
        templates.add(new Policy("permit(principal == ?principal, action, resource);", "template2"));

        LinkValue principalLink = new LinkValue("?principal", EntityUID.parse("Library::User::\"Victor\"").get());
        LinkValue resourceLink = new LinkValue("?resource", EntityUID.parse("Library::Book::\"The black Swan\"").get());

        // fails if we provide a value for the wrong slot
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template1", "policy", List.of(principalLink))));
        ValidationResponse response2 = whenValidated();
        thenValidationFailed(response2);

        // fails if we provide a value for too many slots
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template2", "policy", List.of(principalLink, resourceLink))));
        ValidationResponse response3 = whenValidated();
        thenValidationFailed(response3);

        // fails if we don't provide a value for all slots
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template0", "policy", List.of(resourceLink))));
        ValidationResponse response4 = whenValidated();
        thenValidationFailed(response4);

        // validation returns an error if we provide a link with the wrong type
        LinkValue badLink1 = new LinkValue("?resource", EntityUID.parse("Library::User::\"Victor\"").get());
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template1", "policy", List.of(badLink1))));
        ValidationResponse response5 = whenValidated();
        thenIsNotValid(response5);

        // validation returns an error if we provide a link with an invalid type
        LinkValue badLink2 = new LinkValue("?resource", EntityUID.parse("Library::BOOK::\"The black Swan\"").get());
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template1", "policy", List.of(badLink2))));
        ValidationResponse response6 = whenValidated();
        thenIsNotValid(response6);
    }

    private void givenSchema(Schema testSchema) {
        this.schema = testSchema;
    }

    private void givenPolicy(String id, String policyText) {
        Policy policy = new Policy(policyText, id);
        this.policies = new PolicySet(Set.of(policy));
    }

    private ValidationResponse whenValidated() {
        ValidationRequest request = new ValidationRequest(schema, policies);
        return assertDoesNotThrow(() -> engine.validate(request));
    }

    private void thenIsValid(ValidationResponse response) {
        assertEquals(response.type, SuccessOrFailure.Success);
        final ValidationSuccessResponse success = assertDoesNotThrow(() -> response.success.get());
        assertTrue(
                success.validationErrors.isEmpty(),
                () -> {
                    String errors = response.success.get().validationErrors.stream()
                            .map(note -> String.format("in policy %s: %s", note.getPolicyId(), note.getError()))
                            .collect(Collectors.joining("\n"));
                    return "Expected valid response but got validation errors:\n" + errors;
                });
    }

    private void thenIsNotValid(ValidationResponse response) {
        assertEquals(response.type, SuccessOrFailure.Success);
        final ValidationSuccessResponse success = assertDoesNotThrow(() -> response.success.get());
        assertFalse(
                success.validationErrors.isEmpty(),
                () -> {
                    return "Expected validation errors but did not find any";
                });
    }

    private void thenValidationFailed(ValidationResponse response) {
        assertEquals(response.type, SuccessOrFailure.Failure);
        final List<DetailedError> errors = assertDoesNotThrow(() -> response.errors.get());
        assertFalse(errors.isEmpty());
    }

    @BeforeAll
    private static void setUp() {
        engine = new BasicAuthorizationEngine();
    }

    @BeforeEach
    private void reset() {
        this.schema = null;
        this.policies = new PolicySet();
    }

    private static final Schema EMPTY_SCHEMA = loadSchemaResource("/empty_schema.json");
    private static final Schema PHOTOFLASH_SCHEMA = loadSchemaResource("/photoflash_schema.json");
    private static final Schema LIBRARY_SCHEMA = loadSchemaResource("/library_schema.json");
}
