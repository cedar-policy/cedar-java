package cedarpolicy.value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a Cedar Entity UID. An entity UID contains both the entity type and a unique
 * identifier for the entity formatted as <code>TYPE::"ID"</code>.
 */
public class EntityUID extends Value {

    private static class EUIDValidator {
        // Any char except {'\','"'} or escaped slash `\\` or escaped quote `\"` or unicode `\u0000`
        // We need to escape twice, once for Java and once for the Regex
        private static String anyCharExceptSlashOrQuote = "[^\\\"\\\\]";
        private static String escapedSlash = "\\\\\\\\";
        private static String escapedQuote = "\\\\\\\"";
        private static String unicodeEscapedPattern = "\\\\u[A-Fa-f0-9]{4,6}";
        private static Pattern entityUIDPattern =
                Pattern.compile(
                        "^([A-Za-z_]([A-Za-z0-9_])*::)+\"("
                                + anyCharExceptSlashOrQuote
                                + "|"
                                + escapedSlash
                                + "|"
                                + escapedQuote
                                + "|"
                                + unicodeEscapedPattern
                                + ")*\"$");
        private static final long MAXLENGTH = 1024;

        public static boolean validEntityUID(String id) {
            if (id == null || id.isEmpty()) return false;
            id = id.trim();
            if (id.length() > MAXLENGTH) return false;
            try {
                Matcher matcher = entityUIDPattern.matcher(id);
                return matcher.matches();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }
    }

    /** Entity uid. */
    public final String euid;

    /**
     * Build EntityUID.
     *
     * @param euid Entity Unique ID as a string.
     *     <p>Note, we limit euids to 1024 chars.
     */
    public EntityUID(String euid) throws IllegalArgumentException {
        if (!EUIDValidator.validEntityUID(euid)) {
            throw new IllegalArgumentException("Input string is not a valid EntityUID " + euid);
        }

        this.euid = euid;
    }

    /** As String. */
    @Override
    public String toString() {
        return euid;
    }

    /** Write as Cedar expr. */
    @Override
    String toCedarExpr() {
        return euid;
    }
}
