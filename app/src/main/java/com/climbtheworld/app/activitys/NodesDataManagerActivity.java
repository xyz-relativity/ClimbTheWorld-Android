package com.climbtheworld.app.activitys;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.osm.OsmManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.IDataViewFragment;
import com.climbtheworld.app.storage.views.LocalDataFragment;
import com.climbtheworld.app.storage.views.RemoteDataFragment;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodesDataManagerActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener,
        View.OnClickListener {
    private List<String> installedCountries = new ArrayList<>();
    private LayoutInflater inflater;
    private List<GeoNode> updates;

    private BottomNavigationView navigation;
    private List<IDataViewFragment> views = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);



        navigation = findViewById(R.id.dataNavigationBar);
        navigation.setOnNavigationItemSelectedListener(this);

        loadCountryList();

        views.add(new LocalDataFragment(this, R.layout.fragment_data_manager_loca_data, R.id.navigation_local));
        views.add(new RemoteDataFragment(this, R.layout.fragment_data_manager_remote_data, R.id.navigation_download));

        ViewPager viewPager = findViewById(R.id.dataContainerPager);
        viewPager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return views.size();
            }

            @Override
            public Object instantiateItem(ViewGroup collection, int position) {
                IDataViewFragment fragment = views.get(position);
                ViewGroup layout = (ViewGroup) inflater.inflate(fragment.getViewId(), collection, false);
                collection.addView(layout);
                fragment.onCreate(layout);
                return layout;
            }

            @Override
            public void destroyItem(ViewGroup collection, int position, Object view) {
                collection.removeView((View) view);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        });

//        localTab();
    }

    @Override
    protected void onStart() {
        Uri data = getIntent().getData();
        if (data != null) {
            if (data.getQueryParameter("tabID").equalsIgnoreCase("download")) {
                navigation.postDelayed(new Runnable() {
                    public void run() {
                        navigation.setSelectedItemId(R.id.navigation_download);
                    }
                }, 500);
            }
        }
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);
    }

    @Override
    protected void onPause() {
        Globals.onPause(this);

        super.onPause();
    }

    private void loadCountryList() {
        InputStream is = getResources().openRawResource(R.raw.country_bbox);

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        try {
            reader.readLine(); //ignore headers
            String line;
            while ((line = reader.readLine()) != null) {
                String[] country = line.split(",");
                DataFragment.sortedCountryList.add(country[0]);
                DataFragment.countryMap.put(country[0], country);
            }
        } catch (IOException e) {
            e.printStackTrace();
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
//                downloadsTab();
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
                return true;
        }
        return false;
    }

    public void pushTab() {
        final ViewGroup tab = findViewById(R.id.changesView);
        tab.removeAllViews();
        Globals.onResume(this);

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
//                                        downloadManager.getDataManager().downloadIDs(toUpdate, poiMap);
//                                        downloadManager.getDataManager().pushToDb(poiMap, true);

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
//                countryClick(v);
            }
            break;
        }
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
