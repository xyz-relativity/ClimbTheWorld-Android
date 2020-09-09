package com.climbtheworld.app.map.widget;

import android.view.View;

public abstract class MapButtonWidget {
    final MapViewWidget mapViewWidget;
    final View widget;

    protected MapButtonWidget(MapViewWidget mapViewWidget, View widget) {
        this.mapViewWidget = mapViewWidget;
        this.widget = widget;
    }
}
