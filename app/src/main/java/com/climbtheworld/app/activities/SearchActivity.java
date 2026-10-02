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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class SearchActivity extends AppCompatActivity {
	private static final int SEARCH_DEBOUNCE_MS = 300;

	UiRelatedTask<List<DisplayableGeoNode>> dbExecutor = null;
	private final DataManagerNew dataManager = new DataManagerNew();
	private final List<DisplayableGeoNode> searchResults = new ArrayList<>();
	private BaseAdapter resultsAdapter;
	private ProgressBar progress;
	private View noMatch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		progress = findViewById(R.id.progressbarSearching);
		noMatch = findViewById(R.id.findNoMatch);
		noMatch.setVisibility(View.GONE);
		initResultsList();

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
				final String query = editable.toString().trim();
				workRunnable = () -> doSearch(query);
				handler.postDelayed(workRunnable, SEARCH_DEBOUNCE_MS);
			}
		});

		findViewById(R.id.editFind).requestFocus();
	}

	private void doSearch(final String searchFor) {
		if (searchFor.isEmpty()) {
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			updateUI(new ArrayList<DisplayableGeoNode>(), false);
		} else {
			noMatch.setVisibility(View.GONE);
			progress.setVisibility(View.VISIBLE);
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			dbExecutor = new UiRelatedTask<List<DisplayableGeoNode>>() {
				@Override
				protected List<DisplayableGeoNode> doWork() {
					List<DisplayableGeoNode> results = new ArrayList<>();
					for (OsmNode node : dataManager.find(SearchActivity.this, searchFor)) {
						results.add(DataManagerNew.toDisplayableNode(node));
					}
					return results;
				}

				@Override
				protected void thenDoUiRelatedWork(List<DisplayableGeoNode> result) {
					updateUI(result, true);
				}
			};

			Constants.DB_EXECUTOR
					.execute(dbExecutor);
		}
	}

	private void initResultsList() {
		resultsAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				return searchResults.size();
			}

			@Override
			public Object getItem(int i) {
				return searchResults.get(i);
			}

			@Override
			public long getItemId(int i) {
				return i;
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				final DisplayableGeoNode marker = searchResults.get(i);

				view = ListViewItemBuilder.getPaddedBuilder(SearchActivity.this, view, true)
						.setTitle(marker.getGeoNode().getName())
						.setDescription(DialogueUtils.buildDescription(SearchActivity.this,
								marker.getGeoNode()))
						.setIcon(new PoiMarkerDrawable(SearchActivity.this, marker))
						.build();

				view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						NodeDialogBuilder.showNodeInfoDialog(SearchActivity.this,
								marker.getGeoNode());
					}
				});

				((TextView) view.findViewById(R.id.itemID)).setText(
						String.valueOf(marker.getGeoNode().getID()));
				return view;
			}
		};
		((ListView) findViewById(R.id.listSearchResults)).setAdapter(resultsAdapter);
	}

	private void updateUI(final List<DisplayableGeoNode> result, boolean showNoMatch) {
		searchResults.clear();
		searchResults.addAll(result);
		resultsAdapter.notifyDataSetChanged();
		noMatch.setVisibility(showNoMatch && searchResults.isEmpty() ? View.VISIBLE : View.GONE);
		progress.setVisibility(View.INVISIBLE);
		dbExecutor = null;
	}
}
