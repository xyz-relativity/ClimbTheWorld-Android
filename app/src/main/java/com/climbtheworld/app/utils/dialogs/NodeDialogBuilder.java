package com.climbtheworld.app.utils.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
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
import com.climbtheworld.app.utils.ViewUtils;
import com.climbtheworld.app.widgets.MapViewWidget;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
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

    private static void setContactData(AppCompatActivity activity, View result, GeoNode poi) {
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
    }

    private static void setLocationData(AppCompatActivity activity, View result, GeoNode poi) {
        double distance = poi.distanceMeters;

        if (Globals.virtualCamera != null) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
        }

        ((TextView)result.findViewById(R.id.editDistance)).setText(getDistanceString(distance));

        ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(poi.decimalLatitude));
        ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(poi.decimalLongitude));
        ((TextView)result.findViewById(R.id.editElevation)).setText(poi.getKey(GeoNode.KEY_ELEVATION));
    }

    private static View buildRouteDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_route, container, false);

        ((TextView)result.findViewById(R.id.editLength)).setText(poi.getKey(GeoNode.KEY_LENGTH));

        ((TextView)result.findViewById(R.id.gradingTitle)).setText(activity.getResources()
                .getString(R.string.grade_system, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
        ((TextView)result.findViewById(R.id.gradeSpinner)).setText(GradeConverter.getConverter().
                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG)));

        ((TextView)result.findViewById(R.id.gradeSpinner)).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG)).getDefaultColor());

        RadioGroup styles = result.findViewById(R.id.radioGroupStyles);

        for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
            TextView textView = new TextView(activity);
            textView.setText(style.getNameId());
            styles.addView(textView);
        }

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

        setContactData(activity, result, poi);
        setLocationData(activity, result, poi);

        return result;
    }

    private static View buildArtificialDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_artificial, container, false);

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

        setContactData(activity, result, poi);
        setLocationData(activity, result, poi);

        if (poi.isArtificialTower()) {
            ((TextView)result.findViewById(R.id.editCentreType)).setText(R.string.artificial_tower);
        } else {
            ((TextView)result.findViewById(R.id.editCentreType)).setText(R.string.climbing_gym);
        }

        return result;
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

        setContactData(activity, result, poi);
        setLocationData(activity, result, poi);

        return result;
    }

    private static View buildUnknownDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_unknown, container, false);

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

        setContactData(activity, result, poi);

        double distance = poi.distanceMeters;

        if (Globals.virtualCamera != null) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
        }

        ((TextView)result.findViewById(R.id.editDistance)).setText(getDistanceString(distance));

        ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(poi.decimalLatitude));
        ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(poi.decimalLongitude));
        ((TextView)result.findViewById(R.id.editElevation)).setText(poi.getKey(GeoNode.KEY_ELEVATION));

        TableLayout table = result.findViewById(R.id.tableAllTags);

        int padding = (int)Globals.sizeToDPI(activity, 5);
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT, 1f);

        JSONObject tags = poi.getTags();
        Iterator<String> keyIt = tags.keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            TableRow row = new TableRow(activity);

            TextView item = new TextView(activity);
            item.setText(key);
            item.setBackground(ContextCompat.getDrawable(activity, R.drawable.cell_shape));
            item.setPadding(padding, padding, padding, padding);
            item.setLayoutParams(params);
            row.addView(item);
            item = new TextView(activity);
            item.setText(tags.optString(key));
            item.setBackground(ContextCompat.getDrawable(activity, R.drawable.cell_shape));
            item.setPadding(padding, padding, padding, padding);
            item.setLayoutParams(params);
            row.addView(item);

            table.addView(row);
        }

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
                alertDialog.setView(buildArtificialDialog(activity, alertDialog.getListView(), poi));
                break;
            case unknown:
            default:
                alertDialog.setView(buildUnknownDialog(activity, alertDialog.getListView(), poi));
                break;
        }

        addOkButton(activity, alertDialog);
        addEditButton(activity, alertDialog, poi.getID());
        addNavigateButton(activity, alertDialog, poi.osmID, poi.getName(), new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters));

        alertDialog.create();

        return alertDialog;
    }

    private static View buildMarkerDialog(final AppCompatActivity activity,
                                          final ViewGroup container,
                                          final StaticCluster cluster) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_cluster, container, false);

        GeoNode tmpPoi = new GeoNode(cluster.getPosition().getLatitude(), cluster.getPosition().getLongitude(), cluster.getPosition().getAltitude());
        double distance = tmpPoi.distanceMeters;

        if (Globals.virtualCamera != null) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, tmpPoi);
        }

        ((TextView)result.findViewById(R.id.editDistance)).setText(getDistanceString(distance));

        ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(tmpPoi.decimalLatitude));
        ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(tmpPoi.decimalLongitude));

        ListView itemsContainer = result.findViewById(R.id.listGroupItems);

        itemsContainer.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return cluster.getSize();
            }

            @Override
            public Object getItem(int i) {
                return i;
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                final MapViewWidget.GeoNodeMapMarker marker = (MapViewWidget.GeoNodeMapMarker)cluster.getItem(i);

                final View newViewElement = ViewUtils.buildCustomSwitch(activity,
                        marker.getGeoNode().getName(),
                        buildDescription(activity, marker.getGeoNode()),
                        null,
                        marker.getIcon());

                newViewElement.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NodeDialogBuilder.buildNodeInfoDialog(activity, marker.getGeoNode()).show();
                    }
                });

                ((TextView) newViewElement.findViewById(R.id.itemID)).setText(String.valueOf(marker.getId()));
                return newViewElement;
            }
        });
        return result;
    }

    private static String buildDescription(final AppCompatActivity activity, GeoNode poi) {
        StringBuilder appender = new StringBuilder();
        String sepChr = "";
        switch (poi.getNodeType()) {
            case route:
                for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
                    appender.append(sepChr).append(activity.getResources().getString(style.getNameId()));
                    sepChr = ", ";
                }

                appender.append("\n");

                appender.append(activity.getResources().getString(R.string.length)).append(": ").append(poi.getKey(GeoNode.KEY_LENGTH));
                break;
            case crag:
                for (GeoNode.ClimbingStyle style: poi.getClimbingStyles()) {
                    appender.append(sepChr).append(activity.getResources().getString(style.getNameId()));
                    sepChr = ", ";
                }
                appender.append("\n");

                appender.append(activity.getResources().getString(R.string.min_grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
                appender.append(": ").append(GradeConverter.getConverter().
                                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

                appender.append("\n");

                appender.append(activity.getResources().getString(R.string.max_grade, Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)));
                appender.append(": ").append(GradeConverter.getConverter().
                        getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

                break;
            case artificial:
                if (poi.isArtificialTower()) {
                    appender.append(activity.getResources().getString(R.string.artificial_tower));
                } else {
                    appender.append(activity.getResources().getString(R.string.climbing_gym));
                }
            default:
                appender.append("\n");
                appender.append(poi.getKey(GeoNode.KEY_DESCRIPTION));
                break;
        }

        return appender.toString();
    }

    public static AlertDialog buildClusterDialog(final AppCompatActivity activity, final StaticCluster cluster) {
        final AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(String.valueOf(cluster.getSize()));

        Drawable nodeIcon = cluster.getMarker().getIcon();
        alertDialog.setIcon(nodeIcon);

        alertDialog.setView(buildMarkerDialog(activity, alertDialog.getListView(), cluster));

        addOkButton(activity, alertDialog);
        addNavigateButton(activity, alertDialog, 0, String.valueOf(cluster.getSize()), cluster.getPosition());

        alertDialog.create();

        return alertDialog;
    }
}