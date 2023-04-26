package cedarpolicy.pbt;

import cedarpolicy.value.PrimString;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Arbitraries;

/** Utils for generating arbitrary primitives for testing. */
public final class Utils {
    private static final Set<String> CEDAR_RESERVED =
            new HashSet<>(
                    Arrays.asList(
                            "true",
                            "false",
                            "permit",
                            "forbid",
                            "where",
                            "when",
                            "unless",
                            "advice",
                            "in",
                            "has",
                            "if",
                            "then",
                            "else",
                            "for",
                            "let",
                            "def",
                            "principal",
                            "action",
                            "resource",
                            "context"));

    /** Arbitrary strings. */
    public static String strings() {
        return Arbitraries.strings()
                .numeric()
                .alpha()
                .ofMinLength(2)
                .filter(s -> Character.isLowerCase(s.charAt(0)) && !CEDAR_RESERVED.contains(s))
                .sample();
    }

    /** Arbitrary string as PrimString. */
    public static PrimString primStrings() {
        return new PrimString(strings());
    }

    /** Random int in range [min, max]. */
    public static int intInRange(int min, int max) {
        return Arbitraries.integers().between(min, max).sample();
    }

    private Utils() {
        throw new IllegalStateException("Utility class");
    }
}
