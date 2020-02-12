package com.climbtheworld.app.configs;

import android.view.View;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import java.util.LinkedList;
import java.util.List;

public abstract class ConfigFragment {
    protected final AppCompatActivity parent;
    protected final View view;

    private List<OnFilterChangeListener> listenerList = new LinkedList<>();

    public interface OnFilterChangeListener {
        void onFilterChange();
    }

    void notifyListeners() {
        for (OnFilterChangeListener listener: listenerList) {
            listener.onFilterChange();
        }
    }

    public void addListener(OnFilterChangeListener listener) {
        listenerList.add(listener);
    }

    ConfigFragment(AppCompatActivity parent, View view) {
        this.parent = parent;
        this.view = view;
    }

    protected <T extends View> T findViewById(@IdRes int id){
        return view.findViewById(id);
    }
}
