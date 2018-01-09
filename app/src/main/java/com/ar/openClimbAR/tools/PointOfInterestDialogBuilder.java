package com.ar.openClimbAR.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import com.ar.openClimbAR.EditTopo;
import com.ar.openClimbAR.R;
import com.ar.openClimbAR.utils.Constants;

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

    public static AlertDialog buildDialog(final Activity activity,final PointOfInterest poi, PointOfInterest observer) {
        float distance = poi.distanceMeters;

        if (observer != null) {
            distance = ArUtils.calculateDistance(observer, poi);
        }

        AlertDialog ad = new AlertDialog.Builder(activity).create();
        ad.setCancelable(true);
        ad.setTitle(poi.name);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(activity.getResources().getString(R.string.longitude) + ": " + poi.decimalLongitude + "°");
        alertMessage.append("\n" + activity.getResources().getString(R.string.latitude) + ": " + poi.decimalLatitude + "°");
        alertMessage.append("\n" + activity.getResources().getString(R.string.altitude) + ": " + poi.altitudeMeters + "m");
        alertMessage.append("\n" + activity.getResources().getString(R.string.distance) + ": " + distance + "m");
        alertMessage.append("\n" + activity.getResources().getString(R.string.length) + ": " + poi.getLengthMeters() + "m");

        if (GradeConverter.getConverter().isValidSystem(Constants.DISPLAY_SYSTEM)) {
            alertMessage.append("\n" + activity.getResources().getString(R.string.grade)
                    + ": " + GradeConverter.getConverter().getGradeFromOrder(Constants.DISPLAY_SYSTEM, poi.getLevelId()) +" " + Constants.DISPLAY_SYSTEM + "    ("
                    + GradeConverter.getConverter().getGradeFromOrder(Constants.DEFAULT_SYSTEM, poi.getLevelId()) +" " + Constants.DEFAULT_SYSTEM + ")");
        } else {
            alertMessage.append("\n" + activity.getResources().getString(R.string.grade)
                    + ": " + GradeConverter.getConverter().getGradeFromOrder(Constants.DEFAULT_SYSTEM, poi.getLevelId()) +" " + Constants.DEFAULT_SYSTEM + "");
        }

        alertMessage.append("\n");
        alertMessage.append("\n" + activity.getResources().getString(R.string.description) + ": " + poi.getDescription());

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
