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

package com.cedarpolicy;

import com.cedarpolicy.model.TypeAwarePartialAuthorizationRequest;
import com.cedarpolicy.model.exception.InternalException;
import com.cedarpolicy.model.schema.Schema;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PartialEntityUID;
import com.cedarpolicy.value.Unknown;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarpolicy.CedarJson.objectWriter;
import static com.cedarpolicy.TestUtil.assertJSONEqual;
import static com.cedarpolicy.TestUtil.assertMessageContains;
import static com.cedarpolicy.TestUtil.buildEuidObject;
import static com.cedarpolicy.TestUtil.buildUidObject;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link TypeAwarePartialAuthorizationRequest}, whose principal id, resource id, and context may be unknown. */
public class TypeAwarePartialAuthorizationRequestTests {
    /** See {@link PartialEntityTests} for what this schema is shaped to catch. */
    private static final Schema TPE_SCHEMA = TestUtil.loadSchemaResource("/tpe_schema.json");

    @Test
    public void testTypeAwarePartialRequest() throws InternalException {
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var door = new EntityUID(EntityTypeName.parse("Photo").get(), "door");
        var schemaJson = TPE_SCHEMA.schemaJson.orElseThrow();

        var unknownPrincipal = TypeAwarePartialAuthorizationRequest.builder()
            .principal(new PartialEntityUID(EntityTypeName.parse("User").get()))
            .action(view)
            .resource(door)
            .schema(TPE_SCHEMA)
            .build();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("principal", buildUidObject("User"));
        n.set("action", buildEuidObject("Action", "view"));
        n.set("resource", buildUidObject("Photo", "door"));
        n.set("schema", schemaJson);
        assertJSONEqual(n, unknownPrincipal);

        JsonNode serialized = assertDoesNotThrow(
                () -> CedarJson.objectMapper().readTree(objectWriter().writeValueAsString(unknownPrincipal)));
        assertFalse(serialized.get("principal").has("id"));
        assertFalse(serialized.has("context"));
        assertFalse(serialized.has("validateRequest"));

        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var knownPrincipal = TypeAwarePartialAuthorizationRequest.builder()
            .principal(alice)
            .action(view)
            .resource(door)
            .emptyContext()
            .schema(TPE_SCHEMA)
            .build();
        n = JsonNodeFactory.instance.objectNode();
        n.set("principal", buildUidObject("User", "alice"));
        n.set("action", buildEuidObject("Action", "view"));
        n.set("resource", buildUidObject("Photo", "door"));
        n.set("context", JsonNodeFactory.instance.objectNode());
        n.set("schema", schemaJson);
        assertJSONEqual(n, knownPrincipal);
    }

    @Test
    public void testTypeAwarePartialRequestWithUnknownResourceId() throws InternalException {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var schemaJson = TPE_SCHEMA.schemaJson.orElseThrow();

        // Cedar treats the principal and the resource symmetrically, so the resource id may be unknown on its own.
        var unknownResource = TypeAwarePartialAuthorizationRequest.builder()
            .principal(alice)
            .action(view)
            .resource(new PartialEntityUID(EntityTypeName.parse("Photo").get()))
            .emptyContext()
            .schema(TPE_SCHEMA)
            .build();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.set("principal", buildUidObject("User", "alice"));
        n.set("action", buildEuidObject("Action", "view"));
        n.set("resource", buildUidObject("Photo"));
        n.set("context", JsonNodeFactory.instance.objectNode());
        n.set("schema", schemaJson);
        assertJSONEqual(n, unknownResource);

        JsonNode serialized = assertDoesNotThrow(
                () -> CedarJson.objectMapper().readTree(objectWriter().writeValueAsString(unknownResource)));
        assertFalse(serialized.get("resource").has("id"));

        // Both ids unknown at once is also valid.
        var bothUnknown = TypeAwarePartialAuthorizationRequest.builder()
            .principal(new PartialEntityUID(EntityTypeName.parse("User").get()))
            .action(view)
            .resource(new PartialEntityUID(EntityTypeName.parse("Photo").get()))
            .emptyContext()
            .schema(TPE_SCHEMA)
            .build();
        n = JsonNodeFactory.instance.objectNode();
        n.set("principal", buildUidObject("User"));
        n.set("action", buildEuidObject("Action", "view"));
        n.set("resource", buildUidObject("Photo"));
        n.set("context", JsonNodeFactory.instance.objectNode());
        n.set("schema", schemaJson);
        assertJSONEqual(n, bothUnknown);
    }

    @Test
    public void testTypeAwarePartialRequestTypeChecksAgainstSchema() {
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var door = new EntityUID(EntityTypeName.parse("Photo").get(), "door");
        var wrongPrincipalType = TypeAwarePartialAuthorizationRequest.builder()
            .principal(new PartialEntityUID(EntityTypeName.parse("Photo").get()))
            .action(view)
            .resource(door)
            .schema(TPE_SCHEMA);
        assertMessageContains(assertThrows(InternalException.class, wrongPrincipalType::build),
                "principal type `Photo`", "is not valid for", "Action::\"view\"");

        // The resource type is checked the same way the principal type is.
        var wrongResourceType = TypeAwarePartialAuthorizationRequest.builder()
            .principal(new PartialEntityUID(EntityTypeName.parse("User").get()))
            .action(view)
            .resource(new PartialEntityUID(EntityTypeName.parse("User").get()))
            .schema(TPE_SCHEMA);
        assertMessageContains(assertThrows(InternalException.class, wrongResourceType::build),
                "resource type `User`", "is not valid for", "Action::\"view\"");
    }

    @Test
    public void testTypeAwarePartialRequestRejectsUnknownInContext() {
        var alice = new EntityUID(EntityTypeName.parse("User").get(), "alice");
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var door = new EntityUID(EntityTypeName.parse("Photo").get(), "door");
        // The context is all-or-nothing here: a per-key Unknown, which is the partial evaluation idiom, is rejected.
        Map<String, Value> context = Map.of("authenticated", new Unknown("AuthenticatedIsUnknown"));
        var builder = TypeAwarePartialAuthorizationRequest.builder()
            .principal(alice)
            .action(view)
            .resource(door)
            .context(context)
            .schema(TPE_SCHEMA);
        assertMessageContains(assertThrows(InternalException.class, builder::build),
                "Context contains unknowns");
    }

    @Test
    public void testTypeAwarePartialRequestRequiresSchema() {
        var view = new EntityUID(EntityTypeName.parse("Action").get(), "view");
        var door = new EntityUID(EntityTypeName.parse("Photo").get(), "door");
        var builder = TypeAwarePartialAuthorizationRequest.builder()
            .principal(new PartialEntityUID(EntityTypeName.parse("User").get()))
            .action(view)
            .resource(door);
        NullPointerException e = assertThrows(NullPointerException.class, () -> builder.build());
        assertTrue(e.getMessage().contains("schema is required"));
    }
}
