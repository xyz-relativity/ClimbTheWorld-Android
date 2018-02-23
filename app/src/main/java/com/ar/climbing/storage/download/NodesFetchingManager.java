package com.ar.climbing.storage.download;

import android.content.Context;

import com.ar.climbing.R;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.AugmentedRealityUtils;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xyz on 2/9/18.
 */

public class NodesFetchingManager {


    public static String DATA_KEY = "data";

    private Context context;
    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private List<INodesFetchingEventListener> observers = new ArrayList<>();

    public NodesFetchingManager(Context currentContext) {
        this.context = currentContext;
    }

    public void addObserver(INodesFetchingEventListener... observer) {
        observers.addAll(Arrays.asList(observer));
    }

    private HashMap<String, Object> buildParams(final Object data) {
        HashMap<String, Object> params = new HashMap<String, Object>() {{
            put(DATA_KEY, data);
        }};

        return params;
    }

    private boolean canDownload() {
        if (!Globals.allowDownload(context)) {
            return false;
        }

        if (((System.currentTimeMillis() - lastPOINetDownload) < Constants.MINIMUM_CHECK_INTERVAL_MILLISECONDS) && isDownloading.get()) {
            return false;
        }

        lastPOINetDownload = System.currentTimeMillis();
        return true;
    }

    public boolean downloadAround(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double maxDistance, final Map<Long, GeoNode> poiMap, String countryIso) {
        if (!canDownload()) {
            return false;
        }

        double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
        double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

        return downloadBBox(pDecLatitude - deltaLatitude,
                pDecLongitude - deltaLongitude,
                pDecLatitude + deltaLatitude,
                pDecLongitude + deltaLongitude, poiMap, countryIso);
    }

    public boolean downloadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast, final Map<Long, GeoNode> poiMap, final String countryIso) {
        if (!canDownload()) {
            return false;
        }

        final HashMap<String, Object> params = buildParams(poiMap);

        notifyObservers(10, false, params);
        (new Thread() {
            public void run() {
                isDownloading.set(true);

                String formData = String.format(Locale.getDefault(),
                        "[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing$\"~\"route_bottom\"](%f,%f,%f,%f);out body;",
                        latSouth, longWest, latNorth, longEast);

                RequestBody body = new FormBody.Builder().add("data", formData).build();
                Request request = new Request.Builder()
                        .url("http://overpass-api.de/api/interpreter")
                        .post(body)
                        .build();
                notifyObservers(50, false, params);
                try (Response response = Constants.httpClient.newCall(request).execute()) {
                    notifyObservers(100, buildPOIsMapFromJsonString(response.body().string(), poiMap, countryIso), params);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                } finally {
                    isDownloading.set(false);
                }
            }
        }).start();

        return true;
    }

    private boolean buildPOIsMapFromJsonString(String data, Map<Long, GeoNode> poiMap, String countryIso) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        boolean newNode = false;

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong("id");
            if (poiMap.containsKey(nodeID)) {
                if (poiMap.get(nodeID).toJSONString().equalsIgnoreCase(nodeInfo.toString())) {
                    continue;
                }
            }

            GeoNode tmpPoi = new GeoNode(nodeInfo);
            tmpPoi.countryIso = countryIso;
            poiMap.put(nodeID, tmpPoi);
            newNode = true;
        }
        return newNode;
    }

    private void notifyObservers(int progress, boolean hasChanges, Map<String, Object> results) {
        for (INodesFetchingEventListener i: observers) {
            i.onProgress(progress, hasChanges, results);
        }
    }

    public boolean loadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast, final Map<Long, GeoNode> poiMap) {
        final HashMap<String, Object> params = buildParams(poiMap);

        notifyObservers(10, false, params);
        (new Thread() {
            public void run() {
                List<GeoNode> dbNodes = Globals.appDB.nodeDao().loadBBox(latSouth, longWest, latNorth, longEast);
                boolean isDirty = false;
                for (GeoNode node: dbNodes) {
                    if (!poiMap.containsKey(node.getID())) {
                        poiMap.put(node.getID(), node);
                        isDirty = true;
                    }
                }

                notifyObservers(100, isDirty, params);
            }
        }).start();

        return true;
    }

    public boolean pushToDb(final Map<Long, GeoNode> poiMap)
    {
        final HashMap<String, Object> params = buildParams(poiMap);

        (new Thread() {
            public void run() {
                Globals.appDB.nodeDao().insertNodes(poiMap.values().toArray(new GeoNode[poiMap.size()]));
                notifyObservers(100, false, params);
            }
        }).start();
        return true;
    }

    private void initPoiFromResources() {
        InputStream is = context.getResources().openRawResource(R.raw.world_db);

        if (is == null) {
            return;
        }

        BufferedReader reader = null;
        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

        String line = "";
        try {
            StringBuilder responseStrBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                responseStrBuilder.append(line);
            }

            buildPOIsMapFromJsonString(responseStrBuilder.toString(), new HashMap<Long, GeoNode>(), "");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return;
        }

        return;
    }
}
