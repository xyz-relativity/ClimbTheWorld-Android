package com.climbtheworld.app.storage.services;

import android.app.IntentService;
import android.content.Intent;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import needle.Needle;

public class DownloadService extends IntentService {
    public static List<DownloadProgressListener> eventListeners = new ArrayList<>();
    private DataManager downloadManager;

    public DownloadService() {
        super("DownloadService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        downloadManager = new DataManager(getApplicationContext());
        return super.onStartCommand(intent, flags, startId);
    }

    public static void addListener(DownloadProgressListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public static void removeListener(DownloadProgressListener listener) {
        eventListeners.remove(listener);
    }

    public void updateProgress(String eventOwner, int progressEvent) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                for (DownloadProgressListener listener : eventListeners) {
                    listener.onProgress(eventOwner, progressEvent);
                }
            }
        });
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String countryIso = intent.getStringExtra("countryISO");
        System.out.println("========= Start download for: " + countryIso);
        updateProgress(countryIso, DownloadProgressListener.PROGRESS_WAITING);
        Constants.WEB_EXECUTOR
                .execute(new Runnable() {
                    @Override
                    public void run() {
                        updateProgress(countryIso, DownloadProgressListener.PROGRESS_START);
//                        try {
                            updateProgress(countryIso, 10);
                            Map<Long, DisplayableGeoNode> nodes = new HashMap<>();
                            //downloadManager.downloadCountry(nodes, countryIso);
                            updateProgress(countryIso, 50);
                            downloadManager.pushToDb(nodes, true);
                            updateProgress(countryIso, 80);
//                        } catch (IOException | JSONException e) {
//                            updateProgress(countryIso, DownloadProgressListener.PROGRESS_ERROR);
//                        }
                        updateProgress(countryIso, DownloadProgressListener.PROGRESS_DONE);
                    }
                });

    }
}
