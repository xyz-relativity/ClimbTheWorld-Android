package com.climbtheworld.app.utils;

import android.support.annotation.LayoutRes;
import android.view.ViewGroup;

public interface IPagerViewFragment {
    @LayoutRes int getViewId();
    void onCreate(ViewGroup view);
    void onViewSelected();
}
