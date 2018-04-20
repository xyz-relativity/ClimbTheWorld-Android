package com.ar.climbing.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;

import com.ar.climbing.R;
import com.ar.climbing.activitys.EditTopoActivity;
import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.tools.GradeConverter;

/**
 * Created by xyz on 1/4/18.
 */

public class GeoNodeDialogBuilder {
    private GeoNodeDialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildNodeInfoDialog(final AppCompatActivity activity, final GeoNode poi) {
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
        if (GradeConverter.getConverter().isValidSystem(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem))) {
            alertMessage.append("<b>").append(activity.getResources().getString(R.string.grade)).append("</b>")
                    .append(": ").append(GradeConverter.getConverter().getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId()))
                    .append(" ").append(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).append("    (")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.STANDARD_SYSTEM).append(")");
        } else {
            alertMessage.append("<b>").append(activity.getResources().getString(R.string.grade)).append("</b>").append(": ")
                    .append(GradeConverter.getConverter().getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId()))
                    .append(" ").append(Constants.STANDARD_SYSTEM + "");
        }

        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.length_value, poi.getLengthMeters()));

        if (poi.isBolted()) {
            alertMessage.append("<br/>").append("<b>").append(activity.getResources().getString(R.string.protection)).append("</b>").append(": ")
                    .append(activity.getResources().getString(R.string.protection_bolted));
        }

        alertMessage.append("<br/>").append("<b>").append(activity.getResources().getString(R.string.climb_style)).append("</b>").append(": ");
        String sepChr = "";
        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
            alertMessage.append(sepChr).append(activity.getResources().getString(style.stringId));
            sepChr = ", ";
        }

        alertMessage.append("<br/>").append("<b>").append(activity.getResources().getString(R.string.description)).append("</b>").append(":<br/>").append(poi.getDescription());

        alertMessage.append("<br/>");
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.distance_value, distance, displayDistUnits));

        alertMessage.append("<br/>");
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.latitude_value,
                Globals.observer.decimalLatitude,
                Globals.observer.decimalLatitude > 0 ? activity.getResources().getStringArray(R.array.cardinal_names)[0] : activity.getResources().getStringArray(R.array.cardinal_names)[7]));
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.longitude_value,
                Globals.observer.decimalLongitude,
                Globals.observer.decimalLongitude > 0 ? activity.getResources().getStringArray(R.array.cardinal_names)[3] : activity.getResources().getStringArray(R.array.cardinal_names)[11]));
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.elevation_value, poi.elevationMeters));

        ad.setMessage(Html.fromHtml(alertMessage.toString()));

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

    public static void buildObserverInfoDialog(View v) {
        int azimuthID = (int)Math.floor(Math.abs(Globals.observer.degAzimuth - 11.25)/22.5);

        AlertDialog ad = new AlertDialog.Builder(v.getContext()).create();
        ad.setCancelable(true);
        ad.setTitle(v.getResources().getString(R.string.local_coordinate));
        ad.setIcon(R.drawable.person);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(v.getResources().getString(R.string.latitude_value,
                Globals.observer.decimalLatitude,
                Globals.observer.decimalLatitude > 0 ? v.getResources().getStringArray(R.array.cardinal_names)[0] : v.getResources().getStringArray(R.array.cardinal_names)[7]));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.longitude_value,
                Globals.observer.decimalLongitude,
                Globals.observer.decimalLongitude > 0 ? v.getResources().getStringArray(R.array.cardinal_names)[3] : v.getResources().getStringArray(R.array.cardinal_names)[11]));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.elevation_value, Globals.observer.elevationMeters));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.azimuth_value, v.getResources().getStringArray(R.array.cardinal_names)[azimuthID], Globals.observer.degAzimuth));

        ad.setMessage(Html.fromHtml(alertMessage.toString()));
        ad.setButton(DialogInterface.BUTTON_POSITIVE, v.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }
}
