package cedarpolicy.value;

import java.util.Objects;

/** Represents the primitive Cedar integer type. */
public final class PrimLong extends Value {
    /** Value. */
    public final Long value;

    /**
     * Build PrimLong.
     *
     * @param i Long.
     */
    public PrimLong(Long i) throws NullPointerException {
        if (i == null) {
            throw new NullPointerException("Attempt to create PrimLong from null");
        }
        value = i;
    }

    /** equals. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrimLong primLong = (PrimLong) o;
        return value.equals(primLong.value);
    }

    /** hash. */
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
