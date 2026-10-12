package com.climbtheworld.app.storage.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.NodesDataManagerActivity;
import com.climbtheworld.app.storage.offline.importer.OverpassCountryDownloader;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import needle.Needle;

/**
 * Downloads one country per start request, one at a time on a worker thread. It runs in the
 * foreground while a download is queued or running, so it keeps going when the app leaves the
 * screen. An interrupted Overpass query cannot be resumed, so nothing outlives the process.
 */
public class DownloadService extends Service {
	private static final String EXTRA_COUNTRY_ISO = "countryISO";
	private static final String ACTION_CANCEL_ALL = "com.climbtheworld.app.action.CANCEL_DOWNLOADS";
	private static final String NOTIFICATION_CHANNEL_ID = "downloadService";
	private static final int NOTIFICATION_ID = 682988;
	/** Only a safety net for a hung download; the lock is released when the download ends. */
	private static final long WAKE_LOCK_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(1);
	private static final String TAG = DownloadService.class.getSimpleName();

	private static final List<DownloadProgressListener> eventListeners = new CopyOnWriteArrayList<>();
	/** The last state of each queued or running download, replayed to new listeners. */
	private static final Map<String, Integer> currentState = new ConcurrentHashMap<>();
	/** The queued or running request per country, so repeated requests do not download twice. */
	private static final Map<String, DownloadRequest> activeRequests = new ConcurrentHashMap<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor();
	private OverpassCountryDownloader countryDownloader;
	private NotificationManager notificationManager;
	private PendingIntent openDownloadsIntent;
	private PendingIntent cancelAllIntent;
	private PowerManager.WakeLock wakeLock;
	/** The country the notification shows, or null between downloads. */
	private volatile String runningCountry;
	/** The running download's progress in percent, or -1 while it is not known. */
	private volatile int runningPercent = -1;

	/** Queues the country's download, unless it is already queued or running. */
	public static void start(Context context, String countryIso) {
		Intent intent = new Intent(context, DownloadService.class);
		intent.putExtra(EXTRA_COUNTRY_ISO, countryIso);
		ContextCompat.startForegroundService(context, intent);
	}

	public static void addListener(DownloadProgressListener listener) {
		if (!eventListeners.contains(listener)) {
			eventListeners.add(listener);

			Needle.onMainThread().execute(() -> {
				for (Map.Entry<String, Integer> country : currentState.entrySet()) {
					listener.onProgressRestored(country.getKey(), country.getValue());
				}
			});
		}
	}

	public static void removeListener(DownloadProgressListener listener) {
		eventListeners.remove(listener);
	}

	/** Cancels the country's queued or running download, and tells the listeners. */
	public static void cancel(String countryIso) {
		DownloadRequest request = activeRequests.get(countryIso);
		if (request != null && request.cancel(countryIso)) {
			notifyListeners(countryIso, DownloadProgressListener.STATUS_CANCELLED);
		}
	}

	/**
	 * Cancels the country's queued or running download, so it cannot write the country back after
	 * the caller deletes it. Call before deleting the country's data. The listeners are not told,
	 * as the caller shows the delete instead.
	 */
	public static void cancelBeforeDelete(String countryIso) {
		DownloadRequest request = activeRequests.get(countryIso);
		if (request != null) {
			request.cancel(countryIso);
		}
	}

	private static void cancelAll() {
		for (String countryIso : activeRequests.keySet()) {
			cancel(countryIso);
		}
	}

	/** The country's name in the user's language, or its ISO code if Android does not know it. */
	static String countryName(String countryIso) {
		String name = new Locale("", countryIso).getDisplayCountry();
		return name.isEmpty() ? countryIso : name;
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
		if (progressEvent == DownloadProgressListener.STATUS_DONE
				|| progressEvent == DownloadProgressListener.STATUS_ERROR) {
			currentState.remove(eventOwner);
		} else {
			currentState.put(eventOwner, progressEvent);
		}
		notifyListeners(eventOwner, progressEvent);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		countryDownloader = new OverpassCountryDownloader();

		notificationManager = getSystemService(NotificationManager.class);
		notificationManager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID,
				getString(R.string.download_notification_channel), NotificationManager.IMPORTANCE_LOW));
		openDownloadsIntent = PendingIntent.getActivity(this, 0,
				NodesDataManagerActivity.downloadTabIntent(this)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
				PendingIntent.FLAG_IMMUTABLE);
		cancelAllIntent = PendingIntent.getService(this, 0,
				new Intent(this, DownloadService.class).setAction(ACTION_CANCEL_ALL),
				PendingIntent.FLAG_IMMUTABLE);

		// Keeps the CPU running while the screen is off and the server works on the query.
		wakeLock = getSystemService(PowerManager.class)
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:download");
		wakeLock.setReferenceCounted(false);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Runnable task;
		if (intent != null && ACTION_CANCEL_ALL.equals(intent.getAction())) {
			cancelAll();
			task = () -> {
			};
		} else {
			task = queue(intent != null ? intent.getStringExtra(EXTRA_COUNTRY_ISO) : null);
			// Every startForegroundService() call must be answered, a duplicate request's included.
			ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
					ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
		}
		// One task per start request, so stopSelf(startId) only stops the service after the last.
		worker.execute(() -> {
			try {
				task.run();
			} finally {
				stopSelf(startId);
			}
		});
		return START_NOT_STICKY;
	}

	/** Android 15+ limits dataSync services to six hours a day, and stops the app if it runs on. */
	@Override
	public void onTimeout(int startId, int fgsType) {
		Log.w(TAG, "Foreground service time limit reached, cancelling the downloads");
		cancelAll();
		stopSelf();
	}

	@Override
	public void onDestroy() {
		worker.shutdown();
		wakeLock.release();
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/** @return the task that downloads the country, which does nothing for a duplicate. */
	private Runnable queue(String countryIso) {
		if (countryIso == null) {
			return () -> {
			};
		}
		DownloadRequest current = activeRequests.get(countryIso);
		if (current != null && !current.cancellation.isCanceled()) {
			return () -> {
			};
		}
		DownloadRequest request = new DownloadRequest();
		activeRequests.put(countryIso, request);
		request.report(countryIso, DownloadProgressListener.STATUS_WAITING);
		return () -> run(countryIso, request);
	}

	private void run(String countryIso, DownloadRequest request) {
		try {
			if (request.cancellation.isCanceled()) {
				return;
			}
			runningCountry = countryIso;
			runningPercent = -1;
			updateNotification();
			wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS);
			try {
				download(countryIso, request);
			} finally {
				wakeLock.release();
				runningCountry = null;
			}
		} finally {
			activeRequests.remove(countryIso, request);
		}
	}

	private void download(String countryIso, DownloadRequest request) {
		try {
			countryDownloader.downloadAndImport(getApplicationContext(), countryIso, percent -> {
				if (request.report(countryIso, percent)) {
					runningPercent = percent;
					updateNotification();
				}
			}, request.cancellation);
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

	private void updateNotification() {
		notificationManager.notify(NOTIFICATION_ID, buildNotification());
	}

	/**
	 * Not marked ongoing: the system already keeps it while the service is in the foreground,
	 * and an update racing the service's stop must not leave a notification that cannot be cleared.
	 */
	private Notification buildNotification() {
		String country = runningCountry;
		int percent = runningPercent;
		int queued = queuedDownloads();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setContentTitle(country != null
						? getString(R.string.download_notification_title, countryName(country))
						: getString(R.string.download_notification_starting))
				.setProgress(100, Math.max(percent, 0), percent < 0)
				.setCategory(NotificationCompat.CATEGORY_PROGRESS)
				.setOnlyAlertOnce(true)
				.setSilent(true)
				.setContentIntent(openDownloadsIntent)
				.addAction(0, getString(queued > 0 ? R.string.download_cancel_all : R.string.cancel),
						cancelAllIntent);
		if (queued > 0) {
			builder.setContentText(getResources().getQuantityString(
					R.plurals.download_notification_queued, queued, queued));
		}
		return builder.build();
	}

	/** The downloads waiting behind the one that is running or about to run. */
	private static int queuedDownloads() {
		int pending = 0;
		for (DownloadRequest request : activeRequests.values()) {
			if (!request.cancellation.isCanceled()) {
				pending++;
			}
		}
		return Math.max(0, pending - 1);
	}

	/** Shown by the service rather than the listeners, which would repeat it on every replay. */
	private void showError(String message) {
		Context appContext = getApplicationContext();
		Needle.onMainThread().execute(() -> Toast.makeText(appContext, message, Toast.LENGTH_LONG).show());
	}

	/** One queued or running download. Cancelling it also silences its progress reports. */
	private static final class DownloadRequest {
		final CancellationSignal cancellation = new CancellationSignal();

		/** @return false if the request is cancelled, so nothing was reported. */
		synchronized boolean report(String countryIso, int progressEvent) {
			if (cancellation.isCanceled()) {
				return false;
			}
			updateProgress(countryIso, progressEvent);
			return true;
		}

		/**
		 * Forgets the reported state, so listeners registered later do not replay stale progress.
		 *
		 * @return false if the request was already cancelled.
		 */
		synchronized boolean cancel(String countryIso) {
			if (cancellation.isCanceled()) {
				return false;
			}
			cancellation.cancel();
			currentState.remove(countryIso);
			return true;
		}
	}
}
