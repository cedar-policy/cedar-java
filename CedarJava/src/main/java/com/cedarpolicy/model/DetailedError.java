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
    /** Source labels (ranges) */
    @JsonProperty("sourceLocations")
    public final List<SourceLabel> sourceLocations;
    /** Related errors */
    @JsonProperty("related")
    public final List<DetailedError> related;

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
            this.sourceLocations = List.copyOf(sourceLocations.get());
        } else {
            this.sourceLocations = List.of(); // empty
        }
        if (related.isPresent()) {
            this.related = List.copyOf(related.get());
        } else {
            this.related = List.of(); // empty
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

    public static final class SourceLabel {
        /** Text of the label (if any) */
        @JsonProperty("label")
        public final Optional<String> label;
        /** Start of the source location (in bytes) */
        @JsonProperty("start")
        public final int start;
        /** End of the source location (in bytes) */
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
