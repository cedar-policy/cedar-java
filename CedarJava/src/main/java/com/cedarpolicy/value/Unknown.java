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

import com.cedarpolicy.Experimental;
import com.cedarpolicy.ExperimentalFeature;


/**
 * Represents a Cedar unknown extension value.
 * This class can only be used with partial evaluation.
 */
@Experimental(ExperimentalFeature.PARTIAL_EVALUATION)
public class Unknown extends Value {


    /**
     * arg as a string.
     */
    private final String arg;

    /**
     * Construct Unknown.
     *
     * @param arg for the unknown extension
     */
    public Unknown(String arg) throws NullPointerException, IllegalArgumentException {
        this.arg = arg;
    }

    /**
     * Convert Decimal to Cedar expr that can be used in a Cedar policy.
     */
    @Override
    public String toCedarExpr() {
        return "Unknown(\"" + arg + "\")";
    }

    /**
     * Equals.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Unknown unknown = (Unknown) o;
        return arg.equals(unknown.arg);
    }

    /**
     * Hash.
     */
    @Override
    public int hashCode() {
        return Objects.hash(arg);
    }

    /**
     * As a string.
     */
    @Override
    public String toString() {
        return arg;
    }
}
