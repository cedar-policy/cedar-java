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

package com.cedarpolicy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;

public class DetailedError {
    /** Main error message */
    @JsonProperty("message")
    public final String message;
    /** Help message, providing additional information about the error or help resolving it */
    @JsonProperty("help")
    public final Optional<String> help;
    /** Error code */
    @JsonProperty("code")
    public final Optional<String> code;
    /** URL for more information about the error */
    @JsonProperty("url")
    public final Optional<String> url;
    /** Severity */
    @JsonProperty("severity")
    public final Optional<Severity> severity;
    /** Source labels (ranges); see {@link SourceLabel} for how to index them */
    @JsonProperty("sourceLocations")
    public final ImmutableList<SourceLabel> sourceLocations;
    /** Related errors */
    @JsonProperty("related")
    public final ImmutableList<DetailedError> related;

    @JsonCreator
    public DetailedError(
        @JsonProperty("message") String message,
        @JsonProperty("help") Optional<String> help,
        @JsonProperty("code") Optional<String> code,
        @JsonProperty("url") Optional<String> url,
        @JsonProperty("severity") Optional<Severity> severity,
        @JsonProperty("sourceLocations") Optional<List<SourceLabel>> sourceLocations,
        @JsonProperty("related") Optional<List<DetailedError>> related
    ) {
        this.message = message;
        this.help = help;
        this.code = code;
        this.url = url;
        this.severity = severity;
        if (sourceLocations.isPresent()) {
            this.sourceLocations = ImmutableList.copyOf(sourceLocations.get());
        } else {
            this.sourceLocations = ImmutableList.of(); // empty
        }
        if (related.isPresent()) {
            this.related = ImmutableList.copyOf(related.get());
        } else {
            this.related = ImmutableList.of(); // empty
        }
    }

    public enum Severity {
        /** Advice (the lowest severity) */
        @JsonProperty("advice")
        Advice,
        /** Warning */
        @JsonProperty("warning")
        Warning,
        /** Error (the highest severity) */
        @JsonProperty("error")
        Error,
    }

    /**
     * A region of the source text an error refers to, so callers can underline it.
     *
     * <p><b>The offsets are UTF-8 byte offsets, not {@code String} indices.</b> Cedar produces
     * them by counting bytes, while {@link String#substring(int, int)} counts UTF-16 chars. The
     * two coincide only while the source is pure ASCII; a single non-ASCII character anywhere
     * earlier in the document - an accented identifier, a non-Latin string literal, an emoji in
     * a comment - shifts them apart, and slicing the {@code String} directly then either
     * extracts the wrong region or throws {@link StringIndexOutOfBoundsException}. Slice the
     * source's UTF-8 bytes instead:
     *
     * <pre>{@code
     * byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
     * String offending = new String(bytes, label.start, label.end - label.start, StandardCharsets.UTF_8);
     * }</pre>
     *
     * <p>Offsets are absolute within the whole text that was parsed, not relative to the policy
     * containing the error, so they remain directly usable when several policies are parsed
     * together. They carry no policy identity of their own: mapping an offset back to a
     * particular policy statement is left to the caller.
     *
     * <p>A region may be empty ({@code start == end}), which happens when there is no extent to
     * highlight - an unterminated string literal, for instance, reports the position the lexer
     * stopped at. Renderers should treat zero width as a single caret rather than assuming at
     * least one character to underline.
     *
     * <p>For a parse error the region covers the unexpected token, which is not always where a
     * reader would place the mistake: a missing operand is reported at the token that followed
     * it, possibly on a later line.
     */
    public static final class SourceLabel {
        /** Text of the label (if any) */
        @JsonProperty("label")
        public final Optional<String> label;
        /** Start of the source location, as a UTF-8 byte offset, inclusive. See {@link SourceLabel}. */
        @JsonProperty("start")
        public final int start;
        /** End of the source location, as a UTF-8 byte offset, exclusive. See {@link SourceLabel}. */
        @JsonProperty("end")
        public final int end;

        @JsonCreator
        public SourceLabel(
            @JsonProperty("label") Optional<String> label,
            @JsonProperty("start") int start,
            @JsonProperty("end") int end
        ) {
            this.label = label;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("SourceLabel{label=\"%s\", start=%s, end=%s}", label.orElse(""), start, end);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "DetailedError{message=\"%s\", help=\"%s\", code=\"%s\", url=\"%s\", severity=%s, sourcelocations=%s, related=%s}",
                message, help.orElse(""), code.orElse(""), url.orElse(""), severity.map(Severity::toString).orElse(""),
                sourceLocations, related);
    }
}
