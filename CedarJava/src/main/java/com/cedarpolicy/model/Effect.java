package com.cedarpolicy.model;

/**
 * Represents the effect of a Cedar policy.
 */
public enum Effect {
    PERMIT,
    FORBID;

    /**
     * Converts a string to an Effect enum value, case-insensitive.
     *
     * @param effectString the string value to convert
     * @return the corresponding Effect enum value
     * @throws NullPointerException if the effectString is null
     * @throws IllegalArgumentException if the effectString doesn't match any Effect in {permit, forbid}
     */
    public static Effect fromString(String effectString) {

        if (effectString == null) {
            throw new NullPointerException();
        }

        switch (effectString.toLowerCase()) {
            case "permit":
                return PERMIT;
            case "forbid":
                return FORBID;
            default:
                throw new IllegalArgumentException("Invalid Effect: " + effectString + ". Expected 'permit' or 'forbid'");
        }
    }
}

