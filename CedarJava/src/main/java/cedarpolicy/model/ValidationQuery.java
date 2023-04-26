package cedarpolicy.model;

import cedarpolicy.model.schema.Schema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.Objects;

/** Information passed to Cedar for validation. */
public final class ValidationQuery {
    private final Schema schema;
    private final Map<String, String> policySet;

    /**
     * Construct a validation query.
     *
     * @param schema Schema for the query
     * @param policySet Map of Policy ID to policy.
     */
    @SuppressFBWarnings
    public ValidationQuery(Schema schema, Map<String, String> policySet) {
        if (schema == null) {
            throw new NullPointerException("schema");
        }

        if (policySet == null) {
            throw new NullPointerException("policySet");
        }

        this.schema = schema;
        this.policySet = policySet;
    }

    /**
     * Get the schema.
     *
     * @return The schema.
     */
    public Schema getSchema() {
        return this.schema;
    }

    /**
     * Get the policy set.
     *
     * @return Map of policy ID to policy.
     */
    @SuppressFBWarnings
    public Map<String, String> getPolicySet() {
        return this.policySet;
    }

    /** Test equality. */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof ValidationQuery)) {
            return false;
        }

        final ValidationQuery other = (ValidationQuery) o;
        return schema.equals(other.schema) && policySet.equals(other.policySet);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(schema, policySet);
    }

    /** Get readable string representation. */
    public String toString() {
        return "ValidationQuery(schema=" + schema + ", policySet=" + policySet + ")";
    }
}
