package com.climbtheworld.app.utils.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.RemoteDataFragment;
import com.climbtheworld.app.utils.Globals;

/**
 * Created by xyz on 1/4/18.
 */

public class DialogBuilder {
    private DialogBuilder() {
        //hide constructor
    }

    public static AlertDialog buildObserverInfoDialog(View v) {
        int azimuthID = (int) Math.floor(Math.abs(Globals.virtualCamera.degAzimuth - 11.25) / 22.5);

        AlertDialog ad = new AlertDialog.Builder(v.getContext()).create();
        ad.setCancelable(true);
        ad.setTitle(v.getResources().getString(R.string.local_coordinate));
        ad.setIcon(R.drawable.person);

        StringBuilder alertMessage = new StringBuilder();
        alertMessage.append(v.getResources().getString(R.string.latitude_value,
                Globals.virtualCamera.decimalLatitude));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.longitude_value,
                Globals.virtualCamera.decimalLongitude));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.elevation_value, Globals.virtualCamera.elevationMeters));
        alertMessage.append("<br/>").append(v.getResources().getString(R.string.azimuth_value, v.getResources().getStringArray(R.array.cardinal_names)[azimuthID], Globals.virtualCamera.degAzimuth));

        ad.setMessage(Html.fromHtml(alertMessage.toString()));
        ad.setButton(DialogInterface.BUTTON_POSITIVE, v.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        ad.create();
        return ad;
    }

    public static AlertDialog buildDownloadRegionAlert(final AppCompatActivity activity) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(R.string.tutorial_region_download_title);
        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);

        Globals.loadCountryList();

        ViewGroup result = (ViewGroup)activity.getLayoutInflater().inflate(R.layout.fragment_dialog_download, alertDialog.getListView(), false);
        RemoteDataFragment downloadView = new RemoteDataFragment(activity, R.layout.fragment_data_manager_remote_data);
        downloadView.onCreate(result);

        alertDialog.setView(result);

        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        alertDialog.create();

        return alertDialog;
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
        mOverlayDialog.create();
        return mOverlayDialog;
    }

    public static void showErrorDialog(final Context parent, final String message, final DialogInterface.OnClickListener listener) {
        AlertDialog ad = new AlertDialog.Builder(parent)
                .setTitle(parent.getResources().getString(android.R.string.dialog_alert_title))
                .setMessage(Html.fromHtml(message))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNegativeButton(android.R.string.ok, listener).create();
        ad.create(); //create all view elements
        ((TextView) ad.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        ad.show();
    }
}
