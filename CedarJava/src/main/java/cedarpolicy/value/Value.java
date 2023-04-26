package cedarpolicy.value;

import cedarpolicy.serializer.ValueCedarDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** A value in the Cedar language model. */
@JsonDeserialize(using = ValueCedarDeserializer.class)
public abstract class Value {
    /**
     * Convert the Value instance into a string containing the Cedar source code for the equivalent
     * Cedar value.
     *
     * @return Cedar source code for the value.
     */
    abstract String toCedarExpr();
}
