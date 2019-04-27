package com.climbtheworld.app.converter;

import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import com.climbtheworld.app.utils.IPagerViewFragment;

public abstract class ConverterFragment implements IPagerViewFragment {
    final AppCompatActivity parent;
    @LayoutRes
    int viewID;
    ViewGroup view;

    public ConverterFragment(AppCompatActivity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;
    }

    public int getViewId() {
        return viewID;
    }

    <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }
}
