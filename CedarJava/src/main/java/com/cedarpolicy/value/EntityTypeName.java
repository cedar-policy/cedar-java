
package com.cedarpolicy.value;

import com.cedarpolicy.loader.LibraryLoader;
import com.google.common.base.Suppliers;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**  Object representing a Cedar entity type
    An entity type has two components:
    1) An (optional) hierarchical namespace
    2) a basename
*/
public final class EntityTypeName {
    private final List<String> namespace;
    private final String basename;
    private final Supplier<String> entityTypeNameRepr;

    static {
        LibraryLoader.loadLibrary();
    }

    /**
     * Create an Entity Type Name.
     * This should only be called by code that ensures the contents of the strings are valid Type Names
     * @param namespace List of namespace components starting from the root.
     * @param basename Base name of the Entity Type
     */
    protected EntityTypeName(List<String> namespace, String  basename) {
        this.namespace = namespace;
        this.basename = basename;
        this.entityTypeNameRepr = Suppliers.memoize(() -> getEntityTypeNameRepr(this));
    }

    /**
     * Construct an EntityUID of this type
     * @param id The EntityIdentifier for the new EntityUID
     * @return An EntityUID with this type and the supplied EntityIdentifier
     */
    public EntityUID of(String id) {
        return new EntityUID(this, id);
    }

    /**
     * Construct an EntityUID of this type
     * @param id The EntityIdentifier for the new EntityUID
     * @return An EntityUID with this type and the supplied EntityIdentifier
     */
    public EntityUID of(EntityIdentifier id) {
        return new EntityUID(this, id);
    }

    /**
     * Get the namespace components in order from the root
     * Ex: the namespace `foo::bar::baz` would be returned in the order: `foo`, `bar`, `baz`
     * @return stream of Strings representing namespace components
     */
    public Stream<String> getNamespaceComponents() {
        return namespace.stream();
    }

    protected List<String> getNamespace() {
        return namespace;
    }

    /**
     * Get the namespace component as a string
     * This is equivalent to `toString` ignoring the basename.
     * @return String containing the namespace
     */
    public String getNamespaceAsString() {
        return namespace.stream().collect(Collectors.joining("::"));
    }

    public String toString() {
        return this.entityTypeNameRepr.get();
    }

    /**
     * Get the basename of this entity type
     * @return String containing the basename
     */
    public String getBaseName() {
        return basename;
    }


    public boolean equals(Object rhs) {
        if (rhs == null) {
            return false;
        }
        if (rhs == this) {
            return true;
        }
        try {
            var rhsTypename = (EntityTypeName) rhs;
            return basename.equals(rhsTypename.basename) && namespace.equals(rhsTypename.namespace);
        } catch (ClassCastException e) {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(basename, namespace);
    }

    /**
     * Attempt to parse a string into an EntityTypeName
     * @param src the string to be parsed
     * @return An optional containing the EntityTypeName if it was able to be parsed
     */
    public static Optional<EntityTypeName> parse(String src) {
        return parseEntityTypeName(src);
    }

    private static native Optional<EntityTypeName> parseEntityTypeName(String src);
    private static native String getEntityTypeNameRepr(EntityTypeName type);
}
