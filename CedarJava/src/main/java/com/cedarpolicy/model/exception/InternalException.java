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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** An exception which is thrown when Cedar encounters an internal error when processing a request. */
public class InternalException extends AuthException {
    private final List<String> errors;

    public InternalException(String error) {
        super("Internal error: " + error);
        this.errors = new ArrayList<>();
        this.errors.add(error);
    }

    /**
     * Internal exception from Rust library.
     *
     * @param errors List of Errors.
     */
    public InternalException(String[] errors) {
        super("Internal error: " + String.join("\n", errors));
        this.errors = new ArrayList<>(Arrays.asList(errors));
    }

    /**
     * Internal exception whose message is not derived from its error list.
     *
     * <p>The other constructors build the message by joining {@code errors}, which ties the
     * two together: splitting the list more finely necessarily changes the message. Subclasses
     * that report each underlying error separately, but summarise them differently in the
     * message, use this constructor to set the two independently.
     *
     * @param error the message, prefixed as in {@link #InternalException(String)}
     * @param errors the individual error messages, for {@link #getErrors()}
     */
    protected InternalException(String error, List<String> errors) {
        super("Internal error: " + error);
        this.errors = new ArrayList<>(errors);
    }

    /**
     * Get errors.
     *
     * @return the error messages returned by Cedar
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
