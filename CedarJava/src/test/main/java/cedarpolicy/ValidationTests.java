package cedarpolicy;

import static cedarpolicy.TestUtil.loadSchemaResource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cedarpolicy.model.ValidationQuery;
import cedarpolicy.model.ValidationResult;
import cedarpolicy.model.exception.AuthException;
import cedarpolicy.model.exception.BadRequestException;
import cedarpolicy.model.schema.Schema;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the validator. */
public class ValidationTests {
    private Schema schema;
    private HashMap<String, String> policies;

    private static AuthorizationEngine engine;

    /** Test. */
    @Test
    public void givenEmptySchemaAndNoPolicyReturnsValid() {
        givenSchema(EMPTY_SCHEMA);
        ValidationResult result = whenValidated();
        thenIsValid(result);
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
        ValidationResult result = whenValidated();
        thenIsValid(result);
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
        ValidationResult result = whenValidated();
        thenIsNotValid(result);
    }

    /** Test. */
    @Test
    public void givenInvalidPolicyThrowsBadRequestError() {
        givenSchema(EMPTY_SCHEMA);
        givenPolicy("policy0", "permit { }");
        AuthException result = whenValidatingThrows();
        thenTheErrorIsABadRequest(result);
    }

    private void givenSchema(Schema schema) {
        this.schema = schema;
    }

    private void givenPolicy(String id, String policy) {
        this.policies.put(id, policy);
    }

    private ValidationResult whenValidated() {
        ValidationQuery query = new ValidationQuery(schema, policies);
        return assertDoesNotThrow(() -> engine.validate(query));
    }

    private void thenIsValid(ValidationResult result) {
        assertTrue(
                result.getNotes().isEmpty(),
                () -> {
                    String notes =
                            result.getNotes().stream()
                                    .map(
                                            note ->
                                                    String.format(
                                                            "in policy %s: %s",
                                                            note.getPolicyId(), note.getNote()))
                                    .collect(Collectors.joining("\n"));
                    return "Expected valid result but got an invalid one with notes:\n" + notes;
                });
    }

    private void thenIsNotValid(ValidationResult result) {
        assertFalse(result.getNotes().isEmpty());
    }

    private AuthException whenValidatingThrows() {
        ValidationQuery query = new ValidationQuery(schema, policies);
        try {
            engine.validate(query);
        } catch (AuthException e) {
            return e;
        }
        return fail("The validation succeeded, but expected it to throw.");
    }

    private void thenTheErrorIsABadRequest(AuthException e) {
        assertTrue(e instanceof BadRequestException);
    }

    @BeforeAll
    private static void setUp() {
        engine = new WrapperAuthorizationEngine();
    }

    @BeforeEach
    private void reset() {
        this.schema = null;
        this.policies = new HashMap<>();
    }

    private static final Schema EMPTY_SCHEMA = loadSchemaResource("/empty_schema.json");
    private static final Schema PHOTOFLASH_SCHEMA = loadSchemaResource("/photoflash_schema.json");

    private static <T> List<T> listOf(T... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
