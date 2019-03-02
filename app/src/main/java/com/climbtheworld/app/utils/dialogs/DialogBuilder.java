package com.climbtheworld.app.utils.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.SensorListener;
import com.climbtheworld.app.storage.views.RemoteDataFragment;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.CompassWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by xyz on 1/4/18.
 */

public class DialogBuilder {
    private static List<Dialog> activeDialogs = new ArrayList<>();
    private DialogBuilder() {
        //hide constructor
    }

    static AlertDialog getNewDialog(AppCompatActivity activity) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        activeDialogs.add(alertDialog);
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                activeDialogs.remove(alertDialog);
            }
        });

        return alertDialog;
    }

    public static void closeAllDialogs () {
        for (Dialog diag: activeDialogs) {
            diag.dismiss();
        }
    }

    public static AlertDialog buildObserverInfoDialog(final AppCompatActivity activity, final SensorListener sensorListener) {
        final String azimuthValue = "%s (%3.4f°)";

        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setCancelable(true);
        alertDialog.setTitle(activity.getResources().getString(R.string.local_coordinate));
        alertDialog.setIcon(R.drawable.person);

        final View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_my_location, alertDialog.getListView(), false);

        final CompassWidget compass = new CompassWidget(result.findViewById(R.id.compassButton));
        final IOrientationListener orientationEvent = new IOrientationListener() {
            @Override
            public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
                int azimuthID = (int) Math.floor(Math.abs(pAzimuth - 11.25) / 22.5);
                ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(Globals.virtualCamera.decimalLatitude));
                ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(Globals.virtualCamera.decimalLongitude));
                ((TextView)result.findViewById(R.id.editElevation)).setText(String.valueOf(Globals.virtualCamera.elevationMeters));
                ((TextView)result.findViewById(R.id.editAzimuth)).setText(String.format(Locale.getDefault(), azimuthValue, activity.getResources().getStringArray(R.array.cardinal_names)[azimuthID], Globals.virtualCamera.degAzimuth));
            }
        };

        sensorListener.addListener(compass, orientationEvent);


        alertDialog.setView(result);

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                sensorListener.removeListener(compass, orientationEvent);
            }
        });

        alertDialog.create();
        return alertDialog;
    }

    public static AlertDialog buildDownloadRegionAlert(final AppCompatActivity activity) {
        final AlertDialog alertDialog = getNewDialog(activity);
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(R.string.tutorial_region_download_title);

        Drawable icon = activity.getDrawable(android.R.drawable.ic_dialog_alert).mutate();
        icon.setTint(activity.getResources().getColor(android.R.color.holo_orange_light));
        alertDialog.setIcon(icon);

        Globals.loadCountryList();

        ViewGroup result = (ViewGroup)activity.getLayoutInflater().inflate(R.layout.fragment_dialog_download, alertDialog.getListView(), false);
        RemoteDataFragment downloadView = new RemoteDataFragment(activity, R.layout.fragment_data_manager_remote_data);
        downloadView.onCreate(result);

        alertDialog.setView(result);

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alertDialog.create();

        return alertDialog;
    }

    public static AlertDialog buildLoadDialog(AppCompatActivity activity, String message, DialogInterface.OnCancelListener cancelListener ) {
        AlertDialog alertDialog = getNewDialog(activity);
        alertDialog.setTitle(R.string.loading_dialog);
        Drawable icon = activity.getDrawable(android.R.drawable.ic_dialog_info).mutate();
        icon.setTint(activity.getResources().getColor(android.R.color.holo_green_light));
        alertDialog.setIcon(icon);

        ViewGroup result = (ViewGroup)activity.getLayoutInflater().inflate(R.layout.dialog_loading, alertDialog.getListView(), false);
        alertDialog.setView(result);

        ((TextView)result.findViewById(R.id.dialogMessage)).setText(message);

        if (cancelListener == null) {
            alertDialog.setCancelable(false);
        } else {
            alertDialog.setCancelable(true);
            alertDialog.setOnCancelListener(cancelListener);
        }

        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.create();
        return alertDialog;
    }

    public static void showErrorDialog(final Context parent, final String message, final DialogInterface.OnClickListener listener) {

        Drawable icon = parent.getDrawable(android.R.drawable.ic_dialog_alert).mutate();
        icon.setTint(parent.getResources().getColor(android.R.color.holo_red_light));

        AlertDialog ad = new AlertDialog.Builder(parent)
                .setTitle(parent.getResources().getString(android.R.string.dialog_alert_title))
                .setMessage(Html.fromHtml(message))
                .setIcon(icon)
                .setNegativeButton(android.R.string.ok, listener).create();

        ad.create(); //create all view elements
        ad.setIcon(icon);
        ((TextView) ad.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        ad.show();
    }
}
