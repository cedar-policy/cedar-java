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

package com.cedarpolicy;

import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.exception.MissingExperimentalFeatureException;

public enum ExperimentalFeature {
    /** Partial evaluation feature */
    PARTIAL_EVALUATION("partial-eval", "AuthorizationPartialOperation"),
    /** Type-aware partial evaluation feature */
    TYPE_AWARE_PARTIAL_EVALUATION("tpe", "TypeAwarePartialEvaluationNotEnabled");

    private String compileFlag;
    private String disabledToken;

    ExperimentalFeature(String compileFlag, String disabledToken) {
        this.compileFlag = compileFlag;
        this.disabledToken = disabledToken;
    }

    public String getCompileFlag() {
        return this.compileFlag;
    }

    /**
     * Translate the error a native call raises when the library was built without this feature into the exception the
     * rest of the library uses to report a missing experimental feature. Every feature is detected by matching a token
     * in the native error message, but the two features supply that token differently: a partial evaluation operation is
     * compiled out of the native dispatch table, so the native library echoes the operation name back in its
     * unsupported-operation error, whereas the type-aware partial evaluation entry points are compiled in either way and
     * their disabled bodies return a token chosen for this purpose.
     *
     * @param e The exception the native call raised.
     * @return A {@link MissingExperimentalFeatureException} if this feature is off, otherwise {@code e} unchanged.
     */
    public InternalException translateIfDisabled(InternalException e) {
        if (e.getMessage() != null && e.getMessage().contains(this.disabledToken)) {
            return new MissingExperimentalFeatureException(this);
        }
        return e;
    }
}
