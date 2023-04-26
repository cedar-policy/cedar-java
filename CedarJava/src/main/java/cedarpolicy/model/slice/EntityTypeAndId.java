package cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Entity type and ID. */
public class EntityTypeAndId {
    /** Entity type. */
    public final String ty;
    /** Entity ID. */
    public final String eid;

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
}
