package com.climbtheworld.app.openstreetmap.ui;

import android.graphics.drawable.Drawable;

import com.climbtheworld.app.storage.database.GeoNode;

import androidx.appcompat.app.AppCompatActivity;

public interface IDisplayableGeoNode {
    GeoNode getGeoNode();

    Drawable getIcon(AppCompatActivity parent);

    void showOnClickDialog(AppCompatActivity parent);

    GeoNode getMarkerData();

    void setVisibility(boolean visible);

    boolean isVisible();

    boolean isShowPoiInfoDialog();

    void setShowPoiInfoDialog(boolean showPoiInfoDialog);

    void setGhost(boolean isGhost);
}
