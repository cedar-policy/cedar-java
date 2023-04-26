package cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Policies in the Cedar language. */
public class Policy {
    /** Policy string. */
    public final String policySrc;
    /** Policy ID. */
    public final String policyID;

    /**
     * Creates a Cedar policy object.
     *
     * @param policy String containing the source code of a Cedar policy in the Cedar policy
     *     language.
     * @param policyID The id of this policy. Must be unique. Note: We may flip the order of the
     *     arguments here for idiomatic reasons.
     */
    public Policy(
            @JsonProperty("policySrc") String policy, @JsonProperty("policyID") String policyID)
            throws NullPointerException {

        if (policy == null) {
            throw new NullPointerException("Failed to construct policy from null string");
        }
        if (policyID == null) {
            throw new NullPointerException("Failed to construct policy with null ID");
        }
        this.policySrc = policy;
        this.policyID = policyID;
    }

    @Override
    public String toString() {
        return "// Policy ID: " + policyID + "\n" + policySrc;
    }
}
