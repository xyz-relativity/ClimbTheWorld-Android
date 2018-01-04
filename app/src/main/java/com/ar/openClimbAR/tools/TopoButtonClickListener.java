package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

import com.ar.openClimbAR.R;

/**
 * Created by xyz on 12/4/17.
 */

public class TopoButtonClickListener implements View.OnClickListener {
    private Activity parentActivity;
    private PointOfInterest displayPoi;

    public TopoButtonClickListener(Activity pActivity, PointOfInterest pPoi)
    {
        this.parentActivity = pActivity;
        this.displayPoi = pPoi;
    }
    @Override
    public void onClick(View v) {
        PointOfInterestDialogBuilder.buildDialog(parentActivity, displayPoi).show();
    }
}
