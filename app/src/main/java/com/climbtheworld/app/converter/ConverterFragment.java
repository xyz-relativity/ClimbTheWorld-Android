package com.climbtheworld.app.converter;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.climbtheworld.app.utils.IPagerViewFragment;

public abstract class ConverterFragment implements IPagerViewFragment {
    final Activity parent;
    @LayoutRes
    int viewID;
    ViewGroup view;

    public ConverterFragment(Activity parent, @LayoutRes int viewID) {
        this.parent = parent;
        this.viewID = viewID;
    }

    public int getViewId() {
        return viewID;
    }

    <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }

    protected void showKeyboard(final View focused) {
        focused.post(new Runnable() {
            @Override
            public void run() {
                final InputMethodManager inputManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    protected void hideKeyboard() {
        view.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        });
    }
}
