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

package com.cedarpolicy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/** Result of a validation request. */
public final class ValidationResponse {
    private final List<Note> notes;

    /**
     * Construct a validation response.
     *
     * @param notes Notes.
     */
    @JsonCreator
    @SuppressFBWarnings
    public ValidationResponse(@JsonProperty("notes") List<Note> notes) {
        if (notes == null) {
            throw new NullPointerException("notes");
        }

        this.notes = notes;
    }

    /**
     * Get notes from a validation response.
     *
     * @return The notes.
     */
    @SuppressFBWarnings
    public List<Note> getNotes() {
        return this.notes;
    }

    /** Test equals. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ValidationResponse)) {
            return false;
        } else {
            return notes.equals(((ValidationResponse) o).notes);
        }
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(notes);
    }

    /** Readable string representation. */
    public String toString() {
        return "ValidationResponse(notes=" + this.getNotes() + ")";
    }

    /** Note for a specific policy. */
    public static final class Note {
        private final String policyId;
        private final String note;

        /**
         * Create note from JSON.
         *
         * @param policyId Policy id to which note applies.
         * @param note The Note.
         */
        @JsonCreator
        public Note(@JsonProperty("policyId") String policyId, @JsonProperty("note") String note) {
            this.policyId = policyId;
            this.note = note;
        }

        /**
         * Get the policy id.
         *
         * @return The policy id.
         */
        public String getPolicyId() {
            return this.policyId;
        }

        /**
         * Get the note.
         *
         * @return The note.
         */
        public String getNote() {
            return this.note;
        }

        /** Equals. */
        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Note)) {
                return false;
            }

            final Note other = (Note) o;
            return policyId.equals(other.policyId) && note.equals(other.note);
        }

        /** Hash. */
        @Override
        public int hashCode() {
            return Objects.hash(policyId, note);
        }

        /** Readable string representation. */
        public String toString() {
            return "Note(policyId=" + policyId + ", note=" + note + ")";
        }
    }
}
