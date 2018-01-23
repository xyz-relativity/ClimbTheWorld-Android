package com.ar.openClimbAR.tools;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.ar.openClimbAR.EditTopoActivity;
import com.ar.openClimbAR.R;
import com.ar.openClimbAR.utils.ArUtils;
import com.ar.openClimbAR.utils.Constants;
import com.ar.openClimbAR.utils.GlobalVariables;

import java.util.Locale;

/**
 * Created by xyz on 1/4/18.
 */

public class PointOfInterestDialogBuilder {
    private PointOfInterestDialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildDialog(final AppCompatActivity activity, PointOfInterest poi) {
        return buildDialog(activity, poi, null);
    }

    public static AlertDialog buildDialog(final AppCompatActivity activity, final PointOfInterest poi, PointOfInterest observer) {
        float distance = poi.distanceMeters;

        if (observer != null) {
            distance = ArUtils.calculateDistance(observer, poi);
        }

        String displayDistWithUnits = "";
        if (distance > 1000) {
            displayDistWithUnits = String.format(Locale.getDefault(),"%f km", distance / 1000f);
        } else {
            displayDistWithUnits = String.format(Locale.getDefault(),"%f m", distance);
        }

        AlertDialog ad = new AlertDialog.Builder(activity).create();
        ad.setCancelable(true);
        ad.setTitle(poi.name);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(activity.getResources().getString(R.string.longitude)).append(": ").append(poi.decimalLongitude).append("°");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.latitude)).append(": ").append(poi.decimalLatitude).append("°");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.elevation)).append(": ").append(poi.elevationMeters).append(" m");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.distance)).append(": ").append(displayDistWithUnits);
        alertMessage.append("\n").append(activity.getResources().getString(R.string.length)).append(": ").append(poi.getLengthMeters()).append(" m");

        if (GradeConverter.getConverter().isValidSystem(Constants.DISPLAY_SYSTEM)) {
            alertMessage.append("\n").append(activity.getResources().getString(R.string.grade))
                    .append(": ").append(GradeConverter.getConverter().getGradeFromOrder(Constants.DISPLAY_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.DISPLAY_SYSTEM).append("    (")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.DEFAULT_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.DEFAULT_SYSTEM).append(")");
        } else {
            alertMessage.append("\n").append(activity.getResources().getString(R.string.grade)).append(": ")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.DEFAULT_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.DEFAULT_SYSTEM + "");
        }

        alertMessage.append("\n").append(activity.getResources().getString(R.string.climb_style)).append(": ");
        String sepChr = "";
        for (PointOfInterest.climbingStyle style: poi.getClimbingStyles()) {
            alertMessage.append(sepChr).append(activity.getResources().getString(style.stringId));
            sepChr = ", ";
        }

        alertMessage.append("\n");
        alertMessage.append("\n").append(activity.getResources().getString(R.string.description)).append(":\n").append(poi.getDescription());

        ad.setMessage(alertMessage);

        ad.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
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
        int azimuthID = (int)Math.floor(Math.abs(GlobalVariables.observer.degAzimuth - 11.25)/22.5);

        AlertDialog ad = new AlertDialog.Builder(v.getContext()).create();
        ad.setCancelable(true);
        ad.setTitle(GlobalVariables.observer.name);
        ad.setMessage(v.getResources().getString(R.string.longitude) + ": " + GlobalVariables.observer.decimalLongitude + "°" +
                " " + v.getResources().getString(R.string.latitude) + ": " + GlobalVariables.observer.decimalLatitude + "°" +
                "\n" + v.getResources().getString(R.string.elevation) + ": " + GlobalVariables.observer.elevationMeters + "m" +
                "\n" + v.getResources().getString(R.string.azimuth) + ": " + Constants.CARDINAL_NAMES[azimuthID] + " (" + GlobalVariables.observer.degAzimuth + "°)");
        ad.setButton(DialogInterface.BUTTON_NEUTRAL, v.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
