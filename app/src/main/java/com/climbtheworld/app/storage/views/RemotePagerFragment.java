package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.IPagerViewFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import needle.UiRelatedTask;

public class RemotePagerFragment extends DataFragment implements IPagerViewFragment {

	class CountryAdapter extends BaseAdapter {
		private List<String> myList = new ArrayList<>();

		public CountryAdapter(List<String> installedCountries) {
		}

		// put below code (method) in Adapter class
		public void filter(String charText) {
			charText = charText.toLowerCase(Locale.getDefault());
			myList.clear();
			if (charText.length() == 0) {
				myList.addAll(countryMap.keySet());
			} else {
				for (String countryIso : countryMap.keySet()) {
					if (getCountryVisibility(countryMap.get(countryIso), charText.toUpperCase())) {
						myList.add(countryIso);
					}
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return myList.size();
		}

		@Override
		public Object getItem(int i) {
			return myList.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			String countryIso = myList.get(i);
			final CountryViewState country = countryMap.get(countryIso);
			country.listViewOrder = i;
			view = buildCountriesView(view, viewGroup, country);
			country.setViewState(country, view);

			return view;
		}

		private boolean getCountryVisibility(CountryViewState country, String filter) {
			return country.countryName.toUpperCase().contains(filter)
					|| country.countryISO.toUpperCase().contains(filter);
		}
	}

	public RemotePagerFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		super(parent, viewID, countryMap);

		downloadManager = new DataManager(parent);
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
		DownloadService.addListener(this);
	}

	@Override
	public void onDestroy(ViewGroup view) {
		DownloadService.removeListener(this);
	}

	@Override
	public void onViewSelected() {

	}

	private void downloadsTab() {
		listView = findViewById(R.id.countryView);

		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<List<String>>() {
					@Override
					protected List<String> doWork() {
						return Globals.appDB.nodeDao().loadCountriesIso();
					}

					@Override
					protected void thenDoUiRelatedWork(List<String> CountryList) {
						final CountryAdapter viewAdaptor = new CountryAdapter(CountryList);

						EditText filter = findViewById(R.id.EditFilter);
						viewAdaptor.filter(filter.getText().toString());
						filter.addTextChangedListener(new TextWatcher() {
							@Override
							public void beforeTextChanged(CharSequence s, int start, int count, int after) {

							}

							@Override
							public void onTextChanged(CharSequence s, int start, int before, int count) {

							}

							@Override
							public void afterTextChanged(Editable s) {
								viewAdaptor.filter(s.toString());
							}
						});

						listView.setAdapter(viewAdaptor);
					}
				});
	}
}
