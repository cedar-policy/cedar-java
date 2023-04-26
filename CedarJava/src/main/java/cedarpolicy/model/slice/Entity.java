package cedarpolicy.model.slice;

import cedarpolicy.value.Value;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An entity is the kind of object about which authorization decisions are made; principals,
 * actions, and resources are all a kind of entity. Each entity is defined by its entity type, a
 * unique identifier (UID), zero or more attributes mapped to values, and zero or more parent
 * entities.
 */
public class Entity {
    /** EUID of this entity object. */
    public final String uid;

    /** Key/Value attribute map. */
    public final Map<String, Value> attrs;

    /** Set of entity EUIDs that are parents to this entity. */
    public final Set<String> parents;

    /**
     * Create an entity from unwrapped JSON values.
     *
     * @param uid Euid of the Entity.
     * @param attributes Key/Value map of attributes.
     * @param parents Set of parent entities.
     */
    public Entity(String uid, Map<String, Value> attributes, Set<String> parents) {
        this.uid = uid;
        this.attrs = new HashMap<>(attributes);
        this.parents = parents;
    }

    /**
     * Create an entity with only a EUID. The entity has an empty attribute map and no parents.
     *
     * @param uid EUID of the entity.
     */
    public Entity(String uid) {
        this.uid = uid;
        this.attrs = new HashMap<>();
        this.parents = new HashSet<>();
    }

    @Override
    public String toString() {
        String parentStr = "";
        if (!parents.isEmpty()) {
            parentStr = "\n\tparents:\n\t\t" + String.join("\n\t\t", parents);
        }
        String attributeStr = "";
        if (!attrs.isEmpty()) {
            attributeStr =
                    "\n\tattrs:\n\t\t"
                            + attrs.entrySet().stream()
                                    .map(e -> e.getKey() + ": " + e.getValue())
                                    .collect(Collectors.joining("\n\t\t"));
        }
        return uid + parentStr + attributeStr;
    }
}
