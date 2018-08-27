package com.climbtheworld.app.storage.views;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.List;
import java.util.Map;

public class UploadDataFragment extends DataFragment implements IDataViewFragment {

    private List<GeoNode> updates;

    public UploadDataFragment(Activity parent, @LayoutRes int viewID, @IdRes int itemId) {
        super(parent, viewID, itemId);
    }

    @Override
    public int getViewId() {
        return this.viewID;
    }

    @Override
    public void onCreate(ViewGroup view) {
        this.view = view;
        pushTab();
    }

    @Override
    public void onViewSelected() {

    }

    @Override
    public void onProgress(int progress, boolean hasChanges, Map<String, Object> results) {

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

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            checkBox.setChecked(true);
                            tab.addView(newViewElement);
                        }
                    });
                }
            }
        }).start();
    }
}
