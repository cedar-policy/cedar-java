package com.cedarpolicy;

import java.lang.annotation.*;

/**
 * Marks this element as experimental.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Experimental {
    /** The experimental feature the element depends on. */
    ExperimentalFeature value();
}
