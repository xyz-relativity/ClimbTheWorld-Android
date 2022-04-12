package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.constants.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public class LocalPagerFragment extends DataFragment {

	public LocalPagerFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		super(parent, viewID, countryMap);

		downloadManager = new DataManager();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		//do nothing
	}

	@Override
	public @LayoutRes
	int getViewId() {
		return this.viewID;
	}

	@Override
	public void onCreate(final ViewGroup view) {
		this.view = view;

		((TextView) findViewById(R.id.noLocalDataText))
				.setText(parent.getString(R.string.no_local_data, parent.getString(R.string.download_manager_downloads)));

		localTab();
		DownloadService.addListener(this);
	}

	@Override
	public void onDestroy(ViewGroup view) {
		DownloadService.removeListener(this);
	}

	@Override
	public void onViewSelected() {

	}

	public void localTab() {
		listView = findViewById(R.id.localCountryView);
		findViewById(R.id.noLocalDataContainer).setVisibility(View.GONE);

		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<List<String>>() {

					@Override
					protected List<String> doWork() {
						return AppDatabase.getInstance(parent).nodeDao().loadCountriesIso();
					}

					@Override
					protected void thenDoUiRelatedWork(List<String> dbCountries) {
						dbCountries.remove("");

						final List<String> installedCountries = new ArrayList<>();

						for (final String countryKey : countryMap.keySet()) {
							if (dbCountries.contains(countryMap.get(countryKey).countryISO)) {
								installedCountries.add(countryKey);
								countryMap.get(countryKey).countryState = CountryState.REMOVE;
							}
						}

						if (installedCountries.size() <= 0) {
							findViewById(R.id.noLocalDataContainer).setVisibility(View.VISIBLE);
						} else {
							findViewById(R.id.noLocalDataContainer).setVisibility(View.GONE);
						}

						listView.setAdapter(new BaseAdapter() {
							@Override
							public int getCount() {
								return installedCountries.size();
							}

							@Override
							public Object getItem(int i) {
								return installedCountries.get(i);
							}

							@Override
							public long getItemId(int i) {
								return i;
							}

							@Override
							public View getView(int i, View view, ViewGroup viewGroup) {
								String countryKey = installedCountries.get(i);
								final CountryViewState country = countryMap.get(countryKey);

								view = buildCountriesView(view, viewGroup, country);
								country.listViewOrder = i;
								country.setViewState(country, view);
								return view;
							}
						});
					}
				});
	}
}
