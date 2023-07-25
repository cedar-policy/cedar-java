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

import java.util.Objects;

/** Represents a primitive Cedar string value. */
public final class PrimString extends Value {
    /** Value. */
    public final String value;

    /**
     * Build PrimString.
     *
     * @param s String.
     */
    public PrimString(String s) {
        value = s;
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
        PrimString that = (PrimString) o;
        return value.equals(that.value);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** ToString. */
    @Override
    public String toString() {
        return value;
    }

    /** To Cedar expr that can be used in a Cedar policy. */
    @Override
    public String toCedarExpr() {
        return "\"" + value + "\"";
    }
}
