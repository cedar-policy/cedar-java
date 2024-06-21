/*
 * Copyright 2022-2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
import java.util.regex.PatternSyntaxException;

/**
 * Represents a Cedar fixed-point decimal extension value. Decimals are encoded as strings in
 * dot-decimal notation with 4 decimals after the dot (e.g., <code>"1.0000"</code>).
 */
public class Decimal extends Value {

    private static class DecimalValidator {
        private static final Pattern DECIMAL_PATTERN = Pattern.compile("^([0-9])*(\\.)([0-9]{0,4})$");

        public static boolean validDecimal(String d) {
            if (d == null || d.isEmpty()) {
                return false;
            }
            d = d.trim();
            if (d.length() > 21) {
                return false; // 19digits, decimal point and - sign
            }
            try {
                Matcher matcher = DECIMAL_PATTERN.matcher(d);
                return matcher.matches();
            } catch (PatternSyntaxException ex) {
                return false;
            }
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
