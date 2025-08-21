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

package com.cedarpolicy.value.functions;

import lombok.Getter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.cedarpolicy.value.DateTime;
import com.cedarpolicy.value.Duration;
import com.cedarpolicy.value.Value;

/**
 * Represents a Cedar datetime offset operation that combines a DateTime with a Duration.
 *
 * The Offset class represents the Cedar expression {@code datetime.offset(duration)}, which applies
 * a duration offset to a datetime value. This is useful for temporal calculations in Cedar
 * policies, such as scheduling, time windows, or deadline computations.
 *
 * For example:
 * <ul>
 * <li>{@code datetime("2023-01-01T12:00:00Z").offset(duration("2h"))} - adds 2 hours to the
 * datetime</li>
 * <li>{@code datetime("2023-01-01T12:00:00Z").offset(duration("-30m"))} - subtracts 30 minutes from
 * the datetime</li>
 * </ul>
 *
 * @see DateTime
 * @see Duration
 * @see Value
 */
@Getter
public class Offset extends Value {
    private final Duration offsetDuration;
    private final DateTime dateTime;

    /**
     * Constructs an Offset with the specified datetime and duration.
     *
     * <p>
     * This represents the Cedar expression {@code dateTime.offset(offsetDuration)}. The offset duration
     * can be positive (future) or negative (past).
     *
     * @param dateTime the base datetime to offset from, must not be null
     * @param offsetDuration the duration to offset by, must not be null
     * @throws NullPointerException if dateTime or offsetDuration is null
     * @throws IllegalArgumentException if the datetime or duration values are invalid
     */
    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public Offset(DateTime dateTime, Duration offsetDuration) throws NullPointerException, IllegalArgumentException {
        if (dateTime == null) {
            throw new NullPointerException("dateTime cannot be null");
        }
        if (offsetDuration == null) {
            throw new NullPointerException("offsetDuration cannot be null");
        }
        this.dateTime = dateTime;
        this.offsetDuration = offsetDuration;
    }

    /** Convert Offset to Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return String.format("%s.offset(%s)", this.dateTime.toCedarExpr(), this.offsetDuration.toCedarExpr());
    }

    /** As a string. */
    @Override
    public String toString() {
        return this.toCedarExpr();
    }
}
