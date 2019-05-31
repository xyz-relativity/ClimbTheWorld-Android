package com.climbtheworld.app.tutorial;

import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public abstract class TutorialFragment {
    public final LayoutInflater inflater;
    AppCompatActivity parent;
    @LayoutRes
    private int viewID;
    
    TutorialFragment(AppCompatActivity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;

        inflater = parent.getLayoutInflater();
    }

    public @LayoutRes
    int getViewId() {
        return this.viewID;
    }

    public abstract void onCreate(final ViewGroup view);
}
