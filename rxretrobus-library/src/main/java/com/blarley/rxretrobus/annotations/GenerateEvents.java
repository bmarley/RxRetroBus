package com.blarley.rxretrobus.annotations;

public @interface GenerateEvents {
    String baseUrl() default "";
    boolean retrofit() default true;
}
