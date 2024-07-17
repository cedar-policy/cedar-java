package com.cedarpolicy;

import com.cedarpolicy.formatter.PolicyFormatter;
import com.cedarpolicy.model.exception.InternalException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PolicyFormatterTests {

  @Test
  public void testPoliciesStrToPretty() throws Exception {
    String unformattedCedarPolicy = """
        permit(
                principal,
          action
          == Action::"update",
          resource
        ) when {resource.owner == principal};""";

    String formattedCedarPolicy = """
        permit (
          principal,
          action == Action::"update",
          resource
        )
        when { resource.owner == principal };""";

    assertEquals(formattedCedarPolicy, PolicyFormatter.policiesStrToPretty(unformattedCedarPolicy));
  }

  @Test
  public void testPoliciesStrToPrettyInvalidCedarPolicy() {
    String invalidCedarPolicy = """
        pppermit(
          principal == User::"alice",
          action    == Action::"update",
          resource  == Photo::"VacationPhoto94.jpg"
        );""";

    assertThrows(InternalException.class,
        () -> PolicyFormatter.policiesStrToPretty(invalidCedarPolicy));
  }

  @Test
  public void testPoliciesStrToPrettyNullSafety() {
    assertThrows(NullPointerException.class, () -> PolicyFormatter.policiesStrToPretty(null));
  }
}
