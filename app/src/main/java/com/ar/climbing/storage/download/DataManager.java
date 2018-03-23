package com.ar.climbing.storage.download;

import android.content.Context;

import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.AugmentedRealityUtils;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xyz on 3/9/18.
 */

public class DataManager {
    private Context context;
    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);

    public DataManager(Context pContext) {
        this.context = pContext;
    }

    public boolean downloadAround(final double pDecLatitude,
                                  final double pDecLongitude,
                                  final double pMetersAltitude,
                                  final double maxDistance,
                                  final Map<Long, GeoNode> poiMap,
                                  String countryIso) {
        return downloadBBox(computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap, countryIso);
    }

    public boolean loadAround(final double pDecLatitude,
                              final double pDecLongitude,
                              final double pMetersAltitude,
                              final double maxDistance,
                              final Map<Long, GeoNode> poiMap,
                              String countryIso) {
        return loadBBox(computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap);
    }

    public boolean downloadBBox(final BoundingBox bBox,
                                           final Map<Long, GeoNode> poiMap,
                                           final String countryIso) {
        if (!canDownload()) {
            return false;
        }

        boolean isDirty = false;

        String formData = String.format(Locale.getDefault(),
                "[out:json][timeout:50];node[\"sport\"=\"climbing\"][~\"^climbing$\"~\"route_bottom\"](%f,%f,%f,%f);out body;",
                bBox.getLatSouth(), bBox.getLonWest(), bBox.getLatNorth(), bBox.getLonEast());

        RequestBody body = new FormBody.Builder().add("data", formData).build();
        Request request = new Request.Builder()
                .url("http://overpass-api.de/api/interpreter")
                .post(body)
                .build();
        try (Response response = Constants.httpClient.newCall(request).execute()) {
            isDirty = buildPOIsMapFromJsonString(response.body().string(), poiMap, countryIso);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return isDirty;
    }

    public boolean downloadIDs(final List<Long> nodeIDs, final Map<Long, GeoNode> poiMap) {
        if (!canDownload()) {
            return false;
        }

        boolean isDirty = false;

        StringBuilder idAsString = new StringBuilder();

        for (Long id: nodeIDs) {
            idAsString.append(Long.toString(id)).append(",");
        }
        if (idAsString.lastIndexOf(",") > 0) {
            idAsString.deleteCharAt(idAsString.lastIndexOf(","));
        } else {
            return false;
        }

        String formData = String.format(Locale.getDefault(),
                "[out:json][timeout:50];node(id:%s);out body;", idAsString);

        RequestBody body = new FormBody.Builder().add("data", formData).build();
        Request request = new Request.Builder()
                .url("http://overpass-api.de/api/interpreter")
                .post(body)
                .build();
        try (Response response = Constants.httpClient.newCall(request).execute()) {
            isDirty = buildPOIsMapFromJsonString(response.body().string(), poiMap, "");
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return isDirty;
    }

    public boolean loadBBox(final BoundingBox bBox,
                            final Map<Long, GeoNode> poiMap) {
        List<GeoNode> dbNodes = Globals.appDB.nodeDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest());
        boolean isDirty = false;
        for (GeoNode node: dbNodes) {
            if (!poiMap.containsKey(node.getID())) {
                poiMap.put(node.getID(), node);
                isDirty = true;
            }
        }
        return isDirty;
    }

    public void pushToDb(final Map<Long, GeoNode> poiMap, boolean replace) {
        if (replace) {
            Globals.appDB.nodeDao().insertNodesWithReplace(poiMap.values().toArray(new GeoNode[poiMap.size()]));
        } else {
            Globals.appDB.nodeDao().insertNodesWithIgnore(poiMap.values().toArray(new GeoNode[poiMap.size()]));
        }
    }

    public static BoundingBox computeBoundingBox(final double pDecLatitude,
                                                 final double pDecLongitude,
                                                 final double pMetersAltitude,
                                                 final double maxDistance) {
        double deltaLatitude = getDeltaLatitude(maxDistance);
        double deltaLongitude = getDeltaLongitude(maxDistance, pDecLatitude);
        return new BoundingBox(pDecLatitude + deltaLatitude,
                pDecLongitude + deltaLongitude,
                pDecLatitude - deltaLatitude,
                pDecLongitude - deltaLongitude);
    }

    private static double getDeltaLatitude(double maxDistance) {
        return Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
    }

    private static double getDeltaLongitude(double maxDistance, double decLatitude) {
        return Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(decLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));
    }

    private boolean buildPOIsMapFromJsonString(String data, Map<Long, GeoNode> poiMap, String countryIso) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        boolean newNode = false;

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong(GeoNode.ID_KEY);
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

    public boolean canDownload() {
        if (!Globals.allowDataDownload(context)) {
            return false;
        }

        if (((System.currentTimeMillis() - lastPOINetDownload) < Constants.MINIMUM_CHECK_INTERVAL_MILLISECONDS) && isDownloading.get()) {
            return false;
        }

        lastPOINetDownload = System.currentTimeMillis();
        return true;
    }

//    private void initPoiFromResources() {
//        InputStream is = context.getResources().openRawResource(R.raw.world_db);
//
//        if (is == null) {
//            return;
//        }
//
//        BufferedReader reader = null;
//        reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
//
//        String line = "";
//        try {
//            StringBuilder responseStrBuilder = new StringBuilder();
//            while ((line = reader.readLine()) != null) {
//                responseStrBuilder.append(line);
//            }
//
//            buildPOIsMapFromJsonString(responseStrBuilder.toString(), new HashMap<Long, GeoNode>(), "");
//        } catch (IOException | JSONException e) {
//            e.printStackTrace();
//            return;
//        }
//
//        return;
//    }
}
