package com.climbtheworld.app.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.EditNodeActivity;
import com.climbtheworld.app.activities.ViewMapActivity;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.configs.DisplayFilterFragment;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.GeoNodeMapMarker;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ListViewItemBuilder;
import com.climbtheworld.app.utils.Sorters;

import org.json.JSONObject;
import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.util.GeoPoint;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class NodeDialogBuilder {
    private static final int INFO_DIALOG_STYLE_ICON_SIZE = Math.round(Globals.convertDpToPixel(10));
    private NodeDialogBuilder() {
        //hide constructor
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

        ((TextView)result.findViewById(R.id.editDistance)).setText(Globals.getDistanceString(distance));

        ((TextView)result.findViewById(R.id.editLatitude)).setText(String.valueOf(poi.decimalLatitude));
        ((TextView)result.findViewById(R.id.editLongitude)).setText(String.valueOf(poi.decimalLongitude));
        ((TextView)result.findViewById(R.id.editElevation)).setText(Globals.getDistanceString(poi.getKey(GeoNode.KEY_ELEVATION), "m"));
    }

    private static void setClimbingStyle(AppCompatActivity parent, View result, GeoNode poi) {
        ViewGroup styles = result.findViewById(R.id.containerClimbingStylesView);

        for (GeoNode.ClimbingStyle styleName: Sorters.sortStyles(parent, poi.getClimbingStyles())) {
            View customView = ListViewItemBuilder.getNonPaddedBuilder(parent)
                    .setDescription(parent.getResources().getString(styleName.getNameId()))
                    .setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(styleName), INFO_DIALOG_STYLE_ICON_SIZE))
                    .build();

            styles.addView(customView);
        }
    }

    private static View buildRouteDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        Configs configs = Configs.instance(activity);
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_route, container, false);

        ((TextView)result.findViewById(R.id.editLength)).setText(Globals.getDistanceString(poi.getKey(GeoNode.KEY_LENGTH)));
        ((TextView)result.findViewById(R.id.editPitches)).setText(poi.getKey(GeoNode.KEY_PITCHES));
        ((TextView)result.findViewById(R.id.editBolts)).setText(poi.getKey(GeoNode.KEY_BOLTS));

        ((TextView)result.findViewById(R.id.gradingTitle)).setText(activity.getResources().getString(R.string.grade_system,
                        activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
        ((TextView)result.findViewById(R.id.gradeTextView)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG)));

        result.findViewById(R.id.gradeTextView).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG)).getDefaultColor());

        setClimbingStyle(activity, result, poi);

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
        Configs configs = Configs.instance(activity);
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_crag, container, false);
        ((TextView)result.findViewById(R.id.editNumRoutes)).setText(poi.getKey(GeoNode.KEY_ROUTES));
        ((TextView)result.findViewById(R.id.editMinLength)).setText(poi.getKey(GeoNode.KEY_MIN_LENGTH));
        ((TextView)result.findViewById(R.id.editMaxLength)).setText(poi.getKey(GeoNode.KEY_MAX_LENGTH));

        ((TextView)result.findViewById(R.id.minGrading)).setText(
                activity.getResources().getString(R.string.min_grade,
                        activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
        ((TextView)result.findViewById(R.id.minGradeSpinner)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

        ((TextView)result.findViewById(R.id.minGradeSpinner)).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)).getDefaultColor());

        ((TextView)result.findViewById(R.id.maxGrading)).setText(
                activity.getResources().getString(R.string.max_grade,
                        activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
        ((TextView)result.findViewById(R.id.maxGradeSpinner)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

        ((TextView)result.findViewById(R.id.maxGradeSpinner)).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)).getDefaultColor());

        setClimbingStyle(activity, result, poi);

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

        setContactData(activity, result, poi);
        setLocationData(activity, result, poi);

        return result;
    }

    private static View buildUnknownDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_unknown, container, false);

        ((TextView)result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));
        setLocationData(activity, result, poi);

        TableLayout table = result.findViewById(R.id.tableAllTags);

        int padding = (int)Globals.convertPixelsToDp(5);
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
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
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

    private static void addNavigateButton(final AppCompatActivity activity, final AlertDialog alertDialog, final long osmId, final String name, final GeoPoint location, final AlertDialog loadingDialog) {
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
                        popup.getMenuInflater().inflate(R.menu.dialog_nav_share_options, popup.getMenu());

                        //registering popup with OnMenuItemClickListener
                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                String urlFormat;

                                switch (item.getItemId()) {
                                    case R.id.centerLocation:
                                        DialogBuilder.closeAllDialogs();
                                        if (activity instanceof ViewMapActivity) {
                                            ((ViewMapActivity)activity).centerOnLocation(location);
                                        } else {
                                            Intent intent = new Intent(activity, ViewMapActivity.class);
                                            intent.putExtra("GeoPoint", location.toDoubleString());
                                            activity.startActivity(intent);
                                        }
                                        break;

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
                                        urlFormat = String.format(Locale.getDefault(), "climbtheworld://map_view/location/%s",
                                                location.toDoubleString());
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
                                        urlFormat = String.format(Locale.getDefault(), "https://www.google.com/maps/place/%f,%f/@%f,%f,19z/data=!5m1!1e4",
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

                View geolocButton = alertDialog.findViewById(R.id.showOnMapButton);
                if (geolocButton != null) {
                    geolocButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            DialogBuilder.closeAllDialogs();
                            if (activity instanceof ViewMapActivity) {
                                ((ViewMapActivity) activity).centerOnLocation(location);
                            } else {
                                Intent intent = new Intent(activity, ViewMapActivity.class);
                                intent.putExtra("GeoPoint", location.toDoubleString());
                                activity.startActivity(intent);
                            }
                        }
                    });
                }

                if (loadingDialog != null) {
                    loadingDialog.dismiss();
                }
            }
        });
    }

    public static void showNodeInfoDialog(final AppCompatActivity activity, final GeoNode poi) {
        final AlertDialog loading = DialogBuilder.buildLoadDialog(activity, activity.getResources().getString(R.string.loading_message), null);
        final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
        new AsyncTask<Void, Void, Void>() {
            protected void onPreExecute() {
                loading.show();
            }
            protected Void doInBackground(Void... unused) {
                alertDialog.setCancelable(true);
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.setTitle((!poi.getName().isEmpty() ? poi.getName():" "));

                Drawable nodeIcon = new PoiMarkerDrawable(activity, null, new DisplayableGeoNode(poi), 0, 0);
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
                addNavigateButton(activity, alertDialog, poi.osmID, poi.getName(), new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters), loading);

                return null;
            }
            protected void onPostExecute(Void unused) {
                alertDialog.create();
                alertDialog.show();
            }
        }.execute();
    }

    private static View buildClusterDialog(final AppCompatActivity activity,
                                           final ViewGroup container,
                                           final StaticCluster cluster) {
        View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_cluster, container, false);

        GeoNode tmpPoi = new GeoNode(cluster.getPosition().getLatitude(), cluster.getPosition().getLongitude(), cluster.getPosition().getAltitude());
        double distance = tmpPoi.distanceMeters;

        if (Globals.virtualCamera != null) {
            distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, tmpPoi);
        }

        ((TextView)result.findViewById(R.id.editDistance)).setText(Globals.getDistanceString(distance));

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
                final GeoNodeMapMarker marker = (GeoNodeMapMarker)cluster.getItem(i);

                view = ListViewItemBuilder.getPaddedBuilder(activity, view, true)
                        .setTitle(marker.getGeoNode().getName())
                        .setDescription(buildDescription(activity, marker.getGeoNode()))
                        .setIcon(marker.getIcon())
                        .build();

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NodeDialogBuilder.showNodeInfoDialog(activity, (marker.getGeoNode()));
                    }
                });

                ((TextView) view.findViewById(R.id.itemID)).setText(String.valueOf(marker.getId()));
                return view;
            }
        });
        return result;
    }

    public static String buildDescription(final AppCompatActivity parent, GeoNode poi) {
        Configs configs = Configs.instance(parent);
        StringBuilder appender = new StringBuilder();
        String sepChr = "";
        switch (poi.getNodeType()) {
            case route:
                for (GeoNode.ClimbingStyle style: Sorters.sortStyles(parent, poi.getClimbingStyles())) {
                    appender.append(sepChr).append(parent.getResources().getString(style.getNameId()));
                    sepChr = ", ";
                }

                appender.append("\n");

                appender.append(parent.getString(R.string.length)).append(": ").append(Globals.getDistanceString(poi.getKey(GeoNode.KEY_LENGTH)));
                break;
            case crag:
                for (GeoNode.ClimbingStyle style: Sorters.sortStyles(parent, poi.getClimbingStyles())) {
                    appender.append(sepChr).append(parent.getString(style.getNameId()));
                    sepChr = ", ";
                }
                appender.append("\n");

                appender.append(parent.getString(R.string.min_grade, configs.getString(Configs.ConfigKey.usedGradeSystem)));
                appender.append(": ").append(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

                appender.append("\n");

                appender.append(parent.getString(R.string.max_grade,
                        parent.getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
                appender.append(": ").append(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

                break;
            case artificial:
                if (poi.isArtificialTower()) {
                    appender.append(parent.getString(R.string.artificial_tower));
                } else {
                    appender.append(parent.getString(R.string.climbing_gym));
                }
            default:
                appender.append("\n");
                appender.append(poi.getKey(GeoNode.KEY_DESCRIPTION));
                break;
        }

        return appender.toString();
    }

    public static void showClusterDialog(final AppCompatActivity activity, final StaticCluster cluster) {
        final AlertDialog loading = DialogBuilder.buildLoadDialog(activity, activity.getResources().getString(R.string.loading_message), null);
        final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
        new AsyncTask<Void, Void, Void>() {
            protected void onPreExecute() {
                loading.show();
            }
            protected Void doInBackground(Void... unused) {
                alertDialog.setCancelable(true);
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.setTitle(activity.getResources().getString(R.string.points_of_interest_value, cluster.getSize()));

                Drawable nodeIcon = cluster.getMarker().getIcon();
                alertDialog.setIcon(nodeIcon);

                alertDialog.setView(buildClusterDialog(activity, alertDialog.getListView(), cluster));

                addOkButton(activity, alertDialog);
                addNavigateButton(activity, alertDialog, 0, String.valueOf(cluster.getSize()), cluster.getPosition(), loading);

                return null;
            }
            protected void onPostExecute(Void unused) {
                alertDialog.create();
                alertDialog.show();
            }
        }.execute();
    }

    private static View buildFilterDialog(final AppCompatActivity activity,
                                          final ViewGroup container) {
        ScrollView wrapper = new ScrollView(activity);
        wrapper.addView(activity.getLayoutInflater().inflate(R.layout.fragment_settings_node_filter, container, false));
        wrapper.setVerticalScrollBarEnabled(true);
        wrapper.setHorizontalScrollBarEnabled(false);
        return wrapper;
    }

    public static void showFilterDialog(final AppCompatActivity activity, DisplayFilterFragment.OnFilterChangeListener listener) {
        final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
        alertDialog.setCancelable(true);
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.setTitle(activity.getResources().getString(R.string.filter));

        alertDialog.setIcon(R.drawable.ic_filter);

        View view = buildFilterDialog(activity, alertDialog.getListView());

        final DisplayFilterFragment filter = new DisplayFilterFragment(activity, view);
        filter.addListener(listener);

        alertDialog.setView(view);

        addOkButton(activity, alertDialog);
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.reset_filters), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                filter.reset();
            }
        });

        alertDialog.create();
        alertDialog.show();
    }
}