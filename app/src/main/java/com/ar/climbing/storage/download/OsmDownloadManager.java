package com.ar.climbing.storage.download;

import android.content.Context;

import com.ar.climbing.storage.database.Node;
import com.ar.climbing.utils.AugmentedRealityUtils;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;
import com.ar.climbing.utils.PointOfInterest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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

public class OsmDownloadManager {

    private Map<Long, PointOfInterest> poiMap;
    private Context context;
    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private List<IOsmDownloadEventListener> handler = new ArrayList<>();

    public OsmDownloadManager(Map<Long, PointOfInterest> allPOIs, Context currentContext) {
        this.poiMap = allPOIs;
        this.context = currentContext;
    }

    public void addListener(IOsmDownloadEventListener... pHandler) {
        for (IOsmDownloadEventListener i: pHandler) {
            handler.add(i);
        }
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
        return downloadAround(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance, null);
    }

    public boolean downloadAround(final double pDecLatitude, final double pDecLongitude, final double pMetersAltitude, final double maxDistance, final String countryIso) {
        if (!canDownload()) {
            return false;
        }

        double deltaLatitude = Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
        double deltaLongitude = Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(pDecLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));

        return downloadBBox(pDecLatitude - deltaLatitude,
                pDecLongitude - deltaLongitude,
                pDecLatitude + deltaLatitude,
                pDecLongitude + deltaLongitude, countryIso);
    }

    public boolean downloadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast) {
        return downloadBBox(latSouth, longWest, latNorth, longEast, null);
    }

    public boolean downloadBBox(final double latSouth, final double longWest, final double latNorth, final double longEast, final String countryIso) {
        if (!canDownload()) {
            return false;
        }

        notifyListeners(10, false, false);
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
                notifyListeners(50, false, false);
                try (Response response = Constants.httpClient.newCall(request).execute()) {
                    notifyListeners(100, true, buildPOIsMap(response.body().string(), countryIso));
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                } finally {
                    isDownloading.set(false);
                }
            }
        }).start();

        return true;
    }

    private boolean buildPOIsMap(String data, String countryIso) throws JSONException {
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

            PointOfInterest tmpPoi = new PointOfInterest(nodeInfo);
            Node tmpNode = new Node();
            tmpNode.osmID = tmpPoi.getID();
            tmpNode.degLat = tmpPoi.decimalLatitude;
            tmpNode.degLon = tmpPoi.decimalLongitude;
            tmpNode.metersElev = tmpPoi.elevationMeters;
            tmpNode.nodeInfo = tmpPoi.toJSONString();
            tmpNode.updateStatus = Node.CLEAN_STATE;
            tmpNode.updateDate = System.currentTimeMillis();
            tmpNode.countryIso = countryIso.toLowerCase();
            Globals.appDB.nodeDao().insertNodes(tmpNode);
            poiMap.put(nodeID, tmpPoi);
            newNode = true;
        }
        return newNode;
    }

    private void notifyListeners(int progress, boolean done, boolean hasChanges) {
        for (IOsmDownloadEventListener i: handler) {
            i.onProgress(progress, done, hasChanges);
        }
    }
}
