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
import com.cedarpolicy.model.LevelValidationRequest;
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
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
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
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
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
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsNotValid(levelResponse);
    }

    /** Test. */
    @Test
    public void givenInvalidPolicyThrowsBadRequestError() {
        givenSchema(EMPTY_SCHEMA);
        givenPolicy("policy0", "permit { }");
        ValidationResponse response = whenValidated();
        thenValidationFailed(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenValidationFailed(levelResponse);
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
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
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
        ValidationResponse response1 = whenValidated();
        thenValidationFailed(response1);
        ValidationResponse levelResponse1 = whenLevelValidated(1);
        thenValidationFailed(levelResponse1);

        // fails if we provide a value for too many slots
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template2", "policy", List.of(principalLink, resourceLink))));
        ValidationResponse response2 = whenValidated();
        thenValidationFailed(response2);
        ValidationResponse levelResponse2 = whenLevelValidated(1);
        thenValidationFailed(levelResponse2);

        // fails if we don't provide a value for all slots
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template0", "policy", List.of(resourceLink))));
        ValidationResponse response3 = whenValidated();
        thenValidationFailed(response3);
        ValidationResponse levelResponse3 = whenLevelValidated(1);
        thenValidationFailed(levelResponse3);


        // validation returns an error if we provide a link with the wrong type
        LinkValue badLink1 = new LinkValue("?resource", EntityUID.parse("Library::User::\"Victor\"").get());
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template1", "policy", List.of(badLink1))));
        ValidationResponse response4 = whenValidated();
        thenIsNotValid(response4);
        ValidationResponse levelResponse4 = whenLevelValidated(1);
        thenIsNotValid(levelResponse4);

        // validation returns an error if we provide a link with an invalid type
        LinkValue badLink2 = new LinkValue("?resource", EntityUID.parse("Library::BOOK::\"The black Swan\"").get());
        this.policies = new PolicySet(new HashSet<>(), templates,
                List.of(new TemplateLink("template1", "policy", List.of(badLink2))));
        ValidationResponse response5 = whenValidated();
        thenIsNotValid(response5);
        ValidationResponse levelResponse5 = whenLevelValidated(1);
        thenIsNotValid(levelResponse5);
    }

     @Test
    public void validateLevelPolicySuccessTest() {
        givenSchema(LEVEL_SCHEMA);
        givenPolicy(
        "policy0",
        "permit(\n"
                + "    principal in UserGroup::\"alice_friends\",\n"
                + "    action == Action::\"viewPhoto\",\n"
                + "    resource\n"
                + ") when {principal in resource.owner.friend};");

        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(2);
        thenIsValid(levelResponse);
    }

    @Test
    public void validateLevelPolicyFailsWhenExpected() {
        givenSchema(LEVEL_SCHEMA);
        givenPolicy(
        "policy0",
        "permit(\n"
                + "    principal in UserGroup::\"alice_friends\",\n"
                + "    action == Action::\"viewPhoto\",\n"
                + "    resource\n"
                + ") when {principal in resource.owner.friend};");

        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsNotValid(levelResponse);
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

    private ValidationResponse whenLevelValidated(long maxDerefLevel) {
        LevelValidationRequest request = new LevelValidationRequest(schema, policies, maxDerefLevel);
        return assertDoesNotThrow(() -> engine.validateWithLevel(request));
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
    private static final Schema LEVEL_SCHEMA = loadSchemaResource("/level_schema.json");
    private static final Schema ENUM_SCHEMA = loadSchemaResource("/enum_schema.json");

    /** Test enum entity validation with valid enum values. */
    @Test
    public void givenEnumSchemaAndValidEnumUsageReturnsValid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal == User::\"alice\","
                        + "    action == Action::\"UpdateTask\","
                        + "    resource == Task::\"task1\""
                        + ") when {"
                        + "    resource.status == Color::\"Red\""
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
    }

    /** Test enum entity validation with invalid enum values. */
    @Test
    public void givenEnumSchemaAndInvalidEnumValueReturnsInvalid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal == User::\"alice\","
                        + "    action == Action::\"UpdateTask\","
                        + "    resource == Task::\"task1\""
                        + ") when {"
                        + "    resource.status != Color::\"Purple\""  // Invalid enum value
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsNotValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsNotValid(levelResponse);
    }

    /** Test enum entity validation with case-sensitive enum values. */
    @Test
    public void givenEnumSchemaAndWrongCaseEnumValueReturnsInvalid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal == User::\"alice\","
                        + "    action == Action::\"UpdateTask\","
                        + "    resource == Task::\"task1\""
                        + ") when {"
                        + "    resource.status != Color::\"red\""  // Wrong case - should be "Red"
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsNotValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsNotValid(levelResponse);
    }

    /** Test RFC example policy with enum entities. */
    @Test
    public void givenEnumSchemaAndRFCExamplePolicyReturnsValid() {
        givenSchema(ENUM_SCHEMA);
        // This is the exact policy from the RFC
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal,"
                        + "    action == Action::\"UpdateTask\","
                        + "    resource"
                        + ") when {"
                        + "    principal == resource.owner &&"
                        + "    resource.status != Color::\"Red\""
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
    }

    /** Test Application enum from RFC example. */
    @Test
    public void givenEnumSchemaAndApplicationEnumPolicyReturnsValid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal,"
                        + "    action == Action::\"CreateList\","
                        + "    resource == Application::\"TinyTodo\""
                        + ");");
        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
    }

    /** Test invalid Application enum usage. */
    @Test
    public void givenEnumSchemaAndInvalidApplicationEnumReturnsInvalid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal,"
                        + "    action == Action::\"CreateList\","
                        + "    resource == Application::\"TinyTODO\""  // Typo in enum value
                        + ");");
        ValidationResponse response = whenValidated();
        thenIsNotValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsNotValid(levelResponse);
    }

    /** Test multiple enum comparisons in policy. */
    @Test
    public void givenEnumSchemaAndMultipleEnumComparisonsReturnsValid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "permit("
                        + "    principal,"
                        + "    action == Action::\"UpdateTask\","
                        + "    resource"
                        + ") when {"
                        + "    resource.status == Color::\"Blue\" ||"
                        + "    resource.status == Color::\"Green\""
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
    }

    /** Test forbid policy with enum entities. */
    @Test
    public void givenEnumSchemaAndForbidPolicyWithEnumsReturnsValid() {
        givenSchema(ENUM_SCHEMA);
        givenPolicy(
                "policy0",
                "forbid("
                        + "    principal,"
                        + "    action == Action::\"UpdateTask\","
                        + "    resource"
                        + ") when {"
                        + "    resource.status == Color::\"Red\" &&"
                        + "    principal != resource.owner"
                        + "};");
        ValidationResponse response = whenValidated();
        thenIsValid(response);
        ValidationResponse levelResponse = whenLevelValidated(1);
        thenIsValid(levelResponse);
    }
}
