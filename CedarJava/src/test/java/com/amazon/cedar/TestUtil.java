package cedarpolicy;

import cedarpolicy.model.schema.Schema;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Utils to help with tests. */
public final class TestUtil {
    private TestUtil() {}

    /**
     * Load schema file.
     *
     * @param schemaFile Schema file name
     */
    public static Schema loadSchemaResource(String schemaFile) {
        try {
            return new Schema(
                    new String(
                            Files.readAllBytes(
                                    Paths.get(
                                            ValidationTests.class.getResource(schemaFile).toURI())),
                            StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test schema file " + schemaFile, e);
        }
    }
}
