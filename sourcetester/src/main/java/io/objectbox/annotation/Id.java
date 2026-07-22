package io.objectbox.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Stub for ObjectBox @Id annotation
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Id {
    boolean assignable() default false;
}
