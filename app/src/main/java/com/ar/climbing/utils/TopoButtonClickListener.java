package com.ar.climbing.utils;

import android.support.v7.app.AppCompatActivity;
import android.view.View;

/**
 * Created by xyz on 12/4/17.
 */

public class TopoButtonClickListener implements View.OnClickListener {
    private AppCompatActivity parentActivity;
    private PointOfInterest displayPoi;

    public TopoButtonClickListener(AppCompatActivity pActivity, PointOfInterest pPoi)
    {
        this.parentActivity = pActivity;
        this.displayPoi = pPoi;
    }
    @Override
    public void onClick(View v) {
        PointOfInterestDialogBuilder.buildDialog(parentActivity, displayPoi).show();
    }
}
