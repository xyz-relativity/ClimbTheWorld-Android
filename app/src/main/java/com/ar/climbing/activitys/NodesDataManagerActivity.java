package com.ar.climbing.activitys;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.osm.OsmManager;
import com.ar.climbing.storage.AsyncDataManager;
import com.ar.climbing.storage.IDataManagerEventListener;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import org.osmdroid.util.BoundingBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NodesDataManagerActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, IDataManagerEventListener {
    public static final String DOWNLOAD_TAB = "0";
    public static final String UPDATE_TAB = "1";
    public static final String PUSH_TAB = "2";

    private List<String> installedCountriesISO = new ArrayList<>();
    private List<String> countryList = new ArrayList<>();
    private LayoutInflater inflater;
    private List<GeoNode> updates;
    private AsyncDataManager downloadManager;
    private Dialog mOverlayDialog;
    private Map<String, View> countryDisplayMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);
        mOverlayDialog = buildLoadDialog(this, "Loading country list. Please wait.");
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        downloadManager = new AsyncDataManager(false);
        downloadManager.addObserver(this);

        downloadsTab();

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);

        EditText filter = findViewById(R.id.EditFilter);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (countryDisplayMap.isEmpty()) {
                    return;
                }

                for (String country: countryList) {
                    String countryIso = country.split(",")[0];
                    String countryName = country.split(",")[1];
                    if (countryName.toUpperCase().contains(s.toString().toUpperCase())
                            || countryIso.toUpperCase().contains(s.toString().toUpperCase())) {
                        countryDisplayMap.get(countryName).setVisibility(View.VISIBLE);
                    } else {
                        countryDisplayMap.get(countryName).setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.navigation_download:
                View frameDownload = findViewById(R.id.downloadTab);
                frameDownload.setVisibility(View.VISIBLE);
                frameDownload = findViewById(R.id.uploadTab);
                frameDownload.setVisibility(View.GONE);
                downloadsTab();
                return true;
            case R.id.navigation_upload:
                View frameUpload = findViewById(R.id.downloadTab);
                frameUpload.setVisibility(View.GONE);
                frameUpload = findViewById(R.id.uploadTab);
                frameUpload.setVisibility(View.VISIBLE);
                pushTab();
                return true;
        }
        return false;
    }

    private void buildDownloadTab(final ViewGroup tab, final List<String> countryList) {
        int countryId = 0;
        for (String country: countryList) {
            String[] elements = country.split(",");
            final String countryIso = elements[0];
            String countryName = elements[1];

            final View newViewElement = inflater.inflate(R.layout.country_list_element, tab, false);
            countryDisplayMap.put(countryName, newViewElement);
            newViewElement.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    TextView textField = v.findViewById(R.id.itemID);
                    final String[] country = countryList.get(Integer.parseInt(textField.getText().toString())).split(",");
                    ImageView statusAdd = v.findViewById(R.id.selectStatusAdd);
                    ImageView statusDel = v.findViewById(R.id.selectStatusDel);
                    if (statusAdd.isShown()) {
                        (new Thread() {
                            public void run() {
                                runOnUiThread(new Thread() {
                                    public void run() {
                                        setProgressStatus("progress", v);
                                    }
                                });

                                Map<Long, GeoNode> nodes = new HashMap<>();
                                downloadManager.getDataManager().downloadCountry(new BoundingBox(Double.parseDouble(country[5]),
                                                Double.parseDouble(country[4]),
                                                Double.parseDouble(country[3]),
                                                Double.parseDouble(country[2])),
                                        nodes,
                                        countryIso);
                                downloadManager.getDataManager().pushToDb(nodes, false);

                                runOnUiThread(new Thread() {
                                    public void run() {
                                        setProgressStatus("delete", v);
                                    }
                                });
                            }}).start();

                    }
                    if (statusDel.isShown()) {
                        (new Thread() {
                            public void run() {
                                runOnUiThread(new Thread() {
                                    public void run() {
                                        setProgressStatus("progress", v);
                                    }
                                });

                                List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(country[0].toLowerCase());
                                Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));

                                runOnUiThread(new Thread() {
                                    public void run() {
                                        setProgressStatus("add", v);
                                    }
                                });
                            }
                        }).start();
                    }
                }
            });

            TextView textField = newViewElement.findViewById(R.id.itemID);
            textField.setText(String.valueOf(countryId));

            textField = newViewElement.findViewById(R.id.selectText);
            textField.setText(countryName);

            ImageView img = newViewElement.findViewById(R.id.countryFlag);
            Bitmap flag = getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png");
            img.setImageBitmap(flag);

            img.getLayoutParams().width = (int) AugmentedRealityUtils.sizeToDPI(this, flag.getWidth());
            img.getLayoutParams().height = (int) AugmentedRealityUtils.sizeToDPI(this, flag.getHeight());

            if (installedCountriesISO.contains(countryIso)) {
                setProgressStatus("delete", newViewElement);
            }
            countryId++;

            runOnUiThread(new Thread() {
                public void run() {
                    tab.addView(newViewElement);
                }
            });
        }
    }

    private static void setProgressStatus(String status, View v) {
        ImageView statusAdd = v.findViewById(R.id.selectStatusAdd);
        ProgressBar statusProgress = v.findViewById(R.id.selectStatusProgress);
        ImageView statusDel = v.findViewById(R.id.selectStatusDel);
        if (status.equalsIgnoreCase("add")) {
            statusAdd.setVisibility(View.VISIBLE);
            statusDel.setVisibility(View.GONE);
            statusProgress.setVisibility(View.GONE);
        }

        if (status.equalsIgnoreCase("progress")) {
            statusAdd.setVisibility(View.GONE);
            statusDel.setVisibility(View.GONE);
            statusProgress.setVisibility(View.VISIBLE);
        }

        if (status.equalsIgnoreCase("delete")) {
            statusAdd.setVisibility(View.GONE);
            statusDel.setVisibility(View.VISIBLE);
            statusProgress.setVisibility(View.GONE);
        }
    }

    public void downloadsTab() {
        if (countryDisplayMap.size() == 0) {
            mOverlayDialog.show();

            final ViewGroup tab = findViewById(R.id.countryView);
            tab.removeAllViews();

            (new Thread() {
                public void run() {
                    installedCountriesISO = Globals.appDB.nodeDao().loadCountries();
                    InputStream is = getResources().openRawResource(R.raw.country_bbox);

                    BufferedReader reader = null;
                    reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

                    try {
                        reader.readLine(); //ignore headers
                        String line;
                        while ((line = reader.readLine()) != null) {
                            countryList.add(line);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    buildDownloadTab(tab, countryList);
                    mOverlayDialog.dismiss();
                }
            }).start();
        }
    }

    public void pushTab() {
        final ViewGroup tab = findViewById(R.id.tabView3);
        tab.removeAllViews();

        (new Thread() {
            public void run() {
                updates = Globals.appDB.nodeDao().loadAllUpdatedNodes();

                for (GeoNode node : updates) {
                    final View newViewElement = inflater.inflate(R.layout.topo_list_element, tab, false);
                    StringBuilder text = new StringBuilder();
                    text.append(node.getName())
                            .append("\n").append(getResources().getStringArray(R.array.route_update_status)[node.localUpdateState]);

                    final CheckBox checkBox = newViewElement.findViewById(R.id.selectCheckBox);
                    checkBox.setText(text);

                    TextView nodeID = newViewElement.findViewById(R.id.itemID);
                    nodeID.setText(String.valueOf(node.getID()));

                    ImageView img = newViewElement.findViewById(R.id.topoIcon);
                    Drawable nodeIcon = getResources().getDrawable(R.drawable.ic_topo_small);
                    nodeIcon.mutate(); //allow different effects for each marker.
                    nodeIcon.setTintList(Globals.gradeToColorState(node.getLevelId()));
                    nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

                    img.setImageDrawable(nodeIcon);

                    runOnUiThread(new Thread() {
                        public void run() {
                            checkBox.setChecked(true);
                            tab.addView(newViewElement);
                        }
                    });
                }
            }
        }).start();
    }

    public void onClick(View v) {
        final List<Long> toChange = new ArrayList<>();

        switch (v.getId()) {
            case R.id.ButtonRevert: {
                aggregateSelectedItems((ViewGroup)findViewById(R.id.tabView3), toChange);

                if (toChange.size() == 0) {
                    break;
                }

                new android.app.AlertDialog.Builder(this)
                        .setTitle(getResources().getString(R.string.revert_confirmation))
                        .setMessage(R.string.revert_confirmation_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                final List<GeoNode> undoNew = new ArrayList<>();
                                final List<GeoNode> undoDelete = new ArrayList<>();
                                final List<GeoNode> undoUpdates = new ArrayList<>();

                                for (GeoNode node : updates) {
                                    if (!toChange.contains(node.getID())) {
                                        continue;
                                    }
                                    if (node.localUpdateState == GeoNode.TO_DELETE_STATE && node.osmID >= 0) {
                                        node.localUpdateState = GeoNode.CLEAN_STATE;
                                        undoDelete.add(node);
                                    }
                                    if (node.localUpdateState == GeoNode.TO_UPDATE_STATE && node.osmID < 0) {
                                        undoNew.add(node);
                                    }
                                    if (node.localUpdateState == GeoNode.TO_UPDATE_STATE && node.osmID >= 0) {
                                        undoUpdates.add(node);
                                    }
                                }

                                updates.removeAll(undoNew);
                                updates.removeAll(undoDelete);
                                updates.removeAll(undoUpdates);

                                (new Thread() {
                                    public void run() {
                                        Globals.appDB.nodeDao().updateNodes(undoDelete.toArray(new GeoNode[undoDelete.size()]));
                                        Globals.appDB.nodeDao().deleteNodes(undoNew.toArray(new GeoNode[undoNew.size()]));

                                        Map<Long, GeoNode> poiMap = new HashMap<>();
                                        List<Long> toUpdate = new ArrayList<>();
                                        for (GeoNode node : undoUpdates) {
                                            toUpdate.add(node.getID());
                                        }
                                        downloadManager.getDataManager().downloadIDs(toUpdate, poiMap);
                                        downloadManager.getDataManager().pushToDb(poiMap, true);

                                        runOnUiThread(new Thread() {
                                            public void run() {
                                                pushTab();
                                            }
                                        });
                                    }
                                }).start();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
            }
            break;

            case R.id.ButtonPush: {
                aggregateSelectedItems((ViewGroup)findViewById(R.id.tabView3), toChange);

                if (toChange.size() == 0) {
                    break;
                }

                if (Globals.oauthToken == null) {
                    Intent intent = new Intent(NodesDataManagerActivity.this, OAuthActivity.class);
                    startActivityForResult(intent, Constants.OPEN_OAUTH_ACTIVITY);
                } else {
                    pushToOsm();
                }
            }
            break;
        }
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
            if (Globals.oauthToken == null) {
                Globals.showErrorDialog(this, getString(R.string.oauth_failed), null);
            } else {
                pushToOsm();
            }
        }
    }

    private void pushToOsm() {
        Dialog progress = buildLoadDialog(this, "Preparing local data.");
        progress.show();

        final List<Long> toChange = new ArrayList<>();
        aggregateSelectedItems((ViewGroup)findViewById(R.id.tabView3), toChange);

        OsmManager osm = new OsmManager(this);

        osm.pushData(toChange, progress);
    }

    private Bitmap getBitmapFromZip(final String imageFileInZip){
        Bitmap result = null;
        try {
            InputStream fis = getResources().openRawResource(R.raw.flags);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = null;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().equals(imageFileInZip)) {
                    result = BitmapFactory.decodeStream(zis);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private Dialog buildLoadDialog(Context context, String message) {
        Dialog mOverlayDialog = new Dialog(context);

        mOverlayDialog.setContentView(R.layout.dialog_loading);

        ((TextView)mOverlayDialog.getWindow().findViewById(R.id.dialogMessage)).setText(message);

        mOverlayDialog.setCancelable(true);
        mOverlayDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
        return mOverlayDialog;
    }

    private void aggregateSelectedItems(ViewGroup listView, List<Long> selectedList) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            CheckBox checkBox = child.findViewById(R.id.selectCheckBox);
            if (checkBox.isChecked()) {
                TextView nodeID = child.findViewById(R.id.itemID);
                selectedList.add(Long.parseLong(nodeID.getText().toString()));
            }
        }
    }
}
