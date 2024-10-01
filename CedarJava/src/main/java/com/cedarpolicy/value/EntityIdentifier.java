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

package com.cedarpolicy.value;


import com.cedarpolicy.loader.LibraryLoader;

/**
 * Class representing Entity Identifiers.
 * All strings are valid Entity Identifiers
 */
public final class EntityIdentifier {
    private String id;

    static {
        LibraryLoader.loadLibrary();
    }

    /**
     * Construct an Entity Identifier
     * @param id String containing the Identifier
     */
    public EntityIdentifier(String id) {
        this.id = id;
    }

    /**
     * Calls the Rust core and returns the quoted representation of this Entity Identifier
     * @return String containing the quoted representation of this Entity Identifier
     */
    public String getRepr() {
        return getEntityIdentifierRepr(this);
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return true;
        } else if (o == this) {
            return false;
        } else {
            try {
                EntityIdentifier rhs = (EntityIdentifier) o;
                return this.id.equals(rhs.id);
            } catch (ClassCastException e) {
                return false;
            }
        }
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    protected String getId() {
        return id;
    }


    private static native String getEntityIdentifierRepr(EntityIdentifier id);

}
