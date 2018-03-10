package com.ar.climbing.utils;

import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.ar.climbing.storage.database.GeoNode;

/**
 * Created by xyz on 12/4/17.
 */

public class TopoButtonClickListener implements View.OnClickListener {
    private AppCompatActivity parentActivity;
    private GeoNode displayPoi;

    public TopoButtonClickListener(AppCompatActivity pActivity, GeoNode pPoi)
    {
        this.parentActivity = pActivity;
        this.displayPoi = pPoi;
    }
    @Override
    public void onClick(View v) {
        DialogBuilder.buildNodeInfoDialog(parentActivity, displayPoi).show();
    }
}
