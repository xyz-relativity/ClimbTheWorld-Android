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


    public enum DownloadOperation {
        BBOX_DOWNLOAD, DB_BBOX_LOAD, BD_PUSH, DB_INSTALLED_COUNTRIES
    }

    private Map<Long, GeoNode> poiMap;
    private Context context;
    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private List<INodesFetchingEventListener> handler = new ArrayList<>();

    public NodesFetchingManager(Map<Long, GeoNode> allPOIs, Context currentContext) {
        this.poiMap = allPOIs;
        this.context = currentContext;
    }

    public void addListener(INodesFetchingEventListener... pHandler) {
        handler.addAll(Arrays.asList(pHandler));
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

    public boolean downloadAround(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double maxDistance) {
        if (!canDownload()) {
            return false;
        }

        double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
        double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

        return downloadBBox(pDecLatitude - deltaLatitude,
                pDecLongitude - deltaLongitude,
                pDecLatitude + deltaLatitude,
                pDecLongitude + deltaLongitude);
    }

    public boolean downloadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast) {
        if (!canDownload()) {
            return false;
        }
        final HashMap<String, String> params = new HashMap<String, String>() {{
            put("operation",DownloadOperation.BBOX_DOWNLOAD.name());
        }};

        notifyListeners(10, false, params);
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
                notifyListeners(50, false, params);
                try (Response response = Constants.httpClient.newCall(request).execute()) {
                    notifyListeners(100, buildPOIsMapFromJsonString(response.body().string()), params);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                } finally {
                    isDownloading.set(false);
                }
            }
        }).start();

        return true;
    }

    private boolean buildPOIsMapFromJsonString(String data) throws JSONException {
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
            poiMap.put(nodeID, tmpPoi);
            newNode = true;
        }
        return newNode;
    }

    private void notifyListeners(int progress, boolean hasChanges, Map<String, String> parameters) {
        for (INodesFetchingEventListener i: handler) {
            i.onProgress(progress, hasChanges, parameters);
        }
    }

    public boolean loadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast) {
        final HashMap<String, String> params = new HashMap<String, String>() {{
            put("operation",DownloadOperation.DB_BBOX_LOAD.name());
        }};

        notifyListeners(10, false, params);
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

                notifyListeners(100, isDirty, params);
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

            buildPOIsMapFromJsonString(responseStrBuilder.toString());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return;
        }

        return;
    }
}
