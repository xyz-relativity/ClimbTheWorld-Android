package com.climbtheworld.app.storage;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.osm.OsmUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xyz on 3/9/18.
 */

public class DataManager {
    private static int apiUrlOrder = 0;

    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private OkHttpClient httpClient;
    private boolean useFilters;

    DataManager(boolean applyFilters) {
        this.useFilters = applyFilters;
        OkHttpClient httpClientBuilder = new OkHttpClient();
        OkHttpClient.Builder builder = httpClientBuilder.newBuilder().connectTimeout(Constants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS).readTimeout(Constants.HTTP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        httpClient = builder.build();
    }

    /**
     * Download nodes around the virtualCamera
     * @param pDecLatitude
     * @param pDecLongitude
     * @param pMetersAltitude
     * @param maxDistance
     * @param poiMap
     * @param countryIso
     * @return If data has changes it will return true
     */
    public boolean downloadAround(final double pDecLatitude,
                                  final double pDecLongitude,
                                  final double pMetersAltitude,
                                  final double maxDistance,
                                  final Map<Long, GeoNode> poiMap,
                                  String countryIso) throws IOException, JSONException {
        return downloadBBox(computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap, countryIso);
    }

    /**
     * Load points from the local storage around the provided location
     * @param pDecLatitude
     * @param pDecLongitude
     * @param pMetersAltitude
     * @param maxDistance
     * @param poiMap
     * @return If data has changes it will return true
     */
    public boolean loadAround(final double pDecLatitude,
                              final double pDecLongitude,
                              final double pMetersAltitude,
                              final double maxDistance,
                              final Map<Long, GeoNode> poiMap,
                              final GeoNode.NodeTypes type) {
        return loadBBox(computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap, type);
    }

    public boolean downloadBBox(final BoundingBox bBox,
                                final Map<Long, GeoNode> poiMap,
                                final String countryIso) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        return downloadNodes(OsmUtils.buildBBoxQuery(bBox), poiMap, countryIso);
    }

    public boolean downloadCountry(final Map<Long, GeoNode> poiMap,
                                   final String countryIso) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        return downloadNodes(OsmUtils.buildCountryQuery(countryIso), poiMap, countryIso);
    }

    /**
     * Takes a list of node IDs and will download the node data.
     * @param nodeIDs
     * @param poiMap
     * @return
     */
    public boolean downloadIDs(final List<Long> nodeIDs, final Map<Long, GeoNode> poiMap) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        StringBuilder idAsString = new StringBuilder();

        for (Long id: nodeIDs) {
            idAsString.append(Long.toString(id)).append(",");
        }
        if (idAsString.lastIndexOf(",") > 0) {
            idAsString.deleteCharAt(idAsString.lastIndexOf(","));
        } else {
            return false;
        }

        return downloadNodes(OsmUtils.buildPoiQueryForType(idAsString.toString()), poiMap, "");
    }



    /**
     * Loads point inside a bounding box form the database.
     * @param bBox
     * @param poiMap
     * @return
     */
    public boolean loadBBox(final BoundingBox bBox,
                            final Map<Long, GeoNode> poiMap,
                            GeoNode.NodeTypes... types) {
        boolean isDirty = false;
        for (GeoNode.NodeTypes type: types) {
            List<GeoNode> dbNodes = Globals.appDB.nodeDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest(), type);
            for (GeoNode node : dbNodes) {
                if (!poiMap.containsKey(node.getID())) {
                    if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(node))) {
                        poiMap.put(node.getID(), node);
                        isDirty = true;
                    }
                }
            }
        }
        return isDirty;
    }

    /**
     * Saves data to the db.
     * @param poiMap
     * @param replace
     */
    public void pushToDb(final Map<Long, GeoNode> poiMap, boolean replace) {
        if (replace) {
            Globals.appDB.nodeDao().insertNodesWithReplace(poiMap.values().toArray(new GeoNode[0]));
        } else {
            Globals.appDB.nodeDao().insertNodesWithIgnore(poiMap.values().toArray(new GeoNode[0]));
        }
    }

    /**
     * Will compute a bounding box around the coordinates.
     * @param pDecLatitude
     * @param pDecLongitude
     * @param pMetersAltitude
     * @param maxDistance
     * @return
     */
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
            long nodeID = nodeInfo.getLong(GeoNode.KEY_ID);
            if (poiMap.containsKey(nodeID)) {
                if (poiMap.get(nodeID).toJSONString().equalsIgnoreCase(nodeInfo.toString())) {
                    continue;
                }
            }
            GeoNode tmpPoi = new GeoNode(nodeInfo);
            if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(tmpPoi))) {
                tmpPoi.countryIso = countryIso;
                tmpPoi.nodeType = GeoNode.NodeTypes.getNodeTypeFromJson(tmpPoi);
                poiMap.put(nodeID, tmpPoi);
                newNode = true;
            }
        }
        return newNode;
    }

    protected boolean canDownload() {
        if (!Globals.allowDataDownload()) {
            return false;
        }

        if (((System.currentTimeMillis() - lastPOINetDownload) < Constants.MINIMUM_CHECK_INTERVAL_MILLISECONDS) && isDownloading.get()) {
            return false;
        }

        lastPOINetDownload = System.currentTimeMillis();
        return true;
    }

    private String getApiUrl() {
        apiUrlOrder = (apiUrlOrder + 1) % Constants.OVERPASS_API.length;
        return Constants.OVERPASS_API[apiUrlOrder];
    }

    private boolean downloadNodes(String formData, Map<Long, GeoNode> poiMap, String countryIso) throws IOException, JSONException {
        boolean isDirty = false;

        RequestBody body = new FormBody.Builder().add("data", formData).build();
        Request request = new Request.Builder()
                .url(getApiUrl())
                .post(body)
                .build();
        Response response = httpClient.newCall(request).execute();
        isDirty = buildPOIsMapFromJsonString(response.body().string(), poiMap, countryIso);
        return isDirty;
    }
}
