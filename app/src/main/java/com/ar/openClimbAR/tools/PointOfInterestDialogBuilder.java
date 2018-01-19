package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.ar.openClimbAR.EditTopo;
import com.ar.openClimbAR.R;
import com.ar.openClimbAR.utils.ArUtils;
import com.ar.openClimbAR.utils.Constants;

import java.util.Locale;

/**
 * Created by xyz on 1/4/18.
 */

public class PointOfInterestDialogBuilder {
    private PointOfInterestDialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildDialog(final Activity activity, PointOfInterest poi) {
        return buildDialog(activity, poi, null);
    }

    public static AlertDialog buildDialog(final Context activity, final PointOfInterest poi, PointOfInterest observer) {
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
        alertMessage.append("\n").append(activity.getResources().getString(R.string.description)).append(": ").append(poi.getDescription());

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
                Intent intent = new Intent(activity, EditTopo.class);
                intent.putExtra("poiJSON", poi.getNodeInfo());
                activity.startActivity(intent);
            }
        });

        return ad;
    }
}
