package com.climbtheworld.app.storage.services;

import android.app.IntentService;
import android.content.Intent;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import needle.Needle;

public class DownloadService extends IntentService {
	private static List<DownloadProgressListener> eventListeners = new ArrayList<>();
	private static Map<String, Integer> currentState = new HashMap<>();
	private DataManager downloadManager;

	public DownloadService() {
		super("DownloadService");
	}

	public static Integer getState(String id) {
		return currentState.get(id);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		downloadManager = new DataManager(getApplicationContext());
		return super.onStartCommand(intent, flags, startId);
	}

	public static void addListener(DownloadProgressListener listener) {
		if (!eventListeners.contains(listener)) {
			eventListeners.add(listener);

			for (String country : currentState.keySet()) {
				notifyListeners(country, currentState.get(country));
			}
		}
	}

	public static void removeListener(DownloadProgressListener listener) {
		eventListeners.remove(listener);
	}

	private void updateProgress(String eventOwner, int progressEvent) {
		currentState.put(eventOwner, progressEvent);
		notifyListeners(eventOwner, progressEvent);
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

	@Override
	protected void onHandleIntent(Intent intent) {
		String countryIso = intent.getStringExtra("countryISO");
		updateProgress(countryIso, DownloadProgressListener.PROGRESS_WAITING);
		Constants.WEB_EXECUTOR
				.execute(new Runnable() {
					private Timer timer;
					private int progress = 1;

					@Override
					public void run() {
						updateProgress(countryIso, DownloadProgressListener.PROGRESS_START);
						try {
							timer = new Timer();
							updateProgress(countryIso, 1);
							Map<Long, DisplayableGeoNode> nodes = new HashMap<>();
							timer.schedule(new TimerTask() {
								@Override
								public void run() {
									progress++;
									updateProgress(countryIso, (int)Globals.map(progress, 1, DataManager.HTTP_TIMEOUT_SECONDS, 1, 99));
								}
							}, 1000);
                            downloadManager.downloadCountry(nodes, countryIso);
							downloadManager.pushToDb(nodes, true);
						} catch (IOException | JSONException e) {
							updateProgress(countryIso, DownloadProgressListener.PROGRESS_ERROR);
						} finally {
							timer.cancel();
							timer.purge();
						}
						updateProgress(countryIso, DownloadProgressListener.PROGRESS_DONE);
					}
				});
	}
}
