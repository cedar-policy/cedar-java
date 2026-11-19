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

import static com.fizzed.jne.OperatingSystem.LINUX;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fizzed.jne.ABI;
import com.fizzed.jne.HardwareArchitecture;
import com.fizzed.jne.NativeTarget;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The compiled Linux libraries must be discoverable using JNE's resource paths. */
public class NativeLibraryPackagingTests {

    @ParameterizedTest
    @CsvSource({"X64,MUSL", "ARM64,MUSL", "X64,GNU", "ARM64,GNU"})
    public void linuxNativeLibraryIsPackaged(HardwareArchitecture architecture, ABI abi) {
        List<String> candidates = NativeTarget.of(LINUX, architecture, abi)
                .resolveResourcePaths("jne", "libcedar_java_ffi.so");
        Path compiledResources = Path.of("build", "resources", "compiled");
        assertTrue(candidates.stream().map(compiledResources::resolve).anyMatch(Files::isRegularFile),
                "Missing compiled native library for " + architecture + "/" + abi + ": " + candidates);
    }
}
