package com.climbtheworld.app.storage.views;

import android.support.annotation.LayoutRes;
import android.view.ViewGroup;

public interface IDataViewFragment {
    @LayoutRes int getViewId();
    void onCreate(ViewGroup view);
    void onViewSelected();
}
