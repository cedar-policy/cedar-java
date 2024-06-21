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

package com.cedarpolicy.loader;

import com.fizzed.jne.JNE;

/**
 * Native Library Loader encapsulates runtime loading of the Cedar Java FFI library
 */
public final class LibraryLoader {
    private static final String LIBRARY_PATH_VARIABLE_NAME = "CEDAR_JAVA_FFI_LIB";

    private static final String LIBRARY_NAME = "cedar_java_ffi";

    /**
     * Private constructor to prevent instantiation of this utility class
     */
    private LibraryLoader() {
    }

    /**
     * Load Cedar Java FFI library based on runtime operating system and architecture of the Java Virtual Machine
     */
    public static void loadLibrary() {
        final String libraryPath = System.getenv(LIBRARY_PATH_VARIABLE_NAME);
        if (libraryPath == null || libraryPath.isEmpty()) {
            JNE.loadLibrary(LIBRARY_NAME);
        } else {
            System.load(libraryPath);
        }
    }
}
