package com.blarley.rxretrobus.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface CachedEvent {
    String tag();
    boolean debounce() default false;
}
