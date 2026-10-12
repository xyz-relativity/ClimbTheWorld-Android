package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.services.DownloadService;

import java.util.List;
import java.util.Map;

public class LocalPagerFragment extends DataFragment {

	public LocalPagerFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		super(parent, viewID, countryMap);

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
				.setText(parent.get().getString(R.string.no_local_data, parent.get().getString(R.string.download_manager_downloads)));

		listView = findViewById(R.id.localCountryView);
		findViewById(R.id.noLocalDataContainer).setVisibility(View.GONE);
		refreshInstalledCountries();
		DownloadService.addListener(this);
	}

	@Override
	public void onDestroy(ViewGroup view) {
		DownloadService.removeListener(this);
	}

	/** A delete only refreshes the tab it was made in, so this one may be behind. */
	@Override
	public void onViewSelected() {
		refreshInstalledCountries();
	}

	@Override
	protected void onInstalledCountriesLoaded(List<String> installedCountries) {
		findViewById(R.id.noLocalDataContainer)
				.setVisibility(installedCountries.isEmpty() ? View.VISIBLE : View.GONE);

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
				country.setViewState(country, view);
				return view;
			}
		});
	}
}
