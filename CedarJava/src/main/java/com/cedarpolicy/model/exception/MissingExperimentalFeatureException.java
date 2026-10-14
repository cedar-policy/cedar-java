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
                + nativeLibraryName()
                + " with \"--features=" + experimentalFeature.getCompileFlag() + "\".");
        this.experimentalFeature = experimentalFeature;
    }

    /**
     * The library to name in the message. {@code CEDAR_JAVA_FFI_LIB} is only an override, so it is unset in the normal
     * bundled-library case — reading it unguarded threw a {@link NullPointerException} from inside this constructor,
     * which discarded the very error being reported.
     */
    private static String nativeLibraryName() {
        String override = System.getenv("CEDAR_JAVA_FFI_LIB");
        if (override == null || override.isEmpty()) {
            return "the CedarJava native library";
        }
        // getFileName() is null for a path with no name element, such as a bare root.
        Path name = Path.of(override).getFileName();
        return name == null ? override : name.toString();
    }

    public ExperimentalFeature getExperimentalFeature() {
        return this.experimentalFeature;
    }
}
