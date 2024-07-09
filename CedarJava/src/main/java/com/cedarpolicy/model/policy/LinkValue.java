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
import com.cedarpolicy.value.EntityUID;


/** Link for policy template. */
public class LinkValue {
    private final String slot;
    private final EntityUID value;

    /**
     * Link for policy template.
     *
     * @param slot  the slot in the template.
     * @param value the value to put in the slot
     */
    public LinkValue(String slot, EntityUID value) {
        this.slot = slot;
        this.value = value;
    }

    /** Get the slot in the template. */
    public String getSlot() {
        return slot;
    }

    /** Get the value to put in the slot. */
    public EntityUID getValue() {
        return value;
    }
}
