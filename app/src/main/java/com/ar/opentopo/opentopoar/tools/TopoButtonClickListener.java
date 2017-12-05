package com.ar.opentopo.opentopoar.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

/**
 * Created by xyz on 12/4/17.
 */

public class TopoButtonClickListener implements View.OnClickListener {
    private Activity parentActivity;
    private DisplayPOI displayPoi;

    public TopoButtonClickListener(Activity pActivity, DisplayPOI pPoi)
    {
        this.parentActivity = pActivity;
        this.displayPoi = pPoi;
    }
    @Override
    public void onClick(View v) {
        AlertDialog ad = new AlertDialog.Builder(parentActivity).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle("test");
        ad.setMessage("Long: " +
                displayPoi.poi.getDecimalLongitude() +
                "\nLat: " + displayPoi.poi.getDecimalLatitude() +
                "\nAlt: " + displayPoi.poi.getMetersAltitude() +
                "\nDistance: " + displayPoi.distance);
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, "ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
