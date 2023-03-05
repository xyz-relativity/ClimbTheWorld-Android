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

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class SearchActivity extends AppCompatActivity {
	UiRelatedTask<List<GeoNode>> dbExecutor = null;
	private ProgressBar progress;
	private View noMatch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		progress = findViewById(R.id.progressbarSearching);
		noMatch = findViewById(R.id.findNoMatch);

		((EditText) findViewById(R.id.editFind)).addTextChangedListener(new TextWatcher() {
			final Handler handler = new Handler(Looper.getMainLooper() /*UI thread*/);
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

		findViewById(R.id.editFind).requestFocus();
	}

	private void doSearch(final String searchFor) {
		if (searchFor.isEmpty()) {
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			updateUI(new ArrayList<GeoNode>());
		} else {
			noMatch.setVisibility(View.GONE);
			progress.setVisibility(View.VISIBLE);
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			dbExecutor = new UiRelatedTask<List<GeoNode>>() {
				@Override
				protected List<GeoNode> doWork() {
					return AppDatabase.getInstance(SearchActivity.this).nodeDao().find(searchFor);
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

				view = ListViewItemBuilder.getPaddedBuilder(SearchActivity.this, view, true)
						.setTitle(marker.getName())
						.setDescription(DialogueUtils.buildDescription(SearchActivity.this, marker))
						.setIcon(new PoiMarkerDrawable(SearchActivity.this, null, new DisplayableGeoNode(marker), 0, 0))
						.build();

				view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						NodeDialogBuilder.showNodeInfoDialog(SearchActivity.this, (marker));
					}
				});

				((TextView) view.findViewById(R.id.itemID)).setText(String.valueOf(marker.getID()));
				return view;
			}
		});
		itemsContainer.invalidate();
		if (itemsContainer.getCount() == 0) {
			noMatch.setVisibility(View.VISIBLE);
		}
		progress.setVisibility(View.INVISIBLE);
		dbExecutor = null;
	}
}