package com.cedarpolicy;

import com.cedarpolicy.formatter.PolicyFormatter;
import com.cedarpolicy.model.exception.InternalException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PolicyFormatterTests {

  private static final String TEST_RESOURCES_DIR = "src/test/resources/";

  @Test
  public void testPoliciesStrToPretty() throws Exception {
    String unformattedCedarPolicy = Files.readString(
        Path.of(TEST_RESOURCES_DIR + "unformatted_policy.cedar"));

    String formattedCedarPolicy = Files.readString(
        Path.of(TEST_RESOURCES_DIR + "formatted_policy.cedar"));

    assertEquals(formattedCedarPolicy, PolicyFormatter.policiesStrToPretty(unformattedCedarPolicy));
  }

  @Test
  public void testPoliciesStrToPrettyNullSafety() {
    assertThrows(NullPointerException.class, () -> PolicyFormatter.policiesStrToPretty(null));
  }
}
