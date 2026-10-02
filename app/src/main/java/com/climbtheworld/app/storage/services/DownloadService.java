package com.climbtheworld.app.storage.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Toast;

import com.climbtheworld.app.storage.offline.importer.OverpassCountryDownloader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import needle.Needle;

/**
 * Downloads one country per intent. Intents run one at a time on the IntentService worker thread,
 * which keeps the service alive until the download and import finish.
 */
public class DownloadService extends IntentService {
	public static final String EXTRA_COUNTRY_ISO = "countryISO";
	private static final String EXTRA_REQUEST_ID = "requestId";
	private static final String TAG = DownloadService.class.getSimpleName();

	private static final List<DownloadProgressListener> eventListeners = new CopyOnWriteArrayList<>();
	private static final Map<String, Integer> currentState = new ConcurrentHashMap<>();
	/** The queued or running request per country, so repeated requests do not download twice. */
	private static final Map<String, DownloadRequest> activeRequests = new ConcurrentHashMap<>();
	private static final AtomicLong nextRequestId = new AtomicLong();
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

	/**
	 * Cancels the country's queued or running download, so it cannot write the country back after
	 * the caller deletes it. Call before deleting the country's data.
	 */
	public static void cancel(String countryIso) {
		DownloadRequest request = activeRequests.get(countryIso);
		if (request != null) {
			request.cancel(countryIso);
		}
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
			DownloadRequest current = activeRequests.get(countryIso);
			// A duplicate gets no request id but is still handed to the worker, so the
			// IntentService stops itself after the last intent.
			if (current == null || current.cancellation.isCanceled()) {
				DownloadRequest request = new DownloadRequest(nextRequestId.incrementAndGet());
				activeRequests.put(countryIso, request);
				intent.putExtra(EXTRA_REQUEST_ID, request.id);
				request.report(countryIso, DownloadProgressListener.STATUS_WAITING);
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
		long requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1);
		DownloadRequest request = countryIso != null ? activeRequests.get(countryIso) : null;
		// No match: a duplicate, or a request cancelled and since replaced by a new one.
		if (request == null || request.id != requestId) {
			return;
		}
		try {
			if (!request.cancellation.isCanceled()) {
				download(countryIso, request);
			}
		} finally {
			activeRequests.remove(countryIso, request);
		}
	}

	private void download(String countryIso, DownloadRequest request) {
		try {
			countryDownloader.downloadAndImport(getApplicationContext(), countryIso,
					percent -> request.report(countryIso, percent), request.cancellation);
		} catch (Exception | OutOfMemoryError exception) {
			if (request.cancellation.isCanceled()) {
				Log.d(TAG, "Download cancelled for " + countryIso);
				return;
			}
			Log.w(TAG, "Download failed for " + countryIso, exception);
			showError(DownloadErrors.message(getApplicationContext(), countryIso, exception));
			request.report(countryIso, DownloadProgressListener.STATUS_ERROR);
			return;
		}
		request.report(countryIso, 100);
		request.report(countryIso, DownloadProgressListener.STATUS_DONE);
	}

	/** Shown by the service rather than the listeners, which would repeat it on every replay. */
	private void showError(String message) {
		Context appContext = getApplicationContext();
		Needle.onMainThread().execute(() -> Toast.makeText(appContext, message, Toast.LENGTH_LONG).show());
	}

	/** One queued or running download. Cancelling it also silences its progress reports. */
	private static final class DownloadRequest {
		final long id;
		final CancellationSignal cancellation = new CancellationSignal();

		DownloadRequest(long id) {
			this.id = id;
		}

		synchronized void report(String countryIso, int progressEvent) {
			if (!cancellation.isCanceled()) {
				updateProgress(countryIso, progressEvent);
			}
		}

		/** Forgets the reported state, so listeners registered later do not replay stale progress. */
		synchronized void cancel(String countryIso) {
			cancellation.cancel();
			currentState.remove(countryIso);
		}
	}
}
