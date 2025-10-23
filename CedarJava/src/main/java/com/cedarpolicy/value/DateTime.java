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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Cedar datetime extension value. DateTime values are encoded as strings in the
 * following formats (Follows ISO 8601 standard):
 *
 * "YYYY-MM-DD" (date only)
 * "YYYY-MM-DDThh:mm:ssZ" (UTC)
 * "YYYY-MM-DDThh:mm:ss.SSSZ" (UTC with millisecond precision)
 * "YYYY-MM-DDThh:mm:ss(+/-)hhmm" (With timezone offset in hours and minutes)
 * "YYYY-MM-DDThh:mm:ss.SSS(+/-)hhmm" (With timezone offset in hours and minutes and millisecond precision)
 *
 */
public class DateTime extends Value {

    private static class DateTimeValidator {

        private static final Pattern OFFSET_PATTERN = Pattern.compile("([+-])(\\d{2})(\\d{2})$");

        // Formatters for UTC datetime
        private static final List<DateTimeFormatter> UTC_FORMATTERS = Arrays.asList(
                DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX")
                        .withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX")
                        .withResolverStyle(ResolverStyle.STRICT));

        // Formatters for local datetime parts (without offset)
        private static final List<DateTimeFormatter> LOCAL_FORMATTERS = Arrays.asList(
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS").withResolverStyle(ResolverStyle.STRICT));

        // Earliest valid instant: 0000-01-01T00:00:00+2359
        private static final Instant MIN_INSTANT = Instant.ofEpochMilli(-62167305540000L);
        // Latest valid instant: 9999-12-31T23:59:59-2359
        private static final Instant MAX_INSTANT = Instant.ofEpochMilli(253402387139000L);

        /**
         * Validates that the instant is within the allowed range.
         *
         * @param instant the parsed instant to validate
         * @return true if the instant is valid, false otherwise
         */
        private static boolean isValidInstant(Instant instant) {
            return !instant.isBefore(MIN_INSTANT) && !instant.isAfter(MAX_INSTANT);
        }

        /**
         * Parses a datetime string and returns the parsed Instant.
         *
         * @param dateTimeString the string to parse
         * @return Optional containing the parsed Instant, or empty if parsing fails
         */
        private static Optional<Instant> parseToInstant(String dateTimeString) {
            if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
                return Optional.empty();
            }

            Matcher offsetMatcher = OFFSET_PATTERN.matcher(dateTimeString);

            Optional<Instant> result;
            if (offsetMatcher.find()) {
                result = parseWithCustomOffset(dateTimeString, offsetMatcher);
            } else {
                result = UTC_FORMATTERS.stream()
                        .flatMap(formatter -> tryParseUTCDateTime(dateTimeString, formatter).stream())
                        .findFirst();
            }

            // Validate instant range
            if (result.isPresent() && !isValidInstant(result.get())) {
                return Optional.empty();
            }

            return result;
        }

        /**
         * Parses datetime string with custom offset handling for extreme values.
         *
         * @param dateTimeString the full datetime string
         * @param offsetMatcher the matcher that found the offset pattern
         * @return Optional containing the parsed Instant, or empty if parsing fails
         */
        private static Optional<Instant> parseWithCustomOffset(String dateTimeString, Matcher offsetMatcher) {
            try {
                String sign = offsetMatcher.group(1);
                int offsetHours = Integer.parseInt(offsetMatcher.group(2));
                int offsetMinutes = Integer.parseInt(offsetMatcher.group(3));

                if (offsetHours > 23 || offsetMinutes > 59) {
                    return Optional.empty();
                }

                String dateTimeWithoutOffset = dateTimeString.substring(0, offsetMatcher.start());

                Optional<LocalDateTime> localDateTime = LOCAL_FORMATTERS.stream()
                        .flatMap(formatter -> tryParseLocalDateTime(dateTimeWithoutOffset, formatter).stream())
                        .findFirst();

                if (localDateTime.isEmpty()) {
                    return Optional.empty();
                }

                long epochMillis = localDateTime.get().toInstant(ZoneOffset.UTC).toEpochMilli();
                long offsetMillis = convertOffsetToMilliseconds(sign, offsetHours, offsetMinutes);
                long adjustedEpochMillis = epochMillis - offsetMillis;

                return Optional.of(Instant.ofEpochMilli(adjustedEpochMillis));

            } catch (Exception e) {
                return Optional.empty();
            }
        }

        /**
         * Attempts to parse a local datetime string with a specific formatter.
         *
         * @param dateTimeString the string to parse
         * @param formatter the formatter to use
         * @return Optional containing the parsed LocalDateTime, or empty if parsing fails
         */
        private static Optional<LocalDateTime> tryParseLocalDateTime(String dateTimeString, DateTimeFormatter formatter) {
            try {
                return Optional.of(LocalDateTime.parse(dateTimeString, formatter));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }

        /**
         * Attempts to parse a UTC datetime string with a specific formatter.
         *
         * @param dateTimeString the string to parse
         * @param formatter the formatter to use
         * @return Optional containing the parsed Instant, or empty if parsing fails
         */
        private static Optional<Instant> tryParseUTCDateTime(String dateTimeString, DateTimeFormatter formatter) {
            try {
                if (formatter == UTC_FORMATTERS.get(0)) {
                    // Date-only format - convert to start of day UTC
                    LocalDate date = LocalDate.parse(dateTimeString, formatter);
                    return Optional.of(date.atStartOfDay(ZoneOffset.UTC).toInstant());
                } else {
                    // UTC format - only accept 'Z' as timezone, not other offsets
                    if (!dateTimeString.endsWith("Z")) {
                        return Optional.empty();
                    }
                    // DateTime format - parse and convert to Instant
                    OffsetDateTime dateTime = OffsetDateTime.parse(dateTimeString, formatter);
                    return Optional.of(dateTime.toInstant());
                }
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }

        /**
         * Converts timezone offset to milliseconds.
         *
         * @param sign the sign of the offset ("+" or "-")
         * @param hours the hours component of the offset
         * @param minutes the minutes component of the offset
         * @return offset in milliseconds
         */
        private static long convertOffsetToMilliseconds(String sign, int hours, int minutes) {
            long totalMinutes = hours * 60L + minutes;
            long milliseconds = totalMinutes * 60L * 1000L;
            return "+".equals(sign) ? milliseconds : -milliseconds;
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

    public long toEpochMilli() {
        return parsedInstant.toEpochMilli();
    }
}
