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

package cedarpolicy.value;

import java.util.Objects;

/** Represents the primitive Cedar integer type. */
public final class PrimLong extends Value {
    /** Value. */
    public final Long value;

    /**
     * Build PrimLong.
     *
     * @param i Long.
     */
    public PrimLong(Long i) throws NullPointerException {
        if (i == null) {
            throw new NullPointerException("Attempt to create PrimLong from null");
        }
        value = i;
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
        PrimLong primLong = (PrimLong) o;
        return value.equals(primLong.value);
    }

    /** hash. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** toString. */
    @Override
    public String toString() {
        return value.toString();
    }

    /** To Cedar expr. */
    @Override
    String toCedarExpr() {
        return value.toString();
    }
}
