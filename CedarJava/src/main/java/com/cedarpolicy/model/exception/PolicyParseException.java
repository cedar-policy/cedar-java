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

import com.cedarpolicy.CedarJson;
import com.cedarpolicy.model.DetailedError;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when Cedar policy text fails to parse, carrying the structured diagnostics Cedar
 * produced for each error.
 *
 * <p>Cedar reports parse failures as {@code miette} diagnostics: a message, the source span
 * of the offending token, the tokens the parser expected there, and often help text. Prior
 * to this type those were flattened to a single {@code Display} string, so callers saw
 * "unexpected token `::`" with no indication of where in the policy it occurred, and every
 * error after the first was discarded. {@link #getDetailedErrors()} returns the full set,
 * one {@link DetailedError} per parse error, in the order Cedar reported them.
 *
 * <p>Extends {@link InternalException} so existing {@code catch} blocks are unaffected, and
 * {@link #getMessage()} is byte-for-byte what the generic error path produced: callers are
 * known to branch on that string and to match it with anchored regexes, so it is treated as
 * part of the API and left alone. The new detail is reached through the accessors instead.
 *
 * <p>{@link #getErrors()} does change: it now carries one entry per parse error rather than
 * a single entry for the whole document, which is what its plural contract always implied,
 * and each entry is the bare Cedar message without the {@code "Internal JNI Error: "}
 * prefix, which described the binding rather than any one error.
 */
public class PolicyParseException extends InternalException {

    private static final TypeReference<List<DetailedError>> ERROR_LIST =
            new TypeReference<List<DetailedError>>() {};

    private final transient List<DetailedError> detailedErrors;

    /**
     * Construct from the JSON array of {@code DetailedError} the native layer serialises.
     *
     * @param message the message, which the native layer builds exactly as the generic
     *     error path does so that {@link #getMessage()} is unchanged
     * @param messages one message per parse error, for {@link #getErrors()}
     * @param detailedErrorsJson JSON array of {@code DetailedError}; if it cannot be read,
     *     the exception still carries {@code message} and {@code messages}, and
     *     {@link #getDetailedErrors()} returns empty, so a serialisation change can never
     *     turn a parse error into a different failure
     */
    public PolicyParseException(String message, String[] messages, String detailedErrorsJson) {
        super(message, Arrays.asList(messages));
        this.detailedErrors = readDetailedErrors(detailedErrorsJson);
    }

    private static List<DetailedError> readDetailedErrors(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            List<DetailedError> parsed = CedarJson.objectReader().forType(ERROR_LIST).readValue(json);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * The structured diagnostics for each parse error, including source spans and help text.
     *
     * @return the diagnostics, or an empty list if none could be recovered
     */
    public List<DetailedError> getDetailedErrors() {
        return Collections.unmodifiableList(detailedErrors);
    }
}
