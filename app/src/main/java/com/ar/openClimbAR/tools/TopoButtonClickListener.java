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
        AlertDialog ad = new AlertDialog.Builder(parentActivity).create();
        ad.setCancelable(false); // This blocks the 'BACK' button
        ad.setTitle(displayPoi.name);
        ad.setMessage(v.getResources().getString(R.string.longitude) + ": " + displayPoi.decimalLongitude + "°" +
                " " + v.getResources().getString(R.string.latitude) + ": " + displayPoi.decimalLatitude + "°" +
                "\n" + v.getResources().getString(R.string.altitude) + ": " + displayPoi.altitudeMeters + "m" +
                "\n" + v.getResources().getString(R.string.distance) + ": " + displayPoi.distance + "m" +
                "\n" + v.getResources().getString(R.string.name) + ": " + displayPoi.name +
                "\n" + v.getResources().getString(R.string.grade) + ": " + GradeConverter.getConverter().getGradeFromOrder("UIAA", displayPoi.getLevel()) +" (UIAA)" +
                "\n" + v.getResources().getString(R.string.description) + ": " + displayPoi.getDescription());
        ad.setButton(DialogInterface.BUTTON_POSITIVE, v.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, v.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
