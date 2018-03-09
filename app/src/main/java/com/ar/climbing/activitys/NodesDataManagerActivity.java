package com.ar.climbing.activitys;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.storage.download.AsyncDataManager;
import com.ar.climbing.storage.download.IDataManagerEventListener;
import com.ar.climbing.utils.AugmentedRealityUtils;
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

public class NodesDataManagerActivity extends AppCompatActivity implements TabHost.OnTabChangeListener, IDataManagerEventListener {
    private static final String DOWNLOAD_TAB = "0";
    private static final String UPDATE_TAB = "1";
    private static final String PUSH_TAB = "2";

    private List<String> countryList = new ArrayList<>();
    private List<String> installedCountries = new ArrayList<>();
    private LayoutInflater inflater;
    private boolean doneLoading = true;
    List<GeoNode> updates;

    private AsyncDataManager downloadManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        downloadsTab();

        TabHost host = findViewById(R.id.tabHost);
        host.setup();

        //Tab 1
        TabHost.TabSpec spec = host.newTabSpec(DOWNLOAD_TAB);
        spec.setContent(R.id.tab1);
        spec.setIndicator(getResources().getStringArray(R.array.download_manager_section)[0]);
        host.addTab(spec);

        //Tab 2
        spec = host.newTabSpec(UPDATE_TAB);
        spec.setContent(R.id.tab2);
        spec.setIndicator(getResources().getStringArray(R.array.download_manager_section)[1]);
        host.addTab(spec);

        //Tab 3
        spec = host.newTabSpec(PUSH_TAB);
        spec.setContent(R.id.tab3);
        spec.setIndicator(getResources().getStringArray(R.array.download_manager_section)[2]);
        host.addTab(spec);

        host.setOnTabChangedListener(this);

        downloadManager = new AsyncDataManager(this);
        downloadManager.addObserver(this);
    }

    private void buildDownloadTab(final ViewGroup tab) {
        int id = 0;
        for (String country: countryList) {
            String[] elements = country.split(",");
            final String countryIso = elements[0];
            String countryName = elements[1];

            final View newViewElement = inflater.inflate(R.layout.country_list_element, tab, false);
            final Switch sw = newViewElement.findViewById(R.id.countrySwitch);
            sw.setText(countryName);
            sw.setId(id);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!doneLoading) {
                        return;
                    }

                    final String[] country = countryList.get(buttonView.getId()).split(",");
                    if (isChecked) {
                        (new Thread() {
                            public void run() {
                                Map<Long, GeoNode> pois = new HashMap<>();
                                downloadManager.getDataManager().downloadBBox(new BoundingBox(Double.parseDouble(country[5]),
                                                Double.parseDouble(country[4]),
                                                Double.parseDouble(country[3]),
                                                Double.parseDouble(country[2])),
                                        pois,
                                        countryIso);
                                downloadManager.getDataManager().pushToDb(pois, false);
                            }}).start();

                    } else {
                        (new Thread() {
                            public void run() {
                                List<GeoNode> countryNodes = Globals.appDB.nodeDao().loadNodesFromCountry(country[0].toLowerCase());
                                Globals.appDB.nodeDao().deleteNodes(countryNodes.toArray(new GeoNode[countryNodes.size()]));
                            }
                        }).start();
                    }
                }
            });

            ImageView img = newViewElement.findViewById(R.id.countryFlag);
            Bitmap flag = getBtimapFromZip("flag_" + countryIso.toLowerCase() + ".png");
            img.setImageBitmap(flag);

            img.getLayoutParams().width = (int) AugmentedRealityUtils.sizeToDPI(this, flag.getWidth());
            img.getLayoutParams().height = (int) AugmentedRealityUtils.sizeToDPI(this, flag.getHeight());

            if (installedCountries.contains(countryIso)) {
                runOnUiThread(new Thread() {
                    public void run() {
                        sw.setChecked(true);
                    }
                });
            }

            runOnUiThread(new Thread() {
                public void run() {
                    tab.addView(newViewElement);
                }
            });
            id++;
        }
    }

    private void downloadsTab() {
        doneLoading = false;
        final ViewGroup tab = findViewById(R.id.tabView1);
        tab.removeAllViews();

        (new Thread() {
            public void run() {
                installedCountries = Globals.appDB.nodeDao().loadCountries();

                String line = "";
                InputStream is = getResources().openRawResource(R.raw.country_bbox);

                BufferedReader reader = null;
                reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

                try {
                    reader.readLine(); //ignore headers
                    while ((line = reader.readLine()) != null) {
                        countryList.add(line);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                buildDownloadTab(tab);
                doneLoading = true;
            }
        }).start();
    }

    private void pushTab() {
        final ViewGroup tab = findViewById(R.id.tabView3);
        tab.removeAllViews();

        (new Thread() {
            public void run() {
                updates = Globals.appDB.nodeDao().loadAllUpdatedNodes();

                for (GeoNode node : updates) {
                    final View newViewElement = inflater.inflate(R.layout.topo_list_element, tab, false);
                    StringBuilder text = new StringBuilder();
                    text.append(node.getName())
                            .append("\n").append(getResources().getStringArray(R.array.topo_status)[node.localUpdateStatus]);

                    CheckBox checkBox = newViewElement.findViewById(R.id.topoCheckBox);
                    checkBox.setChecked(true);
                    checkBox.setText(text);

                    TextView nodeID = newViewElement.findViewById(R.id.topoID);
                    nodeID.setText(Long.toString(node.getID()));

                    ImageView img = newViewElement.findViewById(R.id.topoIcon);

                    img.setImageResource(R.drawable.ic_topo_small);

                    runOnUiThread(new Thread() {
                        public void run() {
                            tab.addView(newViewElement);
                        }
                    });
                }
            }
        }).start();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ButtonRevert:
                final List<GeoNode> undoNew = new ArrayList<>();
                final List<GeoNode> undoDelete = new ArrayList<>();
                final List<GeoNode> undoUpdates = new ArrayList<>();

                for (GeoNode node: updates) {
                    if (node.localUpdateStatus == GeoNode.TO_DELETE_STATE && node.osmID >= 0) {
                        node.localUpdateStatus = GeoNode.CLEAN_STATE;
                        undoDelete.add(node);
                    }
                    if (node.localUpdateStatus == GeoNode.TO_UPDATE_STATE && node.osmID < 0) {
                        undoNew.add(node);
                    }
                    if (node.localUpdateStatus == GeoNode.TO_UPDATE_STATE && node.osmID >= 0) {
                        undoUpdates.add(node);
                    }
                }

                updates.removeAll(undoNew);
                updates.removeAll(undoDelete);

                (new Thread() {
                    public void run() {
                        Globals.appDB.nodeDao().updateNodes(undoDelete.toArray(new GeoNode[undoDelete.size()]));
                        Globals.appDB.nodeDao().deleteNodes(undoNew.toArray(new GeoNode[undoNew.size()]));

                        Map<Long, GeoNode> poiMap = new HashMap<>();
                        List<Long> toUpdate = new ArrayList<>();
                        for (GeoNode node: undoUpdates) {
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

                break;
            case R.id.ButtonPush:
                break;
        }
    }

    @Override
    public void onTabChanged(String tabId) {
        switch (tabId) {
            case DOWNLOAD_TAB:
                downloadsTab();
                break;
            case PUSH_TAB:
                pushTab();
                break;
        }
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {
    }

    public Bitmap getBtimapFromZip(final String imageFileInZip){
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
}
