package cedarpolicy;

import cedarpolicy.model.slice.Slice;
import cedarpolicy.serializer.SliceJsonSerializer;
import cedarpolicy.serializer.ValueCedarDeserializer;
import cedarpolicy.serializer.ValueCedarSerializer;
import cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

final class CedarJson {
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private CedarJson() {
        throw new IllegalStateException("Utility class");
    }

    public static ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }

    public static ObjectWriter objectWriter() {
        return OBJECT_MAPPER.writer();
    }

    public static ObjectReader objectReader() {
        return OBJECT_MAPPER.reader();
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();

        final SimpleModule module = new SimpleModule();
        module.addSerializer(Slice.class, new SliceJsonSerializer());
        module.addSerializer(Value.class, new ValueCedarSerializer());
        module.addDeserializer(Value.class, new ValueCedarDeserializer());
        mapper.registerModule(module);
        mapper.registerModule(new Jdk8Module());

        return mapper;
    }
}
