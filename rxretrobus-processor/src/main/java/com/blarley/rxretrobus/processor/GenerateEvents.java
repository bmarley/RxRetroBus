package com.blarley.rxretrobus.processor;

public @interface GenerateEvents {
    String baseUrl() default "";
    boolean retrofit() default true;
}
