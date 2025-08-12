/*
 * Copyright Cedar Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.cedarpolicy.value;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Cedar datetime extension value. DateTime values are encoded as strings in the
 * following formats (Follows ISO 8601 standard):
 *
 *   "YYYY-MM-DD" (date only)
 *   "YYYY-MM-DDThh:mm:ssZ" (UTC)
 *   "YYYY-MM-DDThh:mm:ss.SSSZ" (UTC with millisecond precision)
 *   "YYYY-MM-DDThh:mm:ss(+/-)hhmm" (With timezone offset in hours and minutes)
 *   "YYYY-MM-DDThh:mm:ss.SSS(+/-)hhmm" (With timezone offset in hours and minutes and millisecond precision)
 *
 */
public class DateTime extends Value {

    private static class DateTimeValidator {

        private static final List<DateTimeFormatter> FORMATTERS =
                Arrays.asList(DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXX").withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXX").withResolverStyle(ResolverStyle.STRICT));

        /**
         * Validates a datetime string against the supported formats. Automatically enforces range
         * constraints: - Month: 01-12 - Day: 01-31 (considering month-specific limits) - Hour:
         * 00-23 - Minute: 00-59 - Second: 00-59
         *
         * @param dateTimeString the string to validate
         * @return true if valid, false otherwise
         */
        public static boolean isValid(String dateTimeString) {
            if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
                return false;
            }

            return FORMATTERS.stream()
                    .anyMatch(formatter -> canParse(formatter, dateTimeString));
        }

        private static boolean canParse(DateTimeFormatter formatter, String dateTimeString) {
            try {
                formatter.parse(dateTimeString);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
    }

    /** Datetime as a string. */
    private final String dateTime;

    /**
     * Construct DateTime.
     *
     * @param dateTime DateTime as a String.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public DateTime(String dateTime) throws NullPointerException, IllegalArgumentException {
        if (!DateTimeValidator.isValid(dateTime)) {
            throw new IllegalArgumentException(
                    "Input string is not a valid DateTime format: " + dateTime);
        }
        this.dateTime = dateTime;
    }

    /** Convert DateTime to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "datetime(\"" + dateTime + "\")";
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
        DateTime dateTime1 = (DateTime) o;
        return dateTime.equals(dateTime1.dateTime);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(dateTime);
    }

    /** As a string. */
    @Override
    public String toString() {
        return dateTime;
    }
}
