package cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Instantiation for policy template. */
public class Instantiation {
    /** The slot in the template. */
    public final String slot;
    /** The value to put in the slot. */
    public final EntityTypeAndId value;

    /**
     * Instantiation for policy template.
     *
     * @param slot the slot in the template.
     * @param value the value to put in the slot
     */
    @JsonCreator
    public Instantiation(
            @JsonProperty("slot") String slot, @JsonProperty("value") EntityTypeAndId value) {
        this.slot = slot;
        this.value = value;
    }
}
