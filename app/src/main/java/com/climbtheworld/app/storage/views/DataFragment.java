package com.climbtheworld.app.storage.views;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.DownloadedCountry;
import com.climbtheworld.app.storage.database.EntityCountryDao;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.logbook.LogBookFilter;
import com.climbtheworld.app.storage.offline.OfflineCountryStore;
import com.climbtheworld.app.storage.services.DownloadProgressListener;
import com.climbtheworld.app.storage.services.DownloadService;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.IPagerViewFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public abstract class DataFragment implements DownloadProgressListener, IPagerViewFragment {
	private static final int NOTIFICATION_PERMISSION_REQUEST = 4101;

	public enum CountryState {
		ADD,
		WAITING,
		PROGRESS,
		DELETING,
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
	 * delete in flight keeps its state until it finishes.
	 */
	void refreshInstalledCountries() {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<InstalledCountries>() {
			@Override
			protected InstalledCountries doWork() {
				AppDatabase database = AppDatabase.getInstance(appContext);
				InstalledCountries result = new InstalledCountries();
				for (DownloadedCountry download : database.downloadedCountryDao().loadAll()) {
					result.downloads.put(download.countryIso, download);
				}
				for (EntityCountryDao.CountryEntityCount routes : database.entityCountryDao()
						.countPerCountry(OsmEntity.EntityClimbingType.route)) {
					result.routeCounts.put(routes.countryIso, routes.entityCount);
				}
				return result;
			}

			@Override
			protected void thenDoUiRelatedWork(InstalledCountries result) {
				List<String> installedCountries = new ArrayList<>();
				for (CountryViewState country : countryMap.values()) {
					country.download = result.downloads.get(country.countryISO);
					Integer routeCount = result.routeCounts.get(country.countryISO);
					country.routeCount = routeCount != null ? routeCount : 0;
					boolean installed = country.download != null;
					if (installed) {
						installedCountries.add(country.countryISO);
					}
					if (country.countryState == CountryState.ADD
							|| country.countryState == CountryState.REMOVE) {
						country.countryState = installed ? CountryState.REMOVE : CountryState.ADD;
					}
				}
				onInstalledCountriesLoaded(installedCountries);
				listViewNotifyDataChange();
			}
		});
	}

	private static final class InstalledCountries {
		final Map<String, DownloadedCountry> downloads = new HashMap<>();
		final Map<String, Integer> routeCounts = new HashMap<>();
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

	/** The quick filter of the country tabs: the name in the user's language or English, or the ISO code. */
	static boolean matchesFilter(CountryViewState country, String filter) {
		String text = LogBookFilter.normalize(filter.trim());
		return LogBookFilter.normalize(country.countryName).contains(text)
				|| LogBookFilter.normalize(country.englishName).contains(text)
				|| LogBookFilter.normalize(country.countryISO).contains(text);
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
		view.findViewById(R.id.countryCancelButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				DownloadService.cancel(country.countryISO);
			}
		});

		TextView textField = view.findViewById(R.id.selectText);
		textField.setText(country.countryName);
		String error = DownloadService.lastError(country.countryISO);
		View errorIcon = view.findViewById(R.id.countryErrorIcon);
		errorIcon.setVisibility(error != null ? View.VISIBLE : View.GONE);
		errorIcon.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Toast.makeText(appContext, error, Toast.LENGTH_LONG).show();
			}
		});

		TextView downloadInfo = view.findViewById(R.id.countryDownloadInfo);
		if (country.download != null) {
			downloadInfo.setText(CountryDownloadInfo.describe(getResources(),
					country.download.downloadedAt, country.routeCount, System.currentTimeMillis()));
			downloadInfo.setVisibility(View.VISIBLE);
		} else {
			downloadInfo.setVisibility(View.GONE);
		}

		ImageView flag = view.findViewById(R.id.countryFlag);
		flag.setTag(country.countryISO);
		country.setFlag(flag, parent.get());
		return view;
	}

	private void onDeleteEvent(CountryViewState country) {
		country.countryState = CountryState.DELETING;
		listViewNotifyDataChange();
		// Before the delete is queued, so a download in flight cannot write the country back.
		DownloadService.cancelBeforeDelete(country.countryISO);

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
						// Deleting the last country brings back the notification to download one.
						AppCompatActivity activity = parent.get();
						if (activity != null) {
							Globals.showNotifications(activity);
						}
					}
				});
	}

	private void onAddRefresh(CountryViewState country) {
		AppCompatActivity activity = parent.get();
		if (Globals.allowDataDownload(activity)) {
			startDownload(activity, country, false);
		} else {
			DialogBuilder.showMobileDataDownloadDialog(activity, country.countryName,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							startDownload(activity, country, true);
						}
					});
		}
	}

	private static void startDownload(AppCompatActivity activity, CountryViewState country,
	                                  boolean allowMobileData) {
		DownloadService.start(activity, country.countryISO, allowMobileData);
		requestNotificationPermission(activity);
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
		List<CountryViewState> countries = new ArrayList<>();
		for (String country : loadCountryList(parent)) {
			countries.add(new CountryViewState(DataFragment.CountryState.ADD, country));
		}
		// The names are in the user's language, so they are sorted by its rules.
		Collator collator = Collator.getInstance();
		countries.sort((first, second) -> collator.compare(first.countryName, second.countryName));

		Map<String, CountryViewState> resultMap = new LinkedHashMap<>();
		for (CountryViewState country : countries) {
			resultMap.put(country.countryISO, country);
		}
		return resultMap;
	}

	private static List<String> loadCountryList(AppCompatActivity parent) {
		List<String> countryList = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				parent.getResources().openRawResource(R.raw.country_bbox), StandardCharsets.UTF_8))) {
			reader.readLine(); //ignore headers
			String line;
			while ((line = reader.readLine()) != null) {
				countryList.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return countryList;
	}


	public Resources getResources() {
		return parent.get().getResources();
	}

	<T extends View> T findViewById(@IdRes int id) {
		return view.findViewById(id);
	}

	public abstract void onActivityResult(int requestCode, int resultCode, Intent data);
}
