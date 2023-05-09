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

/** Represents a primitive Cedar boolean value. */
public final class PrimBool extends Value {
    /** value. */
    public final Boolean value;

    /**
     * Build PrimBool.
     *
     * @param b Boolean.
     */
    public PrimBool(Boolean b) throws NullPointerException {
        if (b == null) {
            throw new NullPointerException("Attempt to create PrimBool from null");
        }
        value = b;
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
        PrimBool primBool = (PrimBool) o;
        return value.equals(primBool.value);
    }

    /** Hash. */
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
