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
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a Cedar fixed-point decimal extension value. Decimals are encoded as strings in
 * dot-decimal notation with 4 decimals after the dot (e.g., <code>"1.0000"</code>).
 */
public class Decimal extends Value {

    private static class DecimalValidator {
        private static final Pattern DECIMAL_PATTERN =
                Pattern.compile("^-?[0-9]+\\.[0-9]{1,4}$");
        private static final BigDecimal RANGE_MIN = new BigDecimal("-922337203685477.5808");
        private static final BigDecimal RANGE_MAX = new BigDecimal("922337203685477.5807");

        public static boolean validDecimal(String d) {
            if (d == null || d.isEmpty()) {
                return false;
            }
            if (!DECIMAL_PATTERN.matcher(d).matches()) {
                return false;
            }

            BigDecimal val = new BigDecimal(d);
            return val.compareTo(RANGE_MIN) >= 0 && val.compareTo(RANGE_MAX) <= 0;
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
