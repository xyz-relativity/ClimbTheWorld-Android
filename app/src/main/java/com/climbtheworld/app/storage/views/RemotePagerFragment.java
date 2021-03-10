package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.views.FilteredListAdapter;
import com.climbtheworld.app.utils.views.IPagerViewFragment;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RemotePagerFragment extends DataFragment implements IPagerViewFragment {

	class CountryAdapter extends FilteredListAdapter<String> {

		public CountryAdapter(List<String> initialList) {
			super(initialList);
		}

		@Override
		protected boolean isVisible(int i, String filter) {
			String countryIso = (String)initialList.get(i);
			CountryViewState country = countryMap.get(countryIso);
			return country.countryName.toUpperCase().contains(filter)
					|| country.countryISO.toUpperCase().contains(filter);
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			String countryIso = (String)visibleList.get(i);
			final CountryViewState country = countryMap.get(countryIso);
			country.listViewOrder = i;
			view = buildCountriesView(view, viewGroup, country);
			country.setViewState(country, view);

			return view;
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

		final CountryAdapter viewAdaptor = new CountryAdapter(new LinkedList<>(countryMap.keySet()));

		EditText filter = findViewById(R.id.editFind);
		viewAdaptor.applyFilter(filter.getText().toString());
		filter.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				viewAdaptor.applyFilter(s.toString());
			}
		});

		listView.setAdapter(viewAdaptor);
	}
}
