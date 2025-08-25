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
 * Represents a Cedar Duration extension.
 *
 * Duration values represent time spans and are encoded as strings combining multiple time units.
 * They are useful for time-based policy decisions such as session timeouts, expiration periods,
 * or time window calculations.
 *
 * <strong>Format:</strong> Duration strings follow the pattern {@code "XdYhZmAsLms"} where:
 * <ul>
 *   <li>{@code d} - days</li>
 *   <li>{@code h} - hours</li>
 *   <li>{@code m} - minutes (not followed by 's')</li>
 *   <li>{@code s} - seconds</li>
 *   <li>{@code ms} - milliseconds</li>
 * </ul>
 *
 * <strong>Rules:</strong>
 * <ul>
 *   <li>Units must appear in order from largest to smallest (days → hours → minutes → seconds → milliseconds)</li>
 *   <li>Each unit can appear at most once</li>
 *   <li>Units with zero quantity may be omitted</li>
 *   <li>Each quantity must be a non-negative integer</li>
 *   <li>The entire duration can be negative by prefixing with a minus sign</li>
 * </ul>
 *
 * <strong>Examples:</strong>
 * <ul>
 *   <li>{@code "1h"} - one hour</li>
 *   <li>{@code "-10h"} - negative ten hours</li>
 *   <li>{@code "5d3ms"} - five days and three milliseconds</li>
 *   <li>{@code "3h5m"} - three hours and five minutes</li>
 *   <li>{@code "1d2h3m4s5ms"} - one day, two hours, three minutes, four seconds, and five milliseconds</li>
 * </ul>
 *
 * Duration objects are immutable and thread-safe. Two Duration instances are considered equal
 * if they represent the same time span, regardless of their string representation format.
 * For example, {@code "60s"} and {@code "1m"} represent the same duration.
 *
 */
public class Duration extends Value implements Comparable<Duration> {

    private static class DurationValidator {

        private static final Pattern DURATION_PATTERN =
                Pattern.compile("^(-?)(?:(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:(\\d+)s)?(?:(\\d+)ms)?)$");

        private static final long DAYS_TO_MS = 86_400_000L;
        private static final long HOURS_TO_MS = 3_600_000L;
        private static final long MINUTES_TO_MS = 60_000L;
        private static final long SECONDS_TO_MS = 1_000L;
        private static final long MILLISECONDS_TO_MS = 1L;

        /**
         * Parses a duration string and returns the total milliseconds. Combines validation and parsing into
         * a single operation to avoid redundancy. All duration formats are normalized to milliseconds for
         * consistent equality comparison.
         *
         * @param durationString the string to parse
         * @return the parsed total milliseconds
         * @throws IllegalArgumentException if the format is invalid
         * @throws ArithmeticException if the value would cause overflow
         */
        private static long parseToMilliseconds(String durationString)
                throws IllegalArgumentException, ArithmeticException {
            if (durationString == null || durationString.trim().isEmpty()) {
                throw new IllegalArgumentException("Duration string cannot be null or empty");
            }

            Matcher matcher = DURATION_PATTERN.matcher(durationString.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid duration format");
            }

            // Extract the optional negative sign (group 1)
            String signStr = matcher.group(1);
            boolean isNegative = "-".equals(signStr);

            long totalMs = 0;
            boolean hasAnyComponent = false;

            // Extract days (group 2)
            String daysStr = matcher.group(2);
            if (daysStr != null && !daysStr.isEmpty()) {
                long days = Long.parseLong(daysStr);
                totalMs = Math.addExact(totalMs, Math.multiplyExact(days, DAYS_TO_MS));
                hasAnyComponent = true;
            }

            // Extract hours (group 3)
            String hoursStr = matcher.group(3);
            if (hoursStr != null && !hoursStr.isEmpty()) {
                long hours = Long.parseLong(hoursStr);
                totalMs = Math.addExact(totalMs, Math.multiplyExact(hours, HOURS_TO_MS));
                hasAnyComponent = true;
            }

            // Extract minutes (group 4)
            String minutesStr = matcher.group(4);
            if (minutesStr != null && !minutesStr.isEmpty()) {
                long minutes = Long.parseLong(minutesStr);
                totalMs = Math.addExact(totalMs, Math.multiplyExact(minutes, MINUTES_TO_MS));
                hasAnyComponent = true;
            }

            // Extract seconds (group 5)
            String secondsStr = matcher.group(5);
            if (secondsStr != null && !secondsStr.isEmpty()) {
                long seconds = Long.parseLong(secondsStr);
                totalMs = Math.addExact(totalMs, Math.multiplyExact(seconds, SECONDS_TO_MS));
                hasAnyComponent = true;
            }

            // Extract milliseconds (group 6)
            String millisecondsStr = matcher.group(6);
            if (millisecondsStr != null && !millisecondsStr.isEmpty()) {
                long milliseconds = Long.parseLong(millisecondsStr);
                totalMs = Math.addExact(totalMs, Math.multiplyExact(milliseconds, MILLISECONDS_TO_MS));
                hasAnyComponent = true;
            }

            // Must have at least one component
            if (!hasAnyComponent) {
                throw new IllegalArgumentException("Invalid duration format");
            }

            // Apply negative sign to the total if present
            if (isNegative) {
                totalMs = Math.negateExact(totalMs);
            }

            return totalMs;
        }
    }

    /** Duration as a string. */
    private final String durationString;

    /** Parsed duration as total milliseconds for semantic comparison. */
    private final long totalMilliseconds;

    /**
     * Construct Duration.
     *
     * @param duration Duration as a String.
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Duration(String duration) throws NullPointerException, IllegalArgumentException {
        if (duration == null) {
            throw new NullPointerException("Duration string cannot be null");
        }

        try {
            this.totalMilliseconds = DurationValidator.parseToMilliseconds(duration);
            this.durationString = duration;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Duration value is too large and would cause overflow: " + duration, e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Input string is not a supported Duration format: " + duration, e);
        }
    }

    /** Convert Duration to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "duration(\"" + durationString + "\")";
    }

    /**
     * Equals based on semantic comparison of the parsed duration values. Two Duration objects are equal
     * if they represent the same time span, regardless of their string representation format.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Duration other = (Duration) o;
        return this.totalMilliseconds == other.totalMilliseconds;
    }

    /**
     * Hash based on the parsed duration value for semantic equality.
     */
    @Override
    public int hashCode() {
        return Objects.hash(totalMilliseconds);
    }

    /** As a string. */
    @Override
    public String toString() {
        return durationString;
    }

    /**
     * Compares this Duration with another Duration based on their total milliseconds. Returns a
     * negative integer, zero, or a positive integer as this Duration is less than, equal to, or greater
     * than the specified Duration.
     *
     * @param other the Duration to compare with
     * @return a negative integer, zero, or a positive integer as this Duration is less than, equal to,
     *         or greater than the specified Duration
     * @throws NullPointerException if the specified Duration is null
     */
    @Override
    public int compareTo(Duration other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare with null Duration");
        }
        return Long.compare(this.totalMilliseconds, other.totalMilliseconds);
    }

}
