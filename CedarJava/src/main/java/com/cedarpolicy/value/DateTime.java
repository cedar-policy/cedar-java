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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Cedar datetime extension value. DateTime values are encoded as strings in the
 * following formats (Follows ISO 8601 standard):
 *
 * "YYYY-MM-DD" (date only) "YYYY-MM-DDThh:mm:ssZ" (UTC) "YYYY-MM-DDThh:mm:ss.SSSZ" (UTC with
 * millisecond precision) "YYYY-MM-DDThh:mm:ss(+/-)hhmm" (With timezone offset in hours and minutes)
 * "YYYY-MM-DDThh:mm:ss.SSS(+/-)hhmm" (With timezone offset in hours and minutes and millisecond
 * precision)
 *
 */
public class DateTime extends Value {

    private static class DateTimeValidator {

        private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
                DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
                        .withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXX")
                        .withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXX")
                        .withResolverStyle(ResolverStyle.STRICT));

        /**
         * Parses a datetime string and returns the parsed Instant. Combines validation and parsing
         * into a single operation to avoid redundancy. All datetime formats are normalized to
         * Instant for consistent equality comparison.
         *
         * @param dateTimeString the string to parse
         * @return Optional containing the parsed Instant, or empty if parsing fails
         */
        private static Optional<Instant> parseToInstant(String dateTimeString) {
            if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
                return java.util.Optional.empty();
            }

            return FORMATTERS.stream()
                    .flatMap(formatter -> tryParseWithFormatter(dateTimeString, formatter).stream())
                    .findFirst();
        }

        /**
         * Attempts to parse a datetime string with a specific formatter.
         *
         * @param dateTimeString the string to parse
         * @param formatter the formatter to use
         * @return Optional containing the parsed Instant, or empty if parsing fails
         */
        private static Optional<Instant> tryParseWithFormatter(String dateTimeString,
                DateTimeFormatter formatter) {
            try {
                if (formatter == FORMATTERS.get(0)) {
                    // Date-only format - convert to start of day UTC
                    LocalDate date = LocalDate.parse(dateTimeString, formatter);
                    return Optional.of(date.atStartOfDay(ZoneOffset.UTC).toInstant());
                } else {
                    // DateTime format - parse and convert to Instant
                    OffsetDateTime dateTime = OffsetDateTime.parse(dateTimeString, formatter);
                    return Optional.of(dateTime.toInstant());
                }
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
    }

    /** Datetime as a string. */
    private final String dateTime;

    /** Parsed datetime as Instant for semantic comparison. */
    private final Instant parsedInstant;

    /**
     * Construct DateTime.
     *
     * @param dateTime DateTime as a String.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public DateTime(String dateTime) throws NullPointerException, IllegalArgumentException {
        Optional<Instant> parsed = DateTimeValidator.parseToInstant(dateTime);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Input string is not a supported DateTime format: " + dateTime);
        } else {
            this.dateTime = dateTime;
            this.parsedInstant = parsed.get();
        }
    }

    /** Convert DateTime to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "datetime(\"" + dateTime + "\")";
    }

    /**
     * Equals based on semantic comparison of the parsed datetime values. Two DateTime objects are
     * equal if they represent the same instant in time, regardless of their string representation
     * format.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DateTime other = (DateTime) o;
        return Objects.equals(this.parsedInstant, other.parsedInstant);
    }

    /**
     * Hash based on the parsed datetime value for semantic equality.
     */
    @Override
    public int hashCode() {
        return Objects.hash(parsedInstant);
    }

    /** As a string. */
    @Override
    public String toString() {
        return dateTime;
    }
}
