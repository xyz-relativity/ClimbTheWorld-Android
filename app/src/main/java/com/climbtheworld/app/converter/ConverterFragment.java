package com.climbtheworld.app.converter;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;

public class ConverterFragment {
    final AppCompatActivity parent;
    @LayoutRes
    int viewID;

    public ConverterFragment(AppCompatActivity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;
    }

    public int getViewId() {
        return viewID;
    }
}
