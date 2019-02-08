package com.climbtheworld.app.utils.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.osm.MarkerUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class NodeDialogBuilder {
    private NodeDialogBuilder() {
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

        final AlertDialog ad = new AlertDialog.Builder(activity).create();
        ad.setCancelable(true);
        ad.setCanceledOnTouchOutside(true);
        ad.setTitle(poi.getName());

        Drawable nodeIcon = MarkerUtils.getPoiIcon(activity, poi);
        ad.setIcon(nodeIcon);

        StringBuilder alertMessage = new StringBuilder();
        if (GradeConverter.getConverter().isValidSystem(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem))) {
            alertMessage.append(activity.getResources().getString(R.string.grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem),
                    GradeConverter.getConverter().
                            getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG))));
        }

        if (!Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem).equalsIgnoreCase(Constants.STANDARD_SYSTEM)) {
            alertMessage.append("<br/>")
                    .append(activity.getResources().getString(R.string.grade,
                            Constants.STANDARD_SYSTEM,
                            GradeConverter.getConverter().
                                    getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId(GeoNode.KEY_GRADE_TAG))));
        }

        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.length_value, poi.getKey(GeoNode.KEY_LENGTH)));

        alertMessage.append("<br/>").append("<b>").append(activity.getResources().getString(R.string.climb_style)).append("</b>: ");
        String sepChr = "";
        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
            alertMessage.append(sepChr).append(activity.getResources().getString(style.getNameId()));
            sepChr = ", ";
        }

        alertMessage.append("<br/>").append("<b>")
                .append(activity.getResources().getString(R.string.description))
                .append("</b>").append(":<br/>").append(poi.getKey(GeoNode.KEY_DESCRIPTION).replace("\n", "<br/>"));

        StringBuilder website = new StringBuilder();
        try {
            URL url = new URL(poi.getWebsite());
            website.append("<a href=").append(url.toString()).append(">").append(url.getProtocol() + "://" + url.getAuthority() + (url.getPath().isEmpty()?"...":"")).append("</a>");
        } catch (MalformedURLException ignored) {
            website.append(poi.getWebsite());
        }

        alertMessage.append("<br/>").append("<b>")
                .append(activity.getResources().getString(R.string.website))
                .append("</b>: ").append(website);

        alertMessage.append("<br/>");

        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.distance_value, distance, displayDistUnits));
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.latitude_value,
                poi.decimalLatitude));
        alertMessage.append("<br/>").append(activity.getResources().getString(R.string.longitude_value,
                poi.decimalLongitude));
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
                Intent intent = new Intent(activity, EditNodeActivity.class);
                intent.putExtra("poiID", poi.getID());
                activity.startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
            }
        });

        ad.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.nav_share), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //add this so we have it in the list ov views.
            }
        });

        ad.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {

                Button button = ad.getButton(AlertDialog.BUTTON_NEGATIVE);
                button.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        //Creating the instance of PopupMenu
                        PopupMenu popup = new PopupMenu(activity, view);
                        popup.getMenuInflater().inflate(R.menu.nav_share_options, popup.getMenu());

                        //registering popup with OnMenuItemClickListener
                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                String urlFormat;

                                switch (item.getItemId()) {
                                    case R.id.navigate:
                                        urlFormat = String.format(Locale.getDefault(), "geo:0,0?q=%f,%f (%s)",
                                                poi.decimalLatitude,
                                                poi.decimalLongitude,
                                                poi.getName());
                                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                                Uri.parse(urlFormat));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        activity.startActivity(intent);
                                        break;

                                    case R.id.climbTheWorldUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "climbtheworld://map_view/node/%d",
                                                poi.getID());
                                        clipboard.setPrimaryClip(ClipData.newPlainText(poi.getName(), urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.openStreetMapUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/node/%d#map=19/%f/%f",
                                                poi.getID(),
                                                poi.decimalLatitude,
                                                poi.decimalLongitude);
                                        clipboard.setPrimaryClip(ClipData.newPlainText(poi.getName(), urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.googleMapsUrlLocation:
                                        //Docs: https://developers.google.com/maps/documentation/urls/guide#search-action
                                        urlFormat = String.format(Locale.getDefault(), "http://www.google.com/maps/place/%f,%f/@%f,%f,19z/data=!5m1!1e4",
                                                poi.decimalLatitude,
                                                poi.decimalLongitude,
                                                poi.decimalLatitude,
                                                poi.decimalLongitude);
                                        clipboard.setPrimaryClip(ClipData.newPlainText(poi.getName(), urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.geoUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "geo:%f,%f,%f",
                                                poi.decimalLatitude,
                                                poi.decimalLongitude,
                                                poi.elevationMeters);
                                        clipboard.setPrimaryClip(ClipData.newPlainText(poi.getName(), urlFormat));
                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;
                                }
                                return true;
                            }
                        });
                        popup.show();
                    }
                });
            }
        });

        ad.create();
        ((TextView)ad.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

        return ad;
    }
}
