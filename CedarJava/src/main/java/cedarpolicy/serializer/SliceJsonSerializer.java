package cedarpolicy.serializer;

import cedarpolicy.model.slice.Entity;
import cedarpolicy.model.slice.Slice;
import cedarpolicy.value.Value;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Serialize a slice. */
public class SliceJsonSerializer extends JsonSerializer<Slice> {

    /** Serialize a slice. */
    @Override
    public void serialize(
            Slice slice, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("policies", slice.getPolicies());
        jsonGenerator.writeObjectField(
                "entities", convertEntitiesToJsonEntities(slice.getEntities()));
        jsonGenerator.writeObjectField("templates", slice.getTemplates());
        jsonGenerator.writeObjectField(
                "template_instantiations", slice.getTemplateInstantiations());
        jsonGenerator.writeEndObject();
    }

    private static class JsonEntity {
        /** Entity uid for the entity. */
        @SuppressFBWarnings public final JsonEUID uid;

        /** Entity attributes, where the value string is a Cedar literal value. */
        @SuppressFBWarnings public final Map<String, Value> attrs;

        /** Set of direct parent entities of this entity. */
        public final Set<JsonEUID> parents;

        JsonEntity(Entity e) {
            this.uid = new JsonEUID(e.uid);
            this.attrs = e.attrs;
            this.parents = new HashSet<JsonEUID>();
            for (String parent : e.parents) {
                this.parents.add(new JsonEUID(parent));
            }
        }
    }

    Set<JsonEntity> convertEntitiesToJsonEntities(Set<Entity> entities) {
        Set<JsonEntity> ret = new HashSet<JsonEntity>();
        for (Entity entity : entities) {
            ret.add(new JsonEntity(entity));
        }
        return ret;
    }
}
