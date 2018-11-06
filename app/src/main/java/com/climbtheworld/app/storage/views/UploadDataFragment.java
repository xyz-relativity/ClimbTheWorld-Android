package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.OAuthActivity;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.osm.MarkerUtils;
import com.climbtheworld.app.osm.OsmManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import needle.Needle;
import needle.UiRelatedProgressTask;

public class UploadDataFragment extends DataFragment implements IDataViewFragment, View.OnClickListener {

    private List<GeoNode> updates;

    public UploadDataFragment(Activity parent, @LayoutRes int viewID) {
        super(parent, viewID);
        downloadManager = new DataManager(false);
    }

    @Override
    public int getViewId() {
        return this.viewID;
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;
        findViewById(R.id.ButtonPush).setOnClickListener(this);
        findViewById(R.id.ButtonRevert).setOnClickListener(this);
        pushTab();
    }

    @Override
    public void onViewSelected() {

    }

    public void pushTab() {
        final ViewGroup tab = findViewById(R.id.changesView);
        tab.removeAllViews();

        Constants.DB_EXECUTOR
                .execute(new Runnable() {
            @Override
            public void run() {
                updates = Globals.appDB.nodeDao().loadAllUpdatedNodes();

                for (GeoNode node : updates) {
                    final View newViewElement = inflater.inflate(R.layout.list_item_topo, tab, false);

                    final CheckBox checkBox = newViewElement.findViewById(R.id.selectCheckBox);
                    checkBox.setText(node.getName());
                    ((TextView)newViewElement.findViewById(R.id.textViewStatus)).setText(getResources().getStringArray(R.array.route_update_status)[node.localUpdateState]);

                    TextView nodeID = newViewElement.findViewById(R.id.itemID);
                    nodeID.setText(String.valueOf(node.getID()));

                    ImageView img = newViewElement.findViewById(R.id.topoIcon);
                    Drawable nodeIcon = MarkerUtils.getPoiIcon(parent, node, MarkerGeoNode.POI_ICON_SIZE_MULTIPLIER);
                    img.setImageDrawable(nodeIcon);

                    Needle.onMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            checkBox.setChecked(true);
                            tab.addView(newViewElement);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        final List<Long> toChange = new ArrayList<>();

        switch (v.getId()) {
            case R.id.ButtonRevert: {
                aggregateSelectedItems((ViewGroup) findViewById(R.id.changesView), toChange);

                if (toChange.size() == 0) {
                    break;
                }

                new android.app.AlertDialog.Builder(parent)
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

                                Constants.DB_EXECUTOR
                                        .execute(new UiRelatedProgressTask<Boolean, String>() {
                                    @Override
                                    protected Boolean doWork() {
                                        Globals.appDB.nodeDao().updateNodes(undoDelete.toArray(new GeoNode[0]));
                                        updates.removeAll(undoDelete);
                                        Globals.appDB.nodeDao().deleteNodes(undoNew.toArray(new GeoNode[0]));
                                        updates.removeAll(undoNew);

                                        Map<Long, MarkerGeoNode> poiMap = new HashMap<>();
                                        List<Long> toUpdate = new ArrayList<>();
                                        for (GeoNode node : undoUpdates) {
                                            toUpdate.add(node.getID());
                                        }
                                        try {
                                            downloadManager.downloadIDs(toUpdate, poiMap);
                                        } catch (IOException | JSONException e) {
                                            publishProgress(e.getMessage());
                                            return false;
                                        }

                                        downloadManager.pushToDb(poiMap, true);
                                        updates.removeAll(undoUpdates);
                                        Globals.showNotifications(parent);

                                        return true;
                                    }

                                    @Override
                                    protected void thenDoUiRelatedWork(Boolean result) {
                                        if (result){
                                            pushTab();
                                        }
                                    }

                                    @Override
                                    protected void onProgressUpdate(String progress) {
                                        Toast.makeText(parent, parent.getResources().getString(R.string.exception_message,
                                                progress), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
            }
            break;

            case R.id.ButtonPush: {
                aggregateSelectedItems((ViewGroup) findViewById(R.id.changesView), toChange);

                if (toChange.size() == 0) {
                    break;
                }

                if (Globals.oauthToken == null) {
                    Intent intent = new Intent(parent, OAuthActivity.class);
                    parent.startActivityForResult(intent, Constants.OPEN_OAUTH_ACTIVITY);
                } else {
                    pushToOsm();
                }
            }
            break;
        }
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

    public void pushToOsm() {
        Dialog progress = DialogBuilder.buildLoadDialog(parent, parent.getString(R.string.osm_preparing_data), null);
        progress.show();

        final List<Long> toChange = new ArrayList<>();
        aggregateSelectedItems((ViewGroup)findViewById(R.id.changesView), toChange);

        OsmManager osm = null;
        try {
            osm = new OsmManager(parent);
        } catch (PackageManager.NameNotFoundException e) {
            DialogBuilder.showErrorDialog(parent, parent.getString(R.string.oauth_failed), null);
        }
        if (osm != null) {
            osm.pushData(toChange, progress);
        }
    }
}
