package com.ar.climbing.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.activitys.EditTopoActivity;
import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.tools.GradeConverter;

/**
 * Created by xyz on 1/4/18.
 */

public class DialogBuilder {
    private DialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildNodeInfoDialog(final AppCompatActivity activity, final GeoNode poi) {
        double distance = poi.distanceMeters;

        if (Globals.virtualCamera != null && distance == 0) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
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
            alertMessage.append(activity.getResources().getString(R.string.grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem),
                    GradeConverter.getConverter().
                            getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId())));
        }

        if (!Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem).equalsIgnoreCase(Constants.STANDARD_SYSTEM)) {
            alertMessage.append("<br/>")
                    .append(activity.getResources().getString(R.string.grade,
                            Constants.STANDARD_SYSTEM,
                            GradeConverter.getConverter().
                                    getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId())));
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

        alertMessage.append("<br/>");
        alertMessage.append("<br/>").append("<b>").append(activity.getResources().getString(R.string.description)).append("</b>").append(":<br/>").append(poi.getDescription());

        alertMessage.append("<br/>");

        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.distance_value, distance, displayDistUnits));

        alertMessage.append("<br/>");
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.latitude_value,
                poi.decimalLatitude, ""));
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.longitude_value,
                poi.decimalLongitude, ""));
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

        ad.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.navigate), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=" + poi.decimalLatitude+"," + poi.decimalLongitude + " (" + poi.getName() + ")"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        });

        return ad;
    }

    public static void buildObserverInfoDialog(View v) {
        int azimuthID = (int) Math.floor(Math.abs(Globals.virtualCamera.degAzimuth - 11.25) / 22.5);

        AlertDialog ad = new AlertDialog.Builder(v.getContext()).create();
        ad.setCancelable(true);
        ad.setTitle(v.getResources().getString(R.string.local_coordinate));
        ad.setIcon(R.drawable.person);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(v.getResources().getString(R.string.latitude_value,
                Globals.virtualCamera.decimalLatitude,
                Globals.virtualCamera.decimalLatitude > 0 ? v.getResources().getStringArray(R.array.cardinal_names)[0] : v.getResources().getStringArray(R.array.cardinal_names)[7]));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.longitude_value,
                Globals.virtualCamera.decimalLongitude,
                Globals.virtualCamera.decimalLongitude > 0 ? v.getResources().getStringArray(R.array.cardinal_names)[3] : v.getResources().getStringArray(R.array.cardinal_names)[11]));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.elevation_value, Globals.virtualCamera.elevationMeters));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.azimuth_value, v.getResources().getStringArray(R.array.cardinal_names)[azimuthID], Globals.virtualCamera.degAzimuth));

        ad.setMessage(Html.fromHtml(alertMessage.toString()));
        ad.setButton(DialogInterface.BUTTON_POSITIVE, v.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.show();
    }

    public static Dialog buildLoadDialog(Context context, String message, DialogInterface.OnCancelListener cancelListener ) {
        Dialog mOverlayDialog = new Dialog(context);

        mOverlayDialog.setContentView(R.layout.dialog_loading);

        ((TextView)mOverlayDialog.getWindow().findViewById(R.id.dialogMessage)).setText(message);

        if (cancelListener == null) {
            mOverlayDialog.setCancelable(false);
        } else {
            mOverlayDialog.setCancelable(true);
            mOverlayDialog.setOnCancelListener(cancelListener);
        }

        mOverlayDialog.setCanceledOnTouchOutside(false);
        return mOverlayDialog;
    }
}
