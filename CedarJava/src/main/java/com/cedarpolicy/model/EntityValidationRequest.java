 /*
  * Copyright 2022-2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

 import java.util.List;
 import java.util.Objects;

 import com.cedarpolicy.model.schema.Schema;
 import com.cedarpolicy.model.slice.Entity;
 import com.fasterxml.jackson.annotation.JsonProperty;

 /**
  * Information passed to Cedar for entities validation.
  */
 public final class EntityValidationRequest {
     @JsonProperty("schema")
     private final Schema schema;
     @JsonProperty("entities")
     private final List<Entity> entities;

     /**
      * Construct a validation request.
      *
      * @param schema   Schema for the request
      * @param entities Map.
      */
     public EntityValidationRequest(Schema schema, List<Entity> entities) {
         if (schema == null) {
             throw new NullPointerException("schema");
         }

         if (entities == null) {
             throw new NullPointerException("entities");
         }

         this.schema = schema;
         this.entities = entities;
     }

     /**
      * Test equality.
      */
     @Override
     public boolean equals(final Object o) {
         if (!(o instanceof EntityValidationRequest)) {
             return false;
         }

         final EntityValidationRequest other = (EntityValidationRequest) o;
         return schema.equals(other.schema) && entities.equals(other.entities);
     }

     /**
      * Hash.
      */
     @Override
     public int hashCode() {
         return Objects.hash(schema, entities);
     }

     /**
      * Get readable string representation.
      */
     public String toString() {
         return "EntityValidationRequest(schema=" + schema + ", entities=" + entities + ")";
     }
 }
