package com.blarley.rxretrobus;

/**
 * Created by esu on 7/10/17.
 */

public class FireAndForgetEvent extends Publish {

    public FireAndForgetEvent(String tag, boolean debounced) {
        super(tag, false, debounced, false);
    }
}