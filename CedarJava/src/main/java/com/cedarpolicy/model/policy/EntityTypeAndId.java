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

package com.cedarpolicy.model.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Entity type and ID. */
public class EntityTypeAndId {
    private final String ty; //We use `ty` to match the JSON format expected by the Rust code
    private final String eid;

    /**
     * Construct Entity type and ID.
     *
     * @param ty Type string.
     * @param eid EID string.
     */
    @JsonCreator
    public EntityTypeAndId(@JsonProperty("ty") String ty, @JsonProperty("eid") String eid) {
        this.ty = ty;
        this.eid = eid;
    }

    /** Get entity type. */
    public String getTy() {
        return ty;
    }

    /** Get entity ID. */
    public String getEid() {
        return eid;
    }
}
