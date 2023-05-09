package cedarpolicy.pbt;

import cedarpolicy.model.slice.Entity;
import cedarpolicy.value.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;

/** Generate random actions for testing. */
public final class ActionGen {
    /** Generate a random Action Group. */
    public static List<Entity> getEntities() {
        List<Entity> actions = new ArrayList<>();
        String actionEuid = "Action::\"" + Utils.strings() + "\"";
        Map<String, Value> actionAttributes = new HashMap<>();
        Set<String> actionParents = new HashSet<>();
        Entity e = new Entity(actionEuid, actionAttributes, actionParents);
        actions.add(e);
        int count = Arbitraries.integers().between(10, 100).sample();

        for (int i = 0; i < count; i++) {
            actionEuid = "Action::\"" + Utils.strings() + "\"";
            if (!e.uid.equals(actionEuid)) {
                e.parents.add(actionEuid);
            }
            actionAttributes = new HashMap<>();
            actionParents = new HashSet<>();
            actions.add(new Entity(actionEuid, actionAttributes, actionParents));
        }
        return actions;
    }

    private ActionGen() {
        throw new IllegalStateException("Utility class");
    }
}
