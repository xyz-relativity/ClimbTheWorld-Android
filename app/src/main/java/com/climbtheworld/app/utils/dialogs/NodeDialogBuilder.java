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
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
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

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.util.GeoPoint;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class NodeDialogBuilder {
    private NodeDialogBuilder() {
        //hide constructor
    }

    private static String getDistanceString(double distance) {
        String displayDistUnits;
        if (distance > 1000) {
            displayDistUnits = "km";
            distance = distance / 1000;
        } else {
            displayDistUnits = "m";
        }

        return String.format(Locale.getDefault(), "%.2f %s", distance, displayDistUnits);
    }

    private static void appendGradeString(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        if (GradeConverter.getConverter().isValidSystem(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem))) {
            appender.append(activity.getResources().getString(R.string.grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem),
                    GradeConverter.getConverter().
                            getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG))));
        }

        if (!Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem).equalsIgnoreCase(Constants.STANDARD_SYSTEM)) {
            appender.append("<br/>")
                    .append(activity.getResources().getString(R.string.grade,
                            Constants.STANDARD_SYSTEM,
                            GradeConverter.getConverter().
                                    getGradeFromOrder(Constants.STANDARD_SYSTEM, poi.getLevelId(GeoNode.KEY_GRADE_TAG))));
        }
    }

    private static void appendLengthString(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        String length = poi.getKey(GeoNode.KEY_LENGTH);

        if (!length.isEmpty()) {
            length = length + " m";
        }
        appender.append("<br/>").append(activity.getResources().getString(R.string.length_value, length));
    }

    private static void appendClimbingStyleString(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        appender.append("<b>").append(activity.getResources().getString(R.string.climb_style)).append("</b>: ");
        appender.append("<ul>");
        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
            appender.append("<li>").append(activity.getResources().getString(style.getNameId())).append("</li>");
        }
        appender.append("</ul>");
    }

    private static void appendDescriptionString(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        appender.append("<b>")
                .append(activity.getResources().getString(R.string.description))
                .append("</b>").append(":<br/>").append(poi.getKey(GeoNode.KEY_DESCRIPTION).replace("\n", "<br/>"));
    }

    private static void appendContactString(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        StringBuilder website = new StringBuilder();
        try {
            URL url = new URL(poi.getWebsite());
            website.append("<a href=").append(url.toString()).append(">").append(url.getProtocol() + "://" + url.getAuthority() + (url.getPath().isEmpty()?"...":"")).append("</a>");
        } catch (MalformedURLException ignored) {
            website.append(poi.getWebsite());
        }

        appender.append("<b>")
                .append(activity.getResources().getString(R.string.website))
                .append("</b>: ").append(website);
    }

    private static void appendGeoLocation(AppCompatActivity activity, GeoNode poi, StringBuilder appender) {
        double distance = poi.distanceMeters;

        if (Globals.virtualCamera != null) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
        }

        appender.append("<br/>").append(activity.getResources().getString(R.string.distance_value, getDistanceString(distance)));
        appender.append("<br/>").append(activity.getResources().getString(R.string.latitude_value,
                poi.decimalLatitude));
        appender.append("<br/>").append(activity.getResources().getString(R.string.longitude_value,
                poi.decimalLongitude));
        appender.append("<br/>").append(activity.getResources().getString(R.string.elevation_value, poi.elevationMeters));
    }

    private static View buildRouteDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        StringBuilder alertMessage = new StringBuilder();

        alertMessage.append("<html><body>");

        appendGradeString(activity, poi, alertMessage);

        appendLengthString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendClimbingStyleString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendDescriptionString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendContactString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendGeoLocation(activity, poi, alertMessage);

        alertMessage.append("</body></html>");

        WebView webView = new WebView(activity);

        webView.loadDataWithBaseURL(null, alertMessage.toString(), "text/html", "utf-8", null);
        webView.setScrollContainer(false);

//        alertDialog.setMessage(Html.fromHtml(alertMessage.toString())); //convert an html formatted string to html rendered text.

        return webView;
    }

    private static void buildGymDialog(AppCompatActivity activity, AlertDialog container, GeoNode poi) {
        StringBuilder alertMessage = new StringBuilder();

        appendGradeString(activity, poi, alertMessage);

        appendLengthString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendClimbingStyleString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendDescriptionString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendContactString(activity, poi, alertMessage);

        alertMessage.append("<br/>");

        appendGeoLocation(activity, poi, alertMessage);

        container.setMessage(Html.fromHtml(alertMessage.toString())); //convert an html formatted string to html rendered text.
    }

    private static View buildCragDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_crag, container, false);
        ((TextView)result.findViewById(R.id.editNumRoutes)).setText(poi.getKey(GeoNode.KEY_ROUTES));
        ((TextView)result.findViewById(R.id.editMinLength)).setText(poi.getKey(GeoNode.KEY_MIN_LENGTH));
        ((TextView)result.findViewById(R.id.editMaxLength)).setText(poi.getKey(GeoNode.KEY_MAX_LENGTH));

        ((TextView)result.findViewById(R.id.minGrading)).setText(activity.getResources()
                .getString(R.string.min_grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        ((TextView)result.findViewById(R.id.minGradeSpinner)).setText(GradeConverter.getConverter().
                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

        ((TextView)result.findViewById(R.id.minGradeSpinner)).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)).getDefaultColor());

        ((TextView)result.findViewById(R.id.maxGrading)).setText(activity.getResources()
                .getString(R.string.max_grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        ((TextView)result.findViewById(R.id.maxGradeSpinner)).setText(GradeConverter.getConverter().
                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

        ((TextView)result.findViewById(R.id.maxGradeSpinner)).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)).getDefaultColor());

        RadioGroup styles = result.findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
            TextView textView = new TextView(activity);
            textView.setText(style.getNameId());
            styles.addView(textView);
        }

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

        StringBuilder website = new StringBuilder();
        try {
            URL url = new URL(poi.getWebsite());
            website.append("<a href=").append(url.toString()).append(">").append(url.toString()).append("</a>");
        } catch (MalformedURLException ignored) {
            website.append(poi.getWebsite());
        }
        ((TextView)result.findViewById(R.id.editWebsite)).setText(Html.fromHtml(website.toString()));
        ((TextView)result.findViewById(R.id.editWebsite)).setMovementMethod(LinkMovementMethod.getInstance()); //activate links

        ((TextView)result.findViewById(R.id.editPhone)).setText(poi.getPhone());
        ((TextView)result.findViewById(R.id.editNo)).setText(poi.getKey(GeoNode.KEY_ADDR_STREETNO));
        ((TextView)result.findViewById(R.id.editStreet)).setText(poi.getKey(GeoNode.KEY_ADDR_STREET));
        ((TextView)result.findViewById(R.id.editUnit)).setText(poi.getKey(GeoNode.KEY_ADDR_UNIT));
        ((TextView)result.findViewById(R.id.editCity)).setText(poi.getKey(GeoNode.KEY_ADDR_CITY));
        ((TextView)result.findViewById(R.id.editProvince)).setText(poi.getKey(GeoNode.KEY_ADDR_PROVINCE));
        ((TextView)result.findViewById(R.id.editPostcode)).setText(poi.getKey(GeoNode.KEY_ADDR_POSTCODE));

        ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(poi.decimalLatitude));
        ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(poi.decimalLongitude));
        ((TextView)result.findViewById(R.id.editElevation)).setText(poi.getKey(GeoNode.KEY_ELEVATION));

        return result;
    }

    private static void addOkButton(AppCompatActivity activity, AlertDialog alertDialog) {
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
    }

    private static void addEditButton(final AppCompatActivity activity, final AlertDialog alertDialog, final long poiId) {
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(activity, EditNodeActivity.class);
                intent.putExtra("poiID", poiId);
                activity.startActivityForResult(intent, Constants.OPEN_EDIT_ACTIVITY);
            }
        });
    }

    private static void addNavigateButton(final AppCompatActivity activity, final AlertDialog alertDialog, final long osmId, final String name, final GeoPoint location) {
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.nav_share), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //add this so we have it in the list of views.
            }
        });

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {

                Button button = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
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
                                                location.getLatitude(),
                                                location.getLongitude(),
                                                name);
                                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                                Uri.parse(urlFormat));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        activity.startActivity(intent);
                                        break;

                                    case R.id.climbTheWorldUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "climbtheworld://map_view/node/%d",
                                                osmId);
                                        clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.openStreetMapUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/node/%d#map=19/%f/%f",
                                                osmId,
                                                location.getLatitude(),
                                                location.getLongitude());
                                        clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.googleMapsUrlLocation:
                                        //Docs: https://developers.google.com/maps/documentation/urls/guide#search-action
                                        urlFormat = String.format(Locale.getDefault(), "http://www.google.com/maps/place/%f,%f/@%f,%f,19z/data=!5m1!1e4",
                                                location.getLatitude(),
                                                location.getLongitude(),
                                                location.getLatitude(),
                                                location.getLongitude());
                                        clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));

                                        Toast.makeText(activity, activity.getResources().getString(R.string.location_copied),
                                                Toast.LENGTH_LONG).show();
                                        break;

                                    case R.id.geoUrlLocation:
                                        urlFormat = String.format(Locale.getDefault(), "geo:%f,%f,%f",
                                                location.getLatitude(),
                                                location.getLongitude(),
                                                location.getAltitude());
                                        clipboard.setPrimaryClip(ClipData.newPlainText(name, urlFormat));
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
    }

    public static AlertDialog buildNodeInfoDialog(final AppCompatActivity activity, final GeoNode poi) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(poi.getName());

        Drawable nodeIcon = MarkerUtils.getPoiIcon(activity, poi);
        alertDialog.setIcon(nodeIcon);

        switch (poi.getNodeType()) {
            case route:
                alertDialog.setView(buildRouteDialog(activity, alertDialog.getListView(), poi));
                break;
            case crag:
                alertDialog.setView(buildCragDialog(activity, alertDialog.getListView(), poi));
                break;
            case artificial:
                buildGymDialog(activity, alertDialog, poi);
                break;
            case unknown:
            default:
                alertDialog.setView(buildRouteDialog(activity, alertDialog.getListView(), poi));
                break;
        }

        addOkButton(activity, alertDialog);
        addEditButton(activity, alertDialog, poi.getID());
        addNavigateButton(activity, alertDialog, poi.osmID, poi.getName(), new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters));

        alertDialog.create();

        return alertDialog;
    }

    public static AlertDialog buildClusterDialog(final AppCompatActivity activity, final StaticCluster cluster) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(String.valueOf(cluster.getSize()));

        Drawable nodeIcon = cluster.getMarker().getIcon();
        alertDialog.setIcon(nodeIcon);

        addOkButton(activity, alertDialog);
        addNavigateButton(activity, alertDialog, 0, "", cluster.getPosition());

        alertDialog.create();

        return alertDialog;
    }
}