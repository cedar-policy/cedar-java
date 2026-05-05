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

import java.lang.ref.Cleaner;

/**
 * Internal shared state for the Cedar library.
 *
 * <p><b>This class is not part of the public API.</b> It is public only because
 * it must be accessible from sub-packages (e.g., {@code com.cedarpolicy.model}).
 * Do not use this class directly.
 */
public final class SharedCedarInternals {
    private static final Cleaner CLEANER = Cleaner.create();

    private SharedCedarInternals() {
    }

    /** Register an object for GC-based cleanup of native resources. */
    public static Cleaner.Cleanable registerCleanup(Object referent, Runnable action) {
        return CLEANER.register(referent, action);
    }
}
