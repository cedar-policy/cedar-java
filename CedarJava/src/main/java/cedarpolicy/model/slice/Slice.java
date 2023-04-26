package cedarpolicy.model.slice;

import cedarpolicy.value.Value;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Slice is a set of policies, entities; in essence, it is the part of the Cedar database that is
 * needed to answer a particular query. A slice is provided to the authorization engine to evaluate
 * a query. The creation of a slice is very important, if the slice leaves out a relevant policy,
 * the authorization engine may return an incorrect answer. The safe, easy choice is to have a slice
 * always contain every policy and entity.
 */
public interface Slice {

    /**
     * Get the policy set.
     *
     * @return Map from policyIDs to Policy source strings.
     */
    Map<String, String> getPolicies();

    /**
     * Get the attribute map.
     *
     * @return Map from EUIDs to attribute key/value maps.
     */
    Map<String, Map<String, Value>> getAttributes();

    /**
     * Get the parent map.
     *
     * @return Map from EUIDs to parent EUIDs.
     */
    Map<String, List<String>> getParents();

    /**
     * Get the entities.
     *
     * @return Set of Entities.
     */
    Set<Entity> getEntities();

    /**
     * Get the template policies.
     *
     * @return Map from template policy ID to template policy
     */
    Map<String, String> getTemplates();

    /**
     * Get the template instantiations.
     *
     * @return List of template instatiations
     */
    List<TemplateInstantiation> getTemplateInstantiations();
}
