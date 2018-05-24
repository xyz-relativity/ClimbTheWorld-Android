package com.ar.climbing.activitys;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.augmentedreality.AugmentedRealityUtils;
import com.ar.climbing.osm.OsmManager;
import com.ar.climbing.storage.AsyncDataManager;
import com.ar.climbing.storage.IDataManagerEventListener;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.DialogBuilder;
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
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NodesDataManagerActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener, IDataManagerEventListener, View.OnClickListener {
    private List<String> installedCountriesISO = new ArrayList<>();
    private Map<String, String[]> countryList = new TreeMap<>();
    private LayoutInflater inflater;
    private List<GeoNode> updates;
    private AsyncDataManager downloadManager;
    private Dialog loadingDialog;
    private Map<String, View> displayCountryMap = new HashMap<>();
    private String filterString = "";

    enum countryState {
        ADD,
        PROGRESS_BAR,
        REMOVE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);
        loadingDialog = DialogBuilder.buildLoadDialog(this,
                getResources().getString(R.string.loading_countries_message),
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        downloadManager = new AsyncDataManager(false);
        downloadManager.addObserver(this);

        localTab();

        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(this);

        EditText filter = findViewById(R.id.EditFilter);
        filter.addTextChangedListener(createTextChangeListener());
        loadCountryList();
    }

    private TextWatcher createTextChangeListener () {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                updateFilter(s.toString().toUpperCase());
            }
        };
    }

    private void updateFilter(String filter) {
        if (displayCountryMap.isEmpty()) {
            return;
        }

        filterString = filter;

        for (String[] country: countryList.values()) {
            if (displayCountryMap.containsKey(country[0])) {
                displayCountryMap.get(country[0]).setVisibility(getCountryVisibility(country));
            }
        }
    }

    private int getCountryVisibility(String[] country) {
        if (country[1].toUpperCase().contains(filterString)
                || country[0].toUpperCase().contains(filterString)) {
            return View.VISIBLE;
        } else {
            return View.GONE;
        }
    }

    private void loadCountryList() {
        InputStream is = getResources().openRawResource(R.raw.country_bbox);

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        try {
            reader.readLine(); //ignore headers
            String line;
            while ((line = reader.readLine()) != null) {
                String[] country = line.split(",");
                countryList.put(country[1], country);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri data = getIntent().getData();
        if (data != null) {
            if (data.getQueryParameter("tabID").equalsIgnoreCase("download")) {
                final BottomNavigationView bottomNavigationView;
                bottomNavigationView = findViewById(R.id.navigation);
                bottomNavigationView.postDelayed(new Runnable() {
                    public void run() {
                        bottomNavigationView.setSelectedItemId(R.id.navigation_download);
                    }
                }, 500);
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        View frameDownload = findViewById(R.id.downloadTab);
        View frameUpload = findViewById(R.id.uploadTab);
        View frameLocal = findViewById(R.id.localTab);

        switch (item.getItemId()) {
            case R.id.navigation_download:
                frameDownload.setVisibility(View.VISIBLE);
                frameUpload.setVisibility(View.GONE);
                frameLocal.setVisibility(View.GONE);
                downloadsTab();
                return true;
            case R.id.navigation_upload:
                frameDownload.setVisibility(View.GONE);
                frameUpload.setVisibility(View.VISIBLE);
                frameLocal.setVisibility(View.GONE);
                pushTab();
                return true;
            case R.id.navigation_local:
                frameDownload.setVisibility(View.GONE);
                frameUpload.setVisibility(View.GONE);
                frameLocal.setVisibility(View.VISIBLE);
                localTab();
                return true;
        }
        return false;
    }

    private View buildCountriesView(final ViewGroup tab, String[] country, final int visibility) {
        final String countryIso = country[0];
        String countryName = country[1];

        final View newViewElement = inflater.inflate(R.layout.country_list_element, tab, false);

        TextView textField = newViewElement.findViewById(R.id.itemID);
        textField.setText(countryName);

        textField = newViewElement.findViewById(R.id.selectText);
        textField.setText(countryName);

        if (installedCountriesISO.contains(countryIso)) {
            setViewState(countryState.REMOVE, newViewElement);
        }

        loadFlags(newViewElement);

        tab.post(new Runnable() {
            @Override
            public void run() {
                tab.addView(newViewElement);
                newViewElement.setVisibility(visibility);
            }
        });

        return newViewElement;
    }

    private void loadFlags(final View country) {
        TextView textField = country.findViewById(R.id.itemID);
        String countryName = textField.getText().toString();
        String countryIso = countryList.get(countryName)[0];

        ImageView img = country.findViewById(R.id.countryFlag);
        Bitmap flag = getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png");
        img.setImageBitmap(flag);

        img.getLayoutParams().width = (int) AugmentedRealityUtils.sizeToDPI(NodesDataManagerActivity.this, flag.getWidth());
        img.getLayoutParams().height = (int) AugmentedRealityUtils.sizeToDPI(NodesDataManagerActivity.this, flag.getHeight());
    }

    public void localTab() {
        loadingDialog.show();

        final ViewGroup tab = findViewById(R.id.localCountryView);
        tab.removeAllViews();

        (new Thread() {
            public void run() {
                installedCountriesISO = Globals.appDB.nodeDao().loadCountries();
                for (String[] country: countryList.values()) {
                    String countryIso = country[0];
                    if (installedCountriesISO.contains(countryIso)) {
                        buildCountriesView(tab, country, View.VISIBLE);
                    }
                }
                loadingDialog.dismiss();
            }
        }).start();
    }

    public void downloadsTab() {
        if (displayCountryMap.size() == 0) {
            showLoadingProgress(true);

            final ViewGroup tab = findViewById(R.id.countryView);
            tab.removeAllViews();

            (new Thread() {
                public void run() {
                    installedCountriesISO = Globals.appDB.nodeDao().loadCountries();
                    for (String[] country: countryList.values()) {
                        displayCountryMap.put(country[0], buildCountriesView(tab, country, getCountryVisibility(country)));
                    }
                    showLoadingProgress(false);
                }
            }).start();
        }
    }

    public void pushTab() {
        final ViewGroup tab = findViewById(R.id.changesView);
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

    @Override
    public void onClick(View v) {
        final List<Long> toChange = new ArrayList<>();

        switch (v.getId()) {
            case R.id.ButtonRevert: {
                aggregateSelectedItems((ViewGroup)findViewById(R.id.changesView), toChange);

                if (toChange.size() == 0) {
                    break;
                }

                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.revert_confirmation)
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
                aggregateSelectedItems((ViewGroup)findViewById(R.id.changesView), toChange);

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

            case R.id.countryAddButton:
            case R.id.countryDeleteButton:
            case R.id.countryRefreshButton: {
                countryClick(v);
            }
            break;
        }
    }

    private void countryClick (View v) {
        final View countryItem = (View) v.getParent().getParent();
        TextView textField = countryItem.findViewById(R.id.itemID);
        final String countryName = textField.getText().toString();
        final String[] country = countryList.get(countryName);
        switch (v.getId()) {
            case R.id.countryAddButton:
                (new Thread() {
                    public void run() {
                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.PROGRESS_BAR, countryItem);
                            }
                        });

                        fetchCountryData(country[0],
                                Double.parseDouble(country[5]),
                                Double.parseDouble(country[4]),
                                Double.parseDouble(country[3]),
                                Double.parseDouble(country[2]));

                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.REMOVE, countryItem);
                                if (displayCountryMap.containsKey(country[0])) {
                                    setViewState(countryState.REMOVE, displayCountryMap.get(country[0]));
                                }
                            }
                        });
                    }
                }).start();
                break;

            case R.id.countryDeleteButton:
                (new Thread() {
                    public void run() {
                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.PROGRESS_BAR, countryItem);
                            }
                        });

                        deleteCountryData(country[0]);

                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.ADD, countryItem);
                                if (displayCountryMap.containsKey(country[0])) {
                                    setViewState(countryState.ADD, displayCountryMap.get(country[0]));
                                }
                            }
                        });
                    }
                }).start();
                break;

            case R.id.countryRefreshButton:
                (new Thread() {
                    public void run() {
                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.PROGRESS_BAR, countryItem);
                            }
                        });

                        deleteCountryData(country[0]);
                        fetchCountryData(country[0],
                                Double.parseDouble(country[5]),
                                Double.parseDouble(country[4]),
                                Double.parseDouble(country[3]),
                                Double.parseDouble(country[2]));

                        runOnUiThread(new Thread() {
                            public void run() {
                                setViewState(countryState.REMOVE, countryItem);
                                if (displayCountryMap.containsKey(country[0])) {
                                    setViewState(countryState.REMOVE, displayCountryMap.get(country[0]));
                                }
                            }
                        });
                    }
                }).start();
                break;
        }
    }

    private void deleteCountryData (String countryIso) {
        List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(countryIso.toLowerCase());
        Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));
    }

    private void fetchCountryData (String countryIso, double north, double east, double south, double west) {
        Map<Long, GeoNode> nodes = new HashMap<>();
        downloadManager.getDataManager().downloadCountry(new BoundingBox(north, east, south, west),
                nodes,
                countryIso);
        downloadManager.getDataManager().pushToDb(nodes, true);
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
            if (Globals.oauthToken == null) {
                DialogBuilder.showErrorDialog(this, getString(R.string.oauth_failed), null);
            } else {
                pushToOsm();
            }
        }
    }

    private void pushToOsm() {
        Dialog progress = DialogBuilder.buildLoadDialog(this, getString(R.string.osm_preparing_data), null);
        progress.show();

        final List<Long> toChange = new ArrayList<>();
        aggregateSelectedItems((ViewGroup)findViewById(R.id.changesView), toChange);

        OsmManager osm = null;
        try {
            osm = new OsmManager(this);
        } catch (PackageManager.NameNotFoundException e) {
            DialogBuilder.showErrorDialog(this, getString(R.string.oauth_failed), null);
        }

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

    private void showLoadingProgress(final boolean show) {
        runOnUiThread(new Thread() {
            public void run() {
                if (show) {
                    findViewById(R.id.loadDialog).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.loadDialog).setVisibility(View.GONE);
                }
            }
        });

    }

    private static void setViewState(countryState state, View v) {
        View statusAdd = v.findViewById(R.id.selectStatusAdd);
        View statusProgress = v.findViewById(R.id.selectStatusProgress);
        View statusDel = v.findViewById(R.id.selectStatusDel);
        switch (state) {
            case ADD:
                statusAdd.setVisibility(View.VISIBLE);
                statusDel.setVisibility(View.GONE);
                statusProgress.setVisibility(View.GONE);
                break;
            case PROGRESS_BAR:
                statusAdd.setVisibility(View.GONE);
                statusDel.setVisibility(View.GONE);
                statusProgress.setVisibility(View.VISIBLE);
                break;
            case REMOVE:
                statusAdd.setVisibility(View.GONE);
                statusDel.setVisibility(View.VISIBLE);
                statusProgress.setVisibility(View.GONE);
                break;
        }
    }
}
