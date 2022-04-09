package com.climbtheworld.app.storage.views;

import android.content.Intent;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.services.DownloadProgressListener;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.IPagerViewFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import needle.UiRelatedTask;

public abstract class DataFragment implements DownloadProgressListener, IPagerViewFragment {
	public enum CountryState {
		ADD,
		WAITING,
		PROGRESS,
		REMOVE
	}

	protected final Configs configs;
	final AppCompatActivity parent;
	Map<String, CountryViewState> countryMap; //ConcurrentSkipListMap<>();
	@LayoutRes
	int viewID;
	ViewGroup view;
	LayoutInflater inflater;
	DataManager downloadManager;
	ListView listView;

	DataFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		this.parent = parent;
		this.viewID = viewID;
		this.countryMap = countryMap;

		inflater = parent.getLayoutInflater();
		configs = Configs.instance(parent);
	}

	@Override
	public void onProgressChanged(String eventOwner, int statusEvent) {
		CountryViewState country = countryMap.get(eventOwner);
		if (country == null) { //this country is not visible in this observer
			return;
		}

		switch (statusEvent) {
			case DownloadProgressListener.STATUS_WAITING:
				country.countryState = CountryState.WAITING;
				break;

			case DownloadProgressListener.STATUS_DONE:
				country.countryState = CountryState.REMOVE;
				break;

			case DownloadProgressListener.STATUS_ERROR:
				country.countryState = CountryState.ADD;
				break;

			default:
				country.countryState = CountryState.PROGRESS;
				country.progress = statusEvent;
		}

		listViewNotifyDataChange();
	}

	private void listViewNotifyDataChange() {
		BaseAdapter adapter = (BaseAdapter)listView.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	View buildCountriesView(View view, final ViewGroup tab, CountryViewState country) {
		if (view == null) {
			view = inflater.inflate(R.layout.list_item_country, tab, false);
		}

		view.findViewById(R.id.countryAddButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onAddRefresh(country);
			}
		});
		view.findViewById(R.id.countryDeleteButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onDeleteEvent(country);
			}
		});
		view.findViewById(R.id.countryRefreshButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				onAddRefresh(country);
			}
		});

		TextView textField = view.findViewById(R.id.selectText);
		textField.setText(country.countryName);

		ImageView flag = view.findViewById(R.id.countryFlag);
		flag.setTag(country.countryISO);
		country.setFlag(flag, parent);
		return view;
	}

	private void onDeleteEvent(CountryViewState country) {
		country.countryState = CountryState.PROGRESS;
		listViewNotifyDataChange();

		Constants.WEB_EXECUTOR
				.execute(new UiRelatedTask<CountryViewState>() {
					@Override
					protected CountryViewState doWork() {
						deleteCountryData(country.countryISO);
						return country;
					}

					@Override
					protected void thenDoUiRelatedWork(CountryViewState result) {
						country.countryState = CountryState.ADD;
						listViewNotifyDataChange();
					}
				});
	}

	private void onAddRefresh(CountryViewState country) {
		Intent intent = new Intent(parent, DownloadService.class);
		intent.putExtra("countryISO", country.countryISO);
		parent.startService(intent);
	}

	private void deleteCountryData(String countryIso) {
		AppDatabase.getInstance(parent).nodeDao().deleteNodesFromCountry(countryIso.toLowerCase());
	}
	public static Map<String, CountryViewState> initCountryMap(AppCompatActivity parent) {
		Map<String, CountryViewState> resultMap = new LinkedHashMap<>();
		Set<String> sortedCountryList = loadCountryList(parent);
		for (String country : sortedCountryList) {
			CountryViewState countryView = new CountryViewState(DataFragment.CountryState.ADD, country);
			resultMap.put(countryView.countryISO, countryView);
		}
		return resultMap;
	}

	private static Set<String> loadCountryList(AppCompatActivity parent) {
		InputStream is = parent.getResources().openRawResource(R.raw.country_bbox);

		BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

		Set<String> sortedCountryList = new TreeSet<>(new Comparator<String>() {
			@Override
			public int compare(String s, String t1) {
				return s.split(",")[1].compareTo(t1.split(",")[1]);
			}
		});
		try {
			reader.readLine(); //ignore headers
			String line;
			while ((line = reader.readLine()) != null) {
				sortedCountryList.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return sortedCountryList;
	}


	public Resources getResources() {
		return parent.getResources();
	}

	<T extends View> T findViewById(@IdRes int id) {
		return view.findViewById(id);
	}

	public abstract void onActivityResult(int requestCode, int resultCode, Intent data);
}
