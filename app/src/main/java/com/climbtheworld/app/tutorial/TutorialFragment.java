package com.climbtheworld.app.tutorial;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

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

    public void onCreate(final ViewGroup view)
    {

    }

    public void onDestroy(final ViewGroup view)
    {

    }
}
