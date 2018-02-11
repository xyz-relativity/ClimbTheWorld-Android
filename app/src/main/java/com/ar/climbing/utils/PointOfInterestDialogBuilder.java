package com.ar.climbing.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.ar.climbing.R;
import com.ar.climbing.activitys.EditTopoActivity;
import com.ar.climbing.tools.GradeConverter;

/**
 * Created by xyz on 1/4/18.
 */

public class PointOfInterestDialogBuilder {
    private PointOfInterestDialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildDialog(final AppCompatActivity activity, final PointOfInterest poi) {
        double distance = poi.distanceMeters;

        if (Globals.observer != null && distance == 0) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.observer, poi);
        }

        String displayDistUnits = "";
        if (distance > 1000) {
            displayDistUnits = "km";
            distance = distance / 1000;
        } else {
            displayDistUnits = "m";
        }

        AlertDialog ad = new AlertDialog.Builder(activity).create();
        ad.setCancelable(true);
        ad.setTitle(poi.getName());

        Drawable nodeIcon = activity.getResources().getDrawable(R.drawable.ic_topo_small);
        nodeIcon.setTintList(Globals.gradeToColorState(poi.getLevelId()));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
        ad.setIcon(nodeIcon);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(activity.getResources().getString(R.string.latitude,
                Globals.observer.decimalLatitude,
                Globals.observer.decimalLatitude > 0 ? activity.getResources().getStringArray(R.array.cardinals_names)[0] : activity.getResources().getStringArray(R.array.cardinals_names)[7]));
        alertMessage.append("\n").append(activity.getResources().getString(R.string.longitude,
                Globals.observer.decimalLongitude,
                Globals.observer.decimalLongitude > 0 ? activity.getResources().getStringArray(R.array.cardinals_names)[3] : activity.getResources().getStringArray(R.array.cardinals_names)[11]));
        alertMessage.append("\n").append(activity.getResources().getString(R.string.elevation, poi.elevationMeters));
        alertMessage.append("\n").append(activity.getResources().getString(R.string.length, poi.getLengthMeters()));

        if (GradeConverter.getConverter().isValidSystem(Globals.globalConfigs.getDisplaySystem())) {
            alertMessage.append("\n").append(activity.getResources().getString(R.string.grade))
                    .append(": ").append(GradeConverter.getConverter().getGradeFromOrder(Globals.globalConfigs.getDisplaySystem(), poi.getLevelId()))
                    .append(" ").append(Globals.globalConfigs.getDisplaySystem()).append("    (")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.STANDARD_SYSTEM).append(")");
        } else {
            alertMessage.append("\n").append(activity.getResources().getString(R.string.grade)).append(": ")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.STANDARD_SYSTEM + "");
        }

        if (poi.isBolted()) {
            alertMessage.append("\n").append(activity.getResources().getString(R.string.protection)).append(": ")
                    .append(activity.getResources().getString(R.string.protection_bolted));
        }

        alertMessage.append("\n").append(activity.getResources().getString(R.string.climb_style)).append(": ");
        String sepChr = "";
        for (PointOfInterest.ClimbingStyle style: poi.getClimbingStyles()) {
            alertMessage.append(sepChr).append(activity.getResources().getString(style.stringId));
            sepChr = ", ";
        }

        alertMessage.append("\n");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.distance, distance, displayDistUnits));

        alertMessage.append("\n");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.description)).append(":\n").append(poi.getDescription());

        ad.setMessage(alertMessage);

        ad.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(activity, EditTopoActivity.class);
                intent.putExtra("poiID", poi.getID());
                activity.startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
            }
        });

        return ad;
    }

    public static void obsDialogBuilder(View v) {
        int azimuthID = (int)Math.floor(Math.abs(Globals.observer.degAzimuth - 11.25)/22.5);

        AlertDialog ad = new AlertDialog.Builder(v.getContext()).create();
        ad.setCancelable(true);
        ad.setTitle(v.getResources().getString(R.string.local_coordinate));
        ad.setIcon(R.drawable.person);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(v.getResources().getString(R.string.latitude,
                Globals.observer.decimalLatitude,
                Globals.observer.decimalLatitude > 0 ? v.getResources().getStringArray(R.array.cardinals_names)[0] : v.getResources().getStringArray(R.array.cardinals_names)[7]));
        alertMessage.append("\n").append(v.getResources().getString(R.string.longitude,
                Globals.observer.decimalLongitude,
                Globals.observer.decimalLongitude > 0 ? v.getResources().getStringArray(R.array.cardinals_names)[3] : v.getResources().getStringArray(R.array.cardinals_names)[11]));
        alertMessage.append("\n").append(v.getResources().getString(R.string.elevation, Globals.observer.elevationMeters));
        alertMessage.append("\n").append(v.getResources().getString(R.string.azimuth, v.getResources().getStringArray(R.array.cardinals_names)[azimuthID], Globals.observer.degAzimuth));

        ad.setMessage(alertMessage);
        ad.setButton(DialogInterface.BUTTON_POSITIVE, v.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
