package com.climbtheworld.app.tutorial;

import android.app.Activity;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public abstract class TutorialFragment {
    public final LayoutInflater inflater;
    Activity parent;
    @LayoutRes
    int viewID;
    
    public TutorialFragment(Activity parent, @LayoutRes int viewID) {
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
