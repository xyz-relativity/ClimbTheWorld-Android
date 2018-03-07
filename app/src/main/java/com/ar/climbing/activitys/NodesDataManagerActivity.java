package com.ar.climbing.activitys;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TabHost;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.storage.download.INodesFetchingEventListener;
import com.ar.climbing.storage.download.NodesFetchingManager;
import com.ar.climbing.utils.Globals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodesDataManagerActivity extends AppCompatActivity implements TabHost.OnTabChangeListener, INodesFetchingEventListener {
    private static final String DOWNLOAD_TAB = "0";
    private static final String UPDATE_TAB = "1";
    private static final String PUSH_TAB = "2";

    private List<String> countryList = new ArrayList<>();
    private List<String> installedCountries = new ArrayList<>();
    private LayoutInflater inflater;
    private boolean doneLoading = true;

    private NodesFetchingManager downloadManager;

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

        downloadManager = new NodesFetchingManager(this);
        downloadManager.addObserver(this);
    }

    private void buildDownloadTab(final ViewGroup tab) {
        int id = 0;
        for (String country: countryList) {
            String[] elements = country.split(",");
            final String countryIso = elements[0];
            String flagIc = "flag_" + countryIso.toLowerCase();
            String countryName = elements[1];

            final View newViewElement = inflater.inflate(R.layout.country_select_button, tab, false);
            final Switch sw = newViewElement.findViewById(R.id.countrySwitch);
            sw.setText(countryName);
            sw.setId(id);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    final String[] country = countryList.get(buttonView.getId()).split(",");
                    if (isChecked && doneLoading) {
                        downloadManager.downloadBBox(Double.parseDouble(country[3]),
                                Double.parseDouble(country[2]),
                                Double.parseDouble(country[5]),
                                Double.parseDouble(country[4]),
                                new HashMap<Long, GeoNode>(),
                                countryIso);
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

            Resources resources = getResources();
            final int resourceId = resources.getIdentifier(flagIc, "drawable",
                    this.getPackageName());

            img.setImageResource(resourceId);

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
                List<GeoNode> updates = Globals.appDB.nodeDao().loadAllUpdatedNodes();

                for (GeoNode node : updates) {
                    final View newViewElement = inflater.inflate(R.layout.country_select_button, tab, false);
                    final Switch sw = newViewElement.findViewById(R.id.countrySwitch);
                    sw.setText(node.getName());

                    ImageView img = newViewElement.findViewById(R.id.countryFlag);

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
        if (progress == 100 && hasChanges) {
            Map nodes = (HashMap<Long, GeoNode>) results.get("data");
            downloadManager.pushToDb(nodes);
        }
    }
}
