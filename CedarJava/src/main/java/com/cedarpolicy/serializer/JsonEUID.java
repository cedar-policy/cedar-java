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

package com.cedarpolicy.serializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Represent JSON format of Entity Unique Identifier. */
@JsonDeserialize
public class JsonEUID {
    /** euid (__entity is used as escape sequence in JSON). */
    @JsonProperty("type")
    public final String type;

    @JsonProperty("id")
    public final String id;


    /** Readable string representation. */
    public String toString() {
        return type+"::"+id;
    }

    /**
     * Build JsonEUID.
     *
     * @param type Entity Type.
     * @param id   Entity ID.
     */
    public JsonEUID(String type, String id) {
        this.type = type; this.id = id;
    }

    /** Build JsonEUID (default constructor needed by Jackson). */
    public JsonEUID() {
        this.type = ""; this.id = "";
    }
}
