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

package com.cedarpolicy.pbt;

import com.cedarpolicy.value.PrimString;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Arbitraries;

/** Utils for generating arbitrary primitives for testing. */
public final class Utils {
    private static final Set<String> CEDAR_RESERVED =
            new HashSet<>(
                    Arrays.asList(
                            "true",
                            "false",
                            "permit",
                            "forbid",
                            "where",
                            "when",
                            "unless",
                            "advice",
                            "in",
                            "has",
                            "if",
                            "then",
                            "else",
                            "for",
                            "let",
                            "def",
                            "principal",
                            "action",
                            "resource",
                            "context"));

    /** Arbitrary strings. */
    public static String strings() {
        return Arbitraries.strings()
                .numeric()
                .alpha()
                .ofMinLength(2)
                .filter(s -> Character.isLowerCase(s.charAt(0)) && !CEDAR_RESERVED.contains(s))
                .sample();
    }

    /** Arbitrary string as PrimString. */
    public static PrimString primStrings() {
        return new PrimString(strings());
    }

    /** Random int in range [min, max]. */
    public static int intInRange(int min, int max) {
        return Arbitraries.integers().between(min, max).sample();
    }

    private Utils() {
        throw new IllegalStateException("Utility class");
    }
}
