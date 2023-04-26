package cedarpolicy.serializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Represent JSON format of Entity Unique Identifier. */
@JsonDeserialize
public class JsonEUID {
    /** euid (__expr is used as escape sequence in JSON). */
    @JsonProperty("__expr")
    public final String euid;

    /** Readable string representation. */
    public String toString() {
        return euid;
    }

    /**
     * Build JsonEUID.
     *
     * @param s Entity Unique ID.
     */
    public JsonEUID(String s) {
        this.euid = s;
    }

    /** Build JsonEUID (default constructor needed by Jackson). */
    public JsonEUID() {
        this.euid = "";
    }
}
