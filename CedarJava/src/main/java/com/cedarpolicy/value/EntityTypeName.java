
package com.cedarpolicy.value;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.NonNull;

/**  Object representing a Cedar entity type 
    An entity type has two components: 
    1) An (optional) hierarchical namespace
    2) a basename 
*/
public final class EntityTypeName {
    private List<String> namespace;
    private String basename;
    static {
        System.load(System.getenv("CEDAR_JAVA_FFI_LIB"));
    }

    protected EntityTypeName(List<String> namespace, String  basename) {
        this.namespace = namespace;
        this.basename= basename;
    }

    public EntityUID of(String id) {
        return new EntityUID(this, id);
    }

    public EntityUID of(EntityIdentifier id) {
        return new EntityUID(this, id);
    }

    /** 
     * Get the namespace components in order from the root
     * Ex: the namespace `foo::bar::baz` would be returned in the order: `foo`, `bar`, `baz`
     */
    public Stream<String> getNamespaceComponents() {
        return namespace.stream();
    }

    public List<String> getNamespace() {
        return namespace;
    }

    /** 
     * Get the namespace component has a string
     * This is equivalent to `toString` ignoring the basename.
     */
    public String getNamespaceAsString() {
        return namespace.stream().collect(Collectors.joining("::"));
    }

    public String toString() {
        return getEntityTypeNameRepr(this);
    }

    /** 
     * Get the basename of this entity type
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
            var rhs_typename = (EntityTypeName) rhs; 
            return basename.equals(rhs_typename.basename) && namespace.equals(rhs_typename.namespace);
        } catch (ClassCastException e) { 
            return false;
        }
    }

    public int hashCode() { 
        return basename.hashCode() + namespace.hashCode();
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
