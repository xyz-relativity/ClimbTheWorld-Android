package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.dialogs.NodeDialogBuilder;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import needle.UiRelatedTask;

public class FindActivity extends AppCompatActivity {
    UiRelatedTask<List<GeoNode>> dbExecutor = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find);

        ((EditText)findViewById(R.id.editFind)).addTextChangedListener(new TextWatcher() {
            Handler handler = new Handler(Looper.getMainLooper() /*UI thread*/);
            Runnable workRunnable;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                handler.removeCallbacks(workRunnable);
                workRunnable = () -> doSearch(editable.toString());
                handler.postDelayed(workRunnable, 1000 /*delay*/);
            }
        });
    }

    private void doSearch(final String searchFor) {
        if (searchFor.isEmpty()) {
            if (dbExecutor != null) {
                dbExecutor.cancel();
            }
            updateUI(new ArrayList<GeoNode>());
        } else {
            ((ProgressBar) findViewById(R.id.progressbarSearching)).setVisibility(View.VISIBLE);
            if (dbExecutor != null) {
                dbExecutor.cancel();
            }
            dbExecutor = new UiRelatedTask<List<GeoNode>>() {
                @Override
                protected List<GeoNode> doWork() {
                    return Globals.appDB.nodeDao().find(searchFor);
                }

                @Override
                protected void thenDoUiRelatedWork(List<GeoNode> result) {
                    updateUI(result);
                }
            };

            Constants.DB_EXECUTOR
                    .execute(dbExecutor);
        }
    }

    private void updateUI(final List<GeoNode> result) {
        ListView itemsContainer = findViewById(R.id.listSearchResults);

        itemsContainer.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return result.size();
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
                final GeoNode marker = result.get(i);

                view = ListViewItemBuilder.getPaddedBuilder(FindActivity.this, view, true)
                        .setTitle(marker.getName())
                        .setDescription(NodeDialogBuilder.buildDescription(FindActivity.this, marker))
                        .setIcon(new PoiMarkerDrawable(FindActivity.this, null, new DisplayableGeoNode(marker), 0, 0))
                        .build();

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NodeDialogBuilder.showNodeInfoDialog(FindActivity.this, (marker));
                    }
                });

                ((TextView) view.findViewById(R.id.itemID)).setText(String.valueOf(marker.getID()));
                return view;
            }
        });
        itemsContainer.invalidate();
        ((ProgressBar)findViewById(R.id.progressbarSearching)).setVisibility(View.INVISIBLE);
        dbExecutor = null;
    }
}