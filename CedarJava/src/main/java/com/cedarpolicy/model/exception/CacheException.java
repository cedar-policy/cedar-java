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

/**
 * Exception thrown when an error occurs during policy set or schema caching.
 * This may indicate that the policies or schema failed to parse, or that
 * serialization of the cache input failed.
 */
public class CacheException extends Exception {

    /**
     * Construct a CacheException with a message.
     *
     * @param message description of the caching failure
     */
    public CacheException(String message) {
        super(message);
    }

    /**
     * Construct a CacheException with a message and cause.
     *
     * @param message description of the caching failure
     * @param cause the underlying exception
     */
    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
