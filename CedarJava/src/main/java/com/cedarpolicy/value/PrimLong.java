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

import java.util.Objects;

/** Represents the primitive Cedar integer type. */
public final class PrimLong extends Value {
    /** Value. */
    private final long value;

    /**
     * Build PrimLong.
     *
     * @param i Long.
     */
    public PrimLong(long i) {
        value = i;
    }

    /** Get the PrimLong as a long. */
    public long getValue() {
        return value;
    }

    /** equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return this.value == ((PrimLong) o).value;
    }

    /** hash. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** toString. */
    @Override
    public String toString() {
        return String.valueOf(value);
    }

    /** To Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return this.toString();
    }
}
