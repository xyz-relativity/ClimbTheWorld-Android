package com.climbtheworld.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.SearchResultsAdapter;
import com.climbtheworld.app.utils.views.TextWatchers;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import needle.UiRelatedTask;

public class SearchActivity extends AppCompatActivity {
	private static final int SEARCH_DEBOUNCE_MS = 300;

	UiRelatedTask<List<SearchResultsAdapter.Result>> dbExecutor = null;
	private final DataManager dataManager = new DataManager();
	private final List<SearchResultsAdapter.Result> searchResults = new ArrayList<>();
	private SearchResultsAdapter resultsAdapter;
	private ProgressBar progress;
	private View noMatch;
	private final Handler searchHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		progress = findViewById(R.id.progressbarSearching);
		noMatch = findViewById(R.id.findNoMatch);
		noMatch.setVisibility(View.GONE);
		initResultsList();

		((EditText) findViewById(R.id.editFind)).addTextChangedListener(
				TextWatchers.afterTextChanged(editable -> {
					searchHandler.removeCallbacks(pendingSearch);
					final String query = editable.toString().trim();
					pendingSearch = () -> doSearch(query);
					searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
				}));

		findViewById(R.id.editFind).requestFocus();
	}

	private void doSearch(final String searchFor) {
		if (searchFor.isEmpty()) {
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			updateUI(new ArrayList<>(), false);
		} else {
			noMatch.setVisibility(View.GONE);
			progress.setVisibility(View.VISIBLE);
			if (dbExecutor != null) {
				dbExecutor.cancel();
			}
			dbExecutor = new UiRelatedTask<List<SearchResultsAdapter.Result>>() {
				@Override
				protected List<SearchResultsAdapter.Result> doWork() {
					return SearchResultsAdapter.Result.load(SearchActivity.this,
							dataManager.find(SearchActivity.this, searchFor));
				}

				@Override
				protected void thenDoUiRelatedWork(List<SearchResultsAdapter.Result> result) {
					updateUI(result, true);
				}
			};

			Constants.DB_EXECUTOR
					.execute(dbExecutor);
		}
	}

	private void initResultsList() {
		resultsAdapter = new SearchResultsAdapter(this, searchResults,
				poi -> NodeDialogBuilder.showNodeInfoDialog(this, poi), null);
		((ListView) findViewById(R.id.listSearchResults)).setAdapter(resultsAdapter);
	}

	private void updateUI(final List<SearchResultsAdapter.Result> result, boolean showNoMatch) {
		searchResults.clear();
		searchResults.addAll(result);
		resultsAdapter.notifyDataSetChanged();
		noMatch.setVisibility(showNoMatch && searchResults.isEmpty() ? View.VISIBLE : View.GONE);
		progress.setVisibility(View.INVISIBLE);
		dbExecutor = null;
	}
}
