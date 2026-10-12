package com.climbtheworld.app.storage.views;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.offline.OfflineCountryStore;
import com.climbtheworld.app.storage.services.DownloadProgressListener;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.IPagerViewFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import needle.UiRelatedTask;

public abstract class DataFragment implements DownloadProgressListener, IPagerViewFragment {
	private static final int NOTIFICATION_PERMISSION_REQUEST = 4101;

	public enum CountryState {
		ADD,
		WAITING,
		PROGRESS,
		REMOVE
	}

	protected final Configs configs;
	final WeakReference<AppCompatActivity> parent;
	/** For background work, which can outlive the activity. */
	final Context appContext;
	Map<String, CountryViewState> countryMap; //ConcurrentSkipListMap<>();
	@LayoutRes
	int viewID;
	ViewGroup view;
	LayoutInflater inflater;
	ListView listView;

	DataFragment(AppCompatActivity parent, @LayoutRes int viewID, Map<String, CountryViewState> countryMap) {
		this.parent = new WeakReference<>(parent);
		this.appContext = parent.getApplicationContext();
		this.viewID = viewID;
		this.countryMap = countryMap;

		inflater = parent.getLayoutInflater();
		configs = Configs.instance(parent);
	}

	@Override
	public void onProgressChanged(String eventOwner, int statusEvent) {
		// Whichever way a download ends, the database says whether the country is installed.
		if (updateCountryState(eventOwner, statusEvent)
				&& (statusEvent == DownloadProgressListener.STATUS_DONE
				|| statusEvent == DownloadProgressListener.STATUS_ERROR
				|| statusEvent == DownloadProgressListener.STATUS_CANCELLED)) {
			refreshInstalledCountries();
		}
	}

	@Override
	public void onProgressRestored(String eventOwner, int statusEvent) {
		updateCountryState(eventOwner, statusEvent);
	}

	/** @return false if this observer does not show the country. */
	private boolean updateCountryState(String eventOwner, int statusEvent) {
		CountryViewState country = countryMap.get(eventOwner);
		if (country == null) { //this country is not visible in this observer
			return false;
		}

		switch (statusEvent) {
			case DownloadProgressListener.STATUS_WAITING:
				country.countryState = CountryState.WAITING;
				break;

			case DownloadProgressListener.STATUS_DONE:
				country.countryState = CountryState.REMOVE;
				break;

			case DownloadProgressListener.STATUS_ERROR:
			case DownloadProgressListener.STATUS_CANCELLED:
				country.countryState = CountryState.ADD;
				break;

			default:
				country.countryState = CountryState.PROGRESS;
				country.progress = statusEvent;
		}

		listViewNotifyDataChange();
		return true;
	}

	/**
	 * Reloads which countries are installed. Idle rows follow the database, while a download or
	 * delete in flight keeps its state, which hides the buttons until it finishes.
	 */
	void refreshInstalledCountries() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Set<String>>() {
			@Override
			protected Set<String> doWork() {
				return new HashSet<>(AppDatabase.getInstance(appContext).downloadedCountryDao()
						.loadCountryIsos());
			}

			@Override
			protected void thenDoUiRelatedWork(Set<String> dbCountries) {
				List<String> installedCountries = new ArrayList<>();
				for (CountryViewState country : countryMap.values()) {
					boolean installed = dbCountries.contains(country.countryISO);
					if (installed) {
						installedCountries.add(country.countryISO);
					}
					if (country.countryState != CountryState.WAITING
							&& country.countryState != CountryState.PROGRESS) {
						country.countryState = installed ? CountryState.REMOVE : CountryState.ADD;
					}
				}
				onInstalledCountriesLoaded(installedCountries);
				listViewNotifyDataChange();
			}
		});
	}

	/** Lets a tab list the installed countries, in the country list's order. */
	protected void onInstalledCountriesLoaded(List<String> installedCountries) {
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
		country.setFlag(flag, parent.get());
		return view;
	}

	private void onDeleteEvent(CountryViewState country) {
		country.countryState = CountryState.PROGRESS;
		listViewNotifyDataChange();
		// Before the delete is queued, so a download in flight cannot write the country back.
		DownloadService.cancel(country.countryISO);

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
						refreshInstalledCountries();
					}
				});
	}

	private void onAddRefresh(CountryViewState country) {
		DownloadService.start(parent.get(), country.countryISO);
		requestNotificationPermission(parent.get());
	}

	/**
	 * Android 13+ only shows the download notification, with its progress and cancel button, once
	 * this is granted. The download runs either way.
	 */
	private static void requestNotificationPermission(AppCompatActivity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
				&& ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
				!= PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(activity,
					new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
		}
	}

	private void deleteCountryData(String countryIso) {
		new OfflineCountryStore(AppDatabase.getInstance(appContext)).deleteCountry(countryIso);
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
		return parent.get().getResources();
	}

	<T extends View> T findViewById(@IdRes int id) {
		return view.findViewById(id);
	}

	public abstract void onActivityResult(int requestCode, int resultCode, Intent data);
}
