package com.ar.climbing.activitys;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ProgressBar;
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

    private List<String> installedCountries = new ArrayList<>();
    private LayoutInflater inflater;
    private List<GeoNode> updates;
    private AsyncDataManager downloadManager;
    private Dialog mOverlayDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodes_data_manager);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        TabHost host = findViewById(R.id.tabHost);
        host.setup();

        mOverlayDialog = buildLoadDialog(this);

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

        downloadsTab();
    }

    private void buildDownloadTab(final ViewGroup tab, final List<String> countryList) {
        int id = 0;
        for (String country: countryList) {
            String[] elements = country.split(",");
            final String countryIso = elements[0];
            String countryName = elements[1];

            final View newViewElement = inflater.inflate(R.layout.country_list_element, tab, false);
            final Switch sw = newViewElement.findViewById(R.id.selectCheckBox);
            sw.setText(countryName);
            sw.setId(id);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    final String[] country = countryList.get(buttonView.getId()).split(",");
                    if (isChecked && !mOverlayDialog.isShowing()) {
                        (new Thread() {
                            public void run() {
                                Map<Long, GeoNode> nodes = new HashMap<>();
                                downloadManager.getDataManager().downloadBBox(new BoundingBox(Double.parseDouble(country[5]),
                                                Double.parseDouble(country[4]),
                                                Double.parseDouble(country[3]),
                                                Double.parseDouble(country[2])),
                                        nodes,
                                        countryIso);
                                downloadManager.getDataManager().pushToDb(nodes, false);
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
            Bitmap flag = getBitmapFromZip("flag_" + countryIso.toLowerCase() + ".png");
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
        mOverlayDialog.show();

        final ViewGroup tab = findViewById(R.id.tabView1);
        tab.removeAllViews();

        (new Thread() {
            public void run() {
                installedCountries = Globals.appDB.nodeDao().loadCountries();
                List<String> countryList = new ArrayList<>();

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
                mOverlayDialog.cancel();
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

                    final CheckBox checkBox = newViewElement.findViewById(R.id.selectCheckBox);
                    checkBox.setText(text);

                    TextView nodeID = newViewElement.findViewById(R.id.topoID);
                    nodeID.setText(String.valueOf(node.getID()));

                    ImageView img = newViewElement.findViewById(R.id.topoIcon);

                    img.setImageResource(R.drawable.ic_topo_small);

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
            }
            break;

            case R.id.ButtonUpdate: {
                aggregateSelectedItems((ViewGroup)findViewById(R.id.tabView2), toChange);
            }
            break;
        }
    }

    private void updatesTab() {
        final ViewGroup tab = findViewById(R.id.tabView2);
        tab.removeAllViews();
    }

    @Override
    public void onTabChanged(String tabId) {
        switch (tabId) {
            case DOWNLOAD_TAB:
                downloadsTab();
                break;
            case UPDATE_TAB:
                updatesTab();
                break;
            case PUSH_TAB:
                pushTab();
                break;
        }
    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {
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

    private Dialog buildLoadDialog(Context context) {
        Dialog mOverlayDialog = new Dialog(context);
        final ProgressBar loadingAnim = new ProgressBar(context);
        mOverlayDialog.setContentView(loadingAnim);
        mOverlayDialog.setCancelable(false);
        return mOverlayDialog;
    }

    private void aggregateSelectedItems(ViewGroup listView, List<Long> selectedList) {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            CheckBox checkBox = child.findViewById(R.id.selectCheckBox);
            if (checkBox.isChecked()) {
                TextView nodeID = child.findViewById(R.id.topoID);
                selectedList.add(Long.parseLong(nodeID.getText().toString()));
            }
        }
    }
}
