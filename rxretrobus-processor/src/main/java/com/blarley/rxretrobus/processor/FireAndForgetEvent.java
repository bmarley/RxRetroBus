package com.blarley.rxretrobus.processor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value = RetentionPolicy.RUNTIME)
public @interface FireAndForgetEvent {
    String tag();
    boolean debounce() default false;
}
