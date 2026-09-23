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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when Cedar policy text fails to parse, carrying the structured diagnostics Cedar
 * produced for each error.
 *
 * <p>Cedar reports a parse failure as one or more {@code miette} diagnostics: a message, the
 * source span of the offending token, the tokens the parser expected there, and often help
 * text. A single document may fail in several places, and every failure is reported.
 *
 * <p>Three accessors describe the same failure at increasing fidelity:
 *
 * <ul>
 *   <li>{@link #getMessage()} - one human-readable line, describing the first error only. Its
 *       wording is treated as part of this class's compatibility surface, so it is the least
 *       informative of the three and the safest to match on.
 *   <li>{@link #getErrors()} - one message per parse error, in the order Cedar reported them.
 *       These are Cedar's messages alone, without the prefix {@link #getMessage()} carries.
 *   <li>{@link #getDetailedErrors()} - the full diagnostic for each error, and the only
 *       accessor that reports where in the source the error occurred. See {@link DetailedError}.
 * </ul>
 *
 * <p>Extends {@link InternalException}, so callers that catch the general parse-or-evaluate
 * failure are unaffected and need not know this type exists.
 */
public final class PolicyParseException extends InternalException {

    private static final TypeReference<List<DetailedError>> ERROR_LIST =
            new TypeReference<List<DetailedError>>() { };

    private final transient List<DetailedError> detailedErrors;

    /**
     * Construct from the JSON array of {@code DetailedError} the native layer serialises.
     *
     * @param message the value {@link #getMessage()} reports
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
        } catch (JsonProcessingException | RuntimeException e) {
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
