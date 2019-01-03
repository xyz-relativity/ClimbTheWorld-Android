package com.climbtheworld.app.osm.editor;

import android.view.View;
import android.view.ViewGroup;

public abstract class Tags {
    ViewGroup container;

    public void showTags() {
        container.setVisibility(View.VISIBLE);
    }

    public void hideTags() {
        container.setVisibility(View.GONE);
    }
}
