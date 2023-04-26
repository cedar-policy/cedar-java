package cedarpolicy.model.slice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Template instantiation. */
public class TemplateInstantiation {

    /** The template ID. */
    @JsonProperty("template_id")
    public final String templateId;

    /** The resulting policy id after slots in the template are filled. */
    @JsonProperty("result_policy_id")
    public final String resultPolicyId;

    /** The instantiations to fill the slots. */
    public final List<Instantiation> instantiations;

    /**
     * Template Instantiation.
     *
     * @param templateId the template ID.
     * @param resultPolicyId the id of the resulting policy.
     * @param instantiations the instantiations.
     */
    @JsonCreator
    public TemplateInstantiation(
            @JsonProperty("template_id") String templateId,
            @JsonProperty("result_policy_id") String resultPolicyId,
            @JsonProperty("instantiations") List<Instantiation> instantiations) {
        this.templateId = templateId;
        this.resultPolicyId = resultPolicyId;
        this.instantiations = instantiations;
    }
}
