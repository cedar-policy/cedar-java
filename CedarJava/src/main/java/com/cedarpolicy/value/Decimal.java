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

package com.cedarpolicy.value;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Cedar fixed-point decimal extension value. Decimals are encoded as strings in
 * dot-decimal notation with 4 decimals after the dot (e.g., <code>"1.0000"</code>).
 */
public class Decimal extends Value {

    private static class DecimalValidator {
        private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?([0-9])*(\\.)([0-9]{0,4})$");
        private static final String MAX_POSITIVE = "9223372036854775807";
        private static final String MAX_NEGATIVE = "9223372036854775808";

        public static boolean validDecimal(String d) {
            if (d == null || d.isEmpty()) {
                return false;
            }
            d = d.trim();
            Matcher matcher = DECIMAL_PATTERN.matcher(d);
            if (!matcher.matches()) {
                return false;
            }

            // Validate range: [-922337203685477.5808, 922337203685477.5807]
            // This corresponds to the i64 range when the value is multiplied by 10000.
            boolean negative = d.startsWith("-");
            String withoutSign = negative ? d.substring(1) : d;

            int dotIndex = withoutSign.indexOf('.');
            String intPart = withoutSign.substring(0, dotIndex);
            String fracPart = withoutSign.substring(dotIndex + 1);

            // Strip leading zeros from integer part
            intPart = intPart.replaceFirst("^0+", "");
            if (intPart.isEmpty()) {
                intPart = "0";
            }

            // Pad fractional part to exactly 4 digits
            fracPart = fracPart + "0000".substring(fracPart.length());

            // Combine integer and fractional parts as the i64 representation (value * 10000)
            String combined = intPart.equals("0") ? fracPart.replaceFirst("^0+", "") : intPart + fracPart;
            if (combined.isEmpty()) {
                combined = "0";
            }

            String limit = negative ? MAX_NEGATIVE : MAX_POSITIVE;
            if (combined.length() != limit.length()) {
                return combined.length() < limit.length();
            }
            return combined.compareTo(limit) <= 0;
        }
    }

    /** decimal as a string. */
    private final String decimal;

    /**
     * Construct Decimal.
     *
     * @param decimal Decimal as a String.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Decimal(String decimal) throws NullPointerException, IllegalArgumentException {
        if (!DecimalValidator.validDecimal(decimal)) {
            throw new IllegalArgumentException(
                    "Input string is not a valid decimal. E.g., \"1.0000\") \n " + decimal);
        }
        this.decimal = decimal;
    }

    /** Convert Decimal to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
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
