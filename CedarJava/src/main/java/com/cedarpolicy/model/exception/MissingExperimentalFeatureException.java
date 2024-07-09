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

package com.cedarpolicy.model.exception;

import com.cedarpolicy.ExperimentalFeature;

import java.nio.file.Path;

public class MissingExperimentalFeatureException extends InternalException {
    private ExperimentalFeature experimentalFeature;

    public MissingExperimentalFeatureException(ExperimentalFeature experimentalFeature) {
        super("Missing experimental feature. To enable this feature please recompile "
                + Path.of(System.getenv("CEDAR_JAVA_FFI_LIB")).getFileName()
                + " with \"--features=" + experimentalFeature.getCompileFlag() + "\".");
        this.experimentalFeature = experimentalFeature;
    }

    public ExperimentalFeature getExperimentalFeature() {
        return this.experimentalFeature;
    }
}
