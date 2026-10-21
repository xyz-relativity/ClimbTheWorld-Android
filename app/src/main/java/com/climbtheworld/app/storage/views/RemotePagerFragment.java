package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.views.FilteredListAdapter;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RemotePagerFragment extends DataFragment implements IPagerViewFragment {
	// Section titles share the list with ISO codes, which never start with '#'.
	private static final String TITLE_NEARBY = "#nearby";
	private static final String TITLE_ALL = "#all";
	private static final int TYPE_COUNTRY = 0;
	private static final int TYPE_TITLE = 1;

	class CountryAdapter extends FilteredListAdapter<String> {
		private final List<String> nearbyCountries;

		public CountryAdapter(List<String> initialList, List<String> nearbyCountries) {
			super(initialList);
			this.nearbyCountries = nearbyCountries;
		}

		/**
		 * Without a filter, the countries near the user come first under their own title, and
		 * also stay in their place in the full list.
		 */
		@Override
		public void applyFilter(String charText) {
			super.applyFilter(charText);
			if (charText.isEmpty() && !nearbyCountries.isEmpty()) {
				List<String> nearbySection = new ArrayList<>();
				nearbySection.add(TITLE_NEARBY);
				nearbySection.addAll(nearbyCountries);
				nearbySection.add(TITLE_ALL);
				visibleList.addAll(0, nearbySection);
				notifyDataSetChanged();
			}
		}

		private boolean isTitle(String item) {
			return TITLE_NEARBY.equals(item) || TITLE_ALL.equals(item);
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int i) {
			return isTitle(visibleList.get(i)) ? TYPE_TITLE : TYPE_COUNTRY;
		}

		@Override
		public boolean areAllItemsEnabled() {
			return false;
		}

		@Override
		public boolean isEnabled(int i) {
			return !isTitle(visibleList.get(i));
		}

		@Override
		protected boolean isVisible(int i, String filter) {
			String countryIso = initialList.get(i);
			CountryViewState country = countryMap.get(countryIso);
			return country != null && (country.countryName.toLowerCase().contains(filter)
					|| country.englishName.toLowerCase().contains(filter)
					|| country.countryISO.toLowerCase().contains(filter));
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			String countryIso = visibleList.get(i);
			if (isTitle(countryIso)) {
				TextView title = view != null ? (TextView) view : (TextView) inflater.inflate(
						R.layout.list_item_section_title, viewGroup, false);
				title.setText(TITLE_NEARBY.equals(countryIso)
						? R.string.download_nearby_countries : R.string.download_all_countries);
				return title;
			}
			final CountryViewState country = countryMap.get(countryIso);
			if (country == null) return view;
			View newView = buildCountriesView(view, viewGroup, country);
			country.setViewState(country, newView);

			return newView;
		}
	}

	public RemotePagerFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		super(parent, viewID, countryMap);

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		//do Nothing
	}

	@Override
	public int getViewId() {
		return this.viewID;
	}

	@Override
	public void onCreate(ViewGroup view) {
		this.view = view;

		downloadsTab();
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

	private void downloadsTab() {
		listView = findViewById(R.id.countryView);

		final CountryAdapter viewAdaptor = new CountryAdapter(new LinkedList<>(countryMap.keySet()),
				NearbyCountries.find(appContext, countryMap));

		EditText filter = findViewById(R.id.editFind);
		viewAdaptor.applyFilter(filter.getText().toString());
		filter.addTextChangedListener(TextWatchers.afterTextChanged(s -> viewAdaptor.applyFilter(s.toString())));

		listView.setAdapter(viewAdaptor);
	}
}
