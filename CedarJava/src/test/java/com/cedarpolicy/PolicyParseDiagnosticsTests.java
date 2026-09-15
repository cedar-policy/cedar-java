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

import com.cedarpolicy.model.DetailedError;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.exception.PolicyParseException;
import com.cedarpolicy.model.policy.PolicySet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parse failures carry Cedar's structured diagnostics, not just a flattened string. */
public class PolicyParseDiagnosticsTests {

    @Test
    public void parseFailureCarriesSourceSpanAndExpectedTokens() {
        // An entity literal in the action slot: the scope needs `action == ...`.
        String src = "forbid(principal, Foo::Action::\"Read\", resource);";
        PolicyParseException e =
                assertThrows(PolicyParseException.class, () -> PolicySet.parsePolicies(src));

        List<DetailedError> details = e.getDetailedErrors();
        assertEquals(1, details.size());
        DetailedError error = details.get(0);
        assertTrue(error.message.contains("unexpected token `::`"), error.message);

        assertEquals(1, error.sourceLocations.size());
        DetailedError.SourceLabel span = error.sourceLocations.get(0);
        // The span must cover the offending `::`, so callers can underline it.
        assertEquals(src.indexOf("::"), span.start);
        assertEquals(src.indexOf("::") + 2, span.end);
        assertTrue(span.label.orElse("").contains("expected"), span.label.toString());
    }

    @Test
    public void everyParseErrorIsReportedNotJustTheFirst() {
        // ParseErrors' Display prints only the first error, so the flattened path reported
        // one string for the whole document. Both accessors now carry all of them.
        String src = "forbid(principal, Foo::Action::\"A\", resource);\n"
                + "permit(principal, action, resource) when { 1 + };";
        PolicyParseException e =
                assertThrows(PolicyParseException.class, () -> PolicySet.parsePolicies(src));

        assertEquals(2, e.getDetailedErrors().size());
        assertEquals(2, e.getErrors().size());
        // The message stays what Display gave it - the first error alone - so populating the
        // list cannot widen the string that existing callers match on.
        assertEquals("Internal error: Internal JNI Error: " + e.getErrors().get(0), e.getMessage());
    }

    @Test
    public void messageIsUnchangedForBackCompat() {
        // Callers branch on getMessage() and match it with anchored regexes, so the string
        // stays exactly as the generic error path wrote it, "Internal JNI Error: " and all.
        // The added detail is reached through the accessors instead.
        PolicyParseException e = assertThrows(PolicyParseException.class,
                () -> PolicySet.parsePolicies("forbid(principal, Foo::Action::\"Read\", resource);"));

        assertEquals("Internal error: Internal JNI Error: unexpected token `::`", e.getMessage());
        // getErrors() entries are the bare Cedar messages: the prefix described the binding
        // rather than any one error, and is meaningless once the list is per-error.
        assertEquals(List.of("unexpected token `::`"), e.getErrors());
    }

    @Test
    public void helpTextSurvivesWhenCedarSuppliesIt() {
        PolicyParseException e = assertThrows(PolicyParseException.class,
                () -> PolicySet.parsePolicies("permit(principle, action, resource);"));

        DetailedError error = e.getDetailedErrors().get(0);
        assertTrue(error.help.isPresent(), "expected help text for an invalid scope variable");
        assertTrue(error.help.get().contains("principal"), error.help.get());
    }

    @Test
    public void remainsCatchableAsInternalException() {
        // PolicyParseException extends InternalException so existing callers keep working.
        InternalException e = assertThrows(InternalException.class,
                () -> PolicySet.parsePolicies("permit(principal, action, resource)"));
        assertFalse(e.getErrors().isEmpty());
    }

    @Test
    public void validPolicySetStillParses() {
        assertDoesNotThrow(() -> PolicySet.parsePolicies("permit(principal, action, resource);"));
    }
}
