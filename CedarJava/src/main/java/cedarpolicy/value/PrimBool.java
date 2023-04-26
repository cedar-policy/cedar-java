package cedarpolicy.value;

import java.util.Objects;

/** Represents a primitive Cedar boolean value. */
public final class PrimBool extends Value {
    /** value. */
    public final Boolean value;

    /**
     * Build PrimBool.
     *
     * @param b Boolean.
     */
    public PrimBool(Boolean b) throws NullPointerException {
        if (b == null) {
            throw new NullPointerException("Attempt to create PrimBool from null");
        }
        value = b;
    }

    /** Equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrimBool primBool = (PrimBool) o;
        return value.equals(primBool.value);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** toString. */
    @Override
    public String toString() {
        return value.toString();
    }

    /** To Cedar expr. */
    @Override
    String toCedarExpr() {
        return value.toString();
    }
}
