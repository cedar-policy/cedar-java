package cedarpolicy.value;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a Cedar fixed-point decimal extension value. Decimals are encoded as strings in
 * dot-decimal notation with 4 decimals after the dot (e.g., <code>"1.0000"</code>).
 */
public class Decimal extends Value {

    private static class DecimalValidator {
        private static final Pattern decimalPattern = Pattern.compile("^([0-9])*(\\.)([0-9]{0,4})$");

        public static boolean validDecimal(String d) {
            if (d == null || d.isEmpty()) return false;
            d = d.trim();
            if (d.length() > 21) return false; // 19digits, decimal point and - sign
            try {
                Matcher matcher = decimalPattern.matcher(d);
                return matcher.matches();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }
    }

    /** decimal as a string. */
    public final String decimal;

    /**
     * Construct Decimal.
     *
     * @param decimal Decimal as a String.
     */
    public Decimal(String decimal) throws NullPointerException, IllegalArgumentException {
        if (!DecimalValidator.validDecimal(decimal)) {
            throw new IllegalArgumentException(
                    "Input string is not a valid decimal. E.g., \"1.0000\") \n " + decimal);
        }
        this.decimal = decimal;
    }

    /** Convert Decimal to cedar expr. */
    @Override
    String toCedarExpr() {
        return "decimal(\"" + decimal + "\")";
    }

    /** Equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Decimal decimal1 = (Decimal) o;
        return decimal.equals(decimal1.decimal);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(decimal);
    }

    /** As a string. */
    @Override
    public String toString() {
        return decimal;
    }
}
