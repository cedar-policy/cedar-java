package cedarpolicy.model;

import cedarpolicy.model.schema.Schema;
import cedarpolicy.value.Value;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An authorization query consists of a principal, action, and resource as well as a context mapping
 * strings to Cedar values. When evaluating the query against a slice, the authorization engine
 * determines if the policies allow for the given principal to perform the given action against the
 * given resource.
 *
 * <p>An optional schema can be provided, but will not be used for validation unless you call
 * validate(). The schema is provided to allow parsing Entities from JSON without escape sequences
 * (in general, you don't need to worry about this if you construct your entities via the EntityUID
 * class).
 */
public class AuthorizationQuery {
    /** EUID of the principal in the query. */
    @JsonProperty("principal")
    public final Optional<String> principalEUID;
    /** EUID of the action in the query. */
    @JsonProperty("action")
    public final String actionEUID;
    /** EUID of the resource in the query. */
    @JsonProperty("resource")
    public final Optional<String> resourceEUID;

    /** Key/Value map representing the context of the query. */
    public final Map<String, Value> context;

    /** JSON object representing the Schema. */
    public final Optional<Schema> schema;

    /**
     * Create an authorization query from the EUIDs and Context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     */
    public AuthorizationQuery(
            Optional<String> principalEUID,
            String actionEUID,
            Optional<String> resourceEUID,
            Map<String, Value> context,
            Optional<Schema> schema) {
        this.principalEUID = principalEUID;
        this.actionEUID = actionEUID;
        this.resourceEUID = resourceEUID;
        if (context == null) {
            this.context = new HashMap<>();
        } else {
            this.context = new HashMap<>(context);
        }
        this.schema = schema;
    }

    /**
     * Create a query in the empty context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     */
    public AuthorizationQuery(
            Optional<String> principalEUID, String actionEUID, Optional<String> resourceEUID) {
        this(principalEUID, actionEUID, resourceEUID, new HashMap<>(), Optional.empty());
    }

    /**
     * Create an authorization query from the EUIDs and Context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     * @param context Key/Value context.
     * @param schema Schema (optional).
     */
    public AuthorizationQuery(
            String principalEUID,
            String actionEUID,
            String resourceEUID,
            Map<String, Value> context,
            Optional<Schema> schema) {
        this.principalEUID = Optional.of(principalEUID);
        this.actionEUID = actionEUID;
        this.resourceEUID = Optional.of(resourceEUID);
        if (context == null) {
            this.context = new HashMap<>();
        } else {
            this.context = new HashMap<>(context);
        }
        this.schema = schema;
    }

    /**
     * Create a query in the empty context.
     *
     * @param principalEUID Principal's EUID.
     * @param actionEUID Action's EUID.
     * @param resourceEUID Resource's EUID.
     */
    public AuthorizationQuery(String principalEUID, String actionEUID, String resourceEUID) {
        this(
                Optional.of(principalEUID),
                actionEUID,
                Optional.of(resourceEUID),
                new HashMap<>(),
                Optional.empty());
    }

    /** Readable string representation. */
    @Override
    public String toString() {
        return "Query(" + principalEUID + ",\t" + actionEUID + ",\t" + resourceEUID + ")";
    }
}
