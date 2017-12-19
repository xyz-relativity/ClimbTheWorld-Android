package com.ar.openClimbAR.tools;

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
        ad.setTitle(displayPoi.poi.getName());
        ad.setMessage("Long: " + displayPoi.poi.getDecimalLongitude() + "°" +
                " Lat: " + displayPoi.poi.getDecimalLatitude() + "°" +
                "\nAlt: " + displayPoi.poi.getAltitudeMeters() + "m" +
                "\nDistance: " + displayPoi.distance + "m" +
                "\nName: " + displayPoi.poi.getName() +
                "\nLevel: " + displayPoi.poi.getLevel() +
                "\nProtection: " + displayPoi.poi.getProtection() +
                "\nDescription: " + displayPoi.poi.getDescription());
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, "ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
