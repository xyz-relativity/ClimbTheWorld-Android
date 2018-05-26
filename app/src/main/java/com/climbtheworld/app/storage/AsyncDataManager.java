package com.climbtheworld.app.storage;

import com.climbtheworld.app.storage.database.GeoNode;

import org.osmdroid.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by xyz on 2/9/18.
 */

public class AsyncDataManager {
    public static String DATA_KEY = "data";

    private DataManager dataManager;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
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
        if (!dataManager.canDownload()) {
            return false;
        }

        return downloadBBox(DataManager.computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance),
                poiMap, countryIso);
    }

    public boolean loadAround(final double pDecLatitude,
                              final double pDecLongitude,
                              final double pMetersAltitude,
                              final double maxDistance,
                              final Map<Long, GeoNode> poiMap) {

        return loadBBox(DataManager.computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap);
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
                isDownloading.set(true);

                dataManager.downloadBBox(bBox, poiMap, countryIso);
                notifyObservers(100, dataManager.downloadBBox(bBox, poiMap, countryIso), params);
                isDownloading.set(false);
            }
        }).start();

        return true;
    }

    public boolean loadBBox(final BoundingBox bBox,
                            final Map<Long, GeoNode> poiMap) {
        final HashMap<String, Object> params = buildParams(poiMap);

        notifyObservers(10, false, params);
        (new Thread() {
            public void run() {
                notifyObservers(100, dataManager.loadBBox(bBox, poiMap), params);
            }
        }).start();

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

        (new Thread() {
            public void run() {
                dataManager.pushToDb(poiMap, replace);
                notifyObservers(100, false, params);
            }
        }).start();
        return true;
    }
}
