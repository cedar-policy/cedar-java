package com.cedarpolicy;

import com.cedarpolicy.formatter.PolicyFormatter;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.formatter.Config;
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
  public void testPoliciesStrToPrettyMalformedCedarPolicy() throws Exception {
    String malformedCedarPolicy = Files.readString(
        Path.of(TEST_RESOURCES_DIR + "malformed_policy_set.cedar"));

    assertThrows(InternalException.class,
        () -> PolicyFormatter.policiesStrToPretty(malformedCedarPolicy));
  }

  @Test
  public void testPoliciesStrToPrettyNullSafety() {
    assertThrows(NullPointerException.class, () -> PolicyFormatter.policiesStrToPretty(null));
  }

  @Test
  public void testPoliciesStrToPrettyWithConfigNullSafety() throws Exception {
    String cedarPolicy = Files.readString(Path.of(TEST_RESOURCES_DIR + "formatted_policy.cedar"));

    assertThrows(NullPointerException.class,
        () -> PolicyFormatter.policiesStrToPrettyWithConfig(null, null));

    assertThrows(NullPointerException.class,
        () -> PolicyFormatter.policiesStrToPrettyWithConfig(cedarPolicy, null));

    assertThrows(NullPointerException.class,
        () -> PolicyFormatter.policiesStrToPrettyWithConfig(null, new Config(120, 4)));
  }

  @Test
  public void testPoliciesStrToPrettyWithConfig() throws Exception {
    String unformattedCedarPolicy = Files.readString(
        Path.of(TEST_RESOURCES_DIR + "unformatted_policy.cedar"));

    String formattedCedarPolicyWithCustomConfig = Files.readString(
        Path.of(TEST_RESOURCES_DIR + "formatted_policy_custom_config.cedar"));

    // Random change
    assertEquals(formattedCedarPolicyWithCustomConfig,
        PolicyFormatter.policiesStrToPrettyWithConfig(unformattedCedarPolicy, new Config(120, 4)));
  }
}
