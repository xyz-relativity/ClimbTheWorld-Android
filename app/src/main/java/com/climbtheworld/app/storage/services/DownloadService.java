package com.climbtheworld.app.storage.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.climbtheworld.app.storage.offline.importer.OverpassCountryDownloader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import needle.Needle;

/**
 * Downloads one country per intent. Intents run one at a time on the IntentService worker thread,
 * which keeps the service alive until the download and import finish.
 */
public class DownloadService extends IntentService {
	public static final String EXTRA_COUNTRY_ISO = "countryISO";
	private static final String EXTRA_DUPLICATE = "duplicate";
	private static final String TAG = DownloadService.class.getSimpleName();

	private static final List<DownloadProgressListener> eventListeners = new CopyOnWriteArrayList<>();
	private static final Map<String, Integer> currentState = new ConcurrentHashMap<>();
	/** Countries queued or downloading, so repeated requests do not download them twice. */
	private static final Set<String> queuedCountries = ConcurrentHashMap.newKeySet();
	private OverpassCountryDownloader countryDownloader;

	public DownloadService() {
		super("DownloadService");
	}

	public static Integer getState(String id) {
		return currentState.get(id);
	}

	public static void addListener(DownloadProgressListener listener) {
		if (!eventListeners.contains(listener)) {
			eventListeners.add(listener);

			for (Map.Entry<String, Integer> country : currentState.entrySet()) {
				notifyListeners(country.getKey(), country.getValue());
			}
		}
	}

	public static void removeListener(DownloadProgressListener listener) {
		eventListeners.remove(listener);
	}

	private static void notifyListeners(String eventOwner, int progressEvent) {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				for (DownloadProgressListener listener : eventListeners) {
					listener.onProgressChanged(eventOwner, progressEvent);
				}
			}
		});
	}

	private static void updateProgress(String eventOwner, int progressEvent) {
		currentState.put(eventOwner, progressEvent);
		notifyListeners(eventOwner, progressEvent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		countryDownloader = new OverpassCountryDownloader();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String countryIso = intent != null ? intent.getStringExtra(EXTRA_COUNTRY_ISO) : null;
		if (countryIso != null) {
			if (queuedCountries.add(countryIso)) {
				updateProgress(countryIso, DownloadProgressListener.STATUS_WAITING);
			} else {
				// Still handed to the worker so the IntentService stops itself after the last intent.
				intent.putExtra(EXTRA_DUPLICATE, true);
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent == null) {
			return;
		}
		String countryIso = intent.getStringExtra(EXTRA_COUNTRY_ISO);
		if (countryIso == null || intent.getBooleanExtra(EXTRA_DUPLICATE, false)) {
			return;
		}
		try {
			download(countryIso);
		} finally {
			queuedCountries.remove(countryIso);
		}
	}

	private void download(String countryIso) {
		int finalState;
		try {
			countryDownloader.downloadAndImport(getApplicationContext(), countryIso,
					percent -> updateProgress(countryIso, percent));
			finalState = DownloadProgressListener.STATUS_DONE;
		} catch (Exception | OutOfMemoryError exception) {
			Log.w(TAG, "Download failed for " + countryIso, exception);
			showError(DownloadErrors.message(getApplicationContext(), countryIso, exception));
			finalState = DownloadProgressListener.STATUS_ERROR;
		}

		if (finalState == DownloadProgressListener.STATUS_DONE) {
			updateProgress(countryIso, 100);
		}
		updateProgress(countryIso, finalState);
	}

	/** Shown by the service rather than the listeners, which would repeat it on every replay. */
	private void showError(String message) {
		Context appContext = getApplicationContext();
		Needle.onMainThread().execute(() -> Toast.makeText(appContext, message, Toast.LENGTH_LONG).show());
	}
}
