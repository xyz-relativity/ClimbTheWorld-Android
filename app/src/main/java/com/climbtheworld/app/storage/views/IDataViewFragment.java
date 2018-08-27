package com.climbtheworld.app.storage.views;

import android.support.annotation.LayoutRes;
import android.view.ViewGroup;

import com.climbtheworld.app.storage.IDataManagerEventListener;

public interface IDataViewFragment extends IDataManagerEventListener {
    @LayoutRes int getViewId();
    void onCreate(ViewGroup view);
    void onViewSelected();
}
