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
        ad.setMessage(v.getResources().getString(R.string.longitude) + ": " + displayPoi.poi.getDecimalLongitude() + "°" +
                " " + v.getResources().getString(R.string.latitude) + ": " + displayPoi.poi.getDecimalLatitude() + "°" +
                "\n" + v.getResources().getString(R.string.altitude) + ": " + displayPoi.poi.getAltitudeMeters() + "m" +
                "\n" + v.getResources().getString(R.string.distance) + ": " + displayPoi.distance + "m" +
                "\n" + v.getResources().getString(R.string.name) + ": " + displayPoi.poi.getName() +
                "\n" + v.getResources().getString(R.string.grade) + ": " + GradeConverter.getConverter().getGradeFromOrder("UIAA", displayPoi.poi.getLevel()) +" (UIAA)" +
                "\n" + v.getResources().getString(R.string.description) + ": " + displayPoi.poi.getTags());
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, v.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
