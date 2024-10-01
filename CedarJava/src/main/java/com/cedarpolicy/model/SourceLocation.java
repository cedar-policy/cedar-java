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

package com.cedarpolicy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A range of source code denoted by offset and length
 */
public final class SourceLocation {
    public final int start;
    public final int end;


    @JsonCreator
    public SourceLocation(@JsonProperty("start") int start, @JsonProperty("end") int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(final Object lhs) {
        if (!(lhs instanceof SourceLocation)) {
            return false;
        }

        final SourceLocation sl = (SourceLocation) lhs;
        return this.start == sl.start && this.end == sl.end;
    }

    @Override
    public int hashCode() {
        return start ^ end;
    }

}
