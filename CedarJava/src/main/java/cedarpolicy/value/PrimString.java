package cedarpolicy.value;

import java.util.Objects;

/** Represents a primitive Cedar string value. */
public final class PrimString extends Value {
    /** Value. */
    public final String value;

    /**
     * Build PrimString.
     *
     * @param s String.
     */
    public PrimString(String s) {
        value = s;
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
        PrimString that = (PrimString) o;
        return value.equals(that.value);
    }

    /** Hash. */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** ToString. */
    @Override
    public String toString() {
        return value;
    }

    /** To Cedar expr. */
    @Override
    String toCedarExpr() {
        return "\"" + value + "\"";
    }
}
