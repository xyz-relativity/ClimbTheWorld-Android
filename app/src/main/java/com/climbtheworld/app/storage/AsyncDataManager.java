package com.climbtheworld.app.storage;

import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONException;
import org.osmdroid.util.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import needle.Needle;

/**
 * Created by xyz on 2/9/18.
 */

public class AsyncDataManager {
    public static String DATA_KEY = "data";

    private DataManager dataManager;
    private final Semaphore isDownloading = new Semaphore(1, true);
    private List<IDataManagerEventListener> observers = new ArrayList<>();

    public AsyncDataManager(boolean applyFilters) {
        dataManager = new DataManager(applyFilters);
    }

    public void addObserver(IDataManagerEventListener... observer) {
        observers.addAll(Arrays.asList(observer));
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    private HashMap<String, Object> buildParams(final Object data) {
        HashMap<String, Object> params = new HashMap<String, Object>() {{
            put(DATA_KEY, data);
        }};

        return params;
    }

    public boolean downloadAround(final double pDecLatitude,
                                  final double pDecLongitude,
                                  final double pMetersAltitude,
                                  final double maxDistance,
                                  final Map<Long, GeoNode> poiMap,
                                  String countryIso) {
        if (dataManager.canDownload()) {
            return downloadBBox(DataManager.computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance),
                    poiMap, countryIso);
        }
        return false;
    }

    public boolean loadAround(final double pDecLatitude,
                              final double pDecLongitude,
                              final double pMetersAltitude,
                              final double maxDistance,
                              final Map<Long, GeoNode> poiMap, GeoNode.NodeTypes type) {

        return loadBBox(DataManager.computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap, type);
    }

    public boolean downloadBBox(final BoundingBox bBox,
                                final Map<Long, GeoNode> poiMap,
                                final String countryIso) {
        if (!dataManager.canDownload()) {
            return false;
        }

        final HashMap<String, Object> params = buildParams(poiMap);

        notifyObservers(10, false, params);
        (new Thread() {
            public void run() {
                boolean hasChange = false;
                try {
                    isDownloading.acquire();

                    hasChange = dataManager.downloadBBox(bBox, poiMap, countryIso);
                } catch (JSONException | IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                notifyObservers(100, hasChange, params);
                isDownloading.release();
            }
        }).start();

        return true;
    }

    public boolean loadBBox(final BoundingBox bBox,
                            final Map<Long, GeoNode> poiMap,
                            final GeoNode.NodeTypes... type) {
        final HashMap<String, Object> params = buildParams(poiMap);

        notifyObservers(10, false, params);
        Needle.onBackgroundThread().withThreadPoolSize(1).execute(new Runnable() {
            @Override
            public void run() {
                notifyObservers(100, dataManager.loadBBox(bBox, poiMap, type), params);
            }
        });

        return true;
    }

    private void notifyObservers(int progress, boolean hasChanges, Map<String, Object> results) {
        for (IDataManagerEventListener i: observers) {
            i.onProgress(progress, hasChanges, results);
        }
    }

    public boolean pushToDb(final Map<Long, GeoNode> poiMap, final boolean replace)
    {
        final HashMap<String, Object> params = buildParams(poiMap);

        Needle.onBackgroundThread().withThreadPoolSize(1).execute(new Runnable() {
            @Override
            public void run() {
                dataManager.pushToDb(poiMap, replace);
                notifyObservers(100, false, params);
            }
        });
        return true;
    }
}
