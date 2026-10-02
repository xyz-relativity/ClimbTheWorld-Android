package com.climbtheworld.app.storage.services;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.climbtheworld.app.storage.offline.importer.OverpassCountryDownloader;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
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
		updateProgress(countryIso, 5);
		Timer timer = new Timer("DownloadProgress-" + countryIso, true);
		ProgressTicker ticker = new ProgressTicker(countryIso);
		timer.schedule(ticker, 1000, 1000);

		int finalState;
		try {
			countryDownloader.downloadAndImport(getApplicationContext(), countryIso);
			finalState = DownloadProgressListener.STATUS_DONE;
		} catch (Exception | OutOfMemoryError exception) {
			Log.w(TAG, "Download failed for " + countryIso, exception);
			finalState = DownloadProgressListener.STATUS_ERROR;
		} finally {
			ticker.stop();
			timer.cancel();
		}

		if (finalState == DownloadProgressListener.STATUS_DONE) {
			updateProgress(countryIso, 100);
		}
		updateProgress(countryIso, finalState);
	}

	/** Estimated progress while waiting on Overpass; never reports after {@link #stop()}. */
	private static final class ProgressTicker extends TimerTask {
		private final String countryIso;
		private int progress = 1;
		private boolean stopped;

		ProgressTicker(String countryIso) {
			this.countryIso = countryIso;
		}

		@Override
		public synchronized void run() {
			if (stopped) {
				return;
			}
			progress++;
			updateProgress(countryIso, (int) Globals.reMap(progress, 1,
					Constants.HTTP_TIMEOUT_SECONDS, 5, 80).longValue());
		}

		synchronized void stop() {
			stopped = true;
			cancel();
		}
	}
}
