package com.climbtheworld.app.storage;

import android.support.v7.app.AppCompatActivity;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.osm.MarkerGeoNode;
import com.climbtheworld.app.osm.OsmUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Quaternion;

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
    private AppCompatActivity parent;

    public DataManager(AppCompatActivity parent, boolean applyFilters) {
        this.useFilters = applyFilters;
        this.parent = parent;
        OkHttpClient httpClientBuilder = new OkHttpClient();
        OkHttpClient.Builder builder = httpClientBuilder.newBuilder().connectTimeout(Constants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS).readTimeout(Constants.HTTP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        httpClient = builder.build();
    }

    /**
     * Download nodes around the virtualCamera
     * @param center
     * @param maxDistance
     * @param poiMap
     * @param countryIso
     * @return If data has changes it will return true
     */
    public boolean downloadAround(final Quaternion center,
                                  final double maxDistance,
                                  final Map<Long, MarkerGeoNode> poiMap,
                                  String countryIso) throws IOException, JSONException {
        return downloadBBox(computeBoundingBox(center, maxDistance), poiMap, countryIso);
    }

    /**
     * Load points from the local storage around the provided location
     * @param center
     * @param maxDistance
     * @param poiMap
     * @return If data has changes it will return true
     */
    public boolean loadAround(final Quaternion center,
                              final double maxDistance,
                              final Map<Long, MarkerGeoNode> poiMap,
                              final GeoNode.NodeTypes... types) {
        return loadBBox(computeBoundingBox(center, maxDistance), poiMap, types);
    }

    public boolean downloadBBox(final BoundingBox bBox,
                                final Map<Long, MarkerGeoNode> poiMap,
                                final String countryIso) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        return downloadNodes(OsmUtils.buildBBoxQuery(bBox), poiMap, countryIso);
    }

    public boolean downloadCountry(final Map<Long, MarkerGeoNode> poiMap,
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
    public boolean downloadIDs(final List<Long> nodeIDs, final Map<Long, MarkerGeoNode> poiMap) throws IOException, JSONException {
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
                            final Map<Long, MarkerGeoNode> poiMap,
                            GeoNode.NodeTypes... types) {
        boolean isDirty = false;

        if (types == null || types.length == 0) {
            List<GeoNode> dbNodes = Globals.appDB.nodeDao().loadBBox(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest());
            for (GeoNode node : dbNodes) {
                if (!poiMap.containsKey(node.getID())) {
                    if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(node))) {
                        poiMap.put(node.getID(), new MarkerGeoNode(node));
                        isDirty = true;
                    }
                }
            }
        } else {
            for (GeoNode.NodeTypes type : types) {
                List<GeoNode> dbNodes = Globals.appDB.nodeDao().loadBBoxByType(bBox.getLatNorth(), bBox.getLonEast(), bBox.getLatSouth(), bBox.getLonWest(), type);
                for (GeoNode node : dbNodes) {
                    if (!poiMap.containsKey(node.getID())) {
                        if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(node))) {
                            poiMap.put(node.getID(), new MarkerGeoNode(node));
                            isDirty = true;
                        }
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
    public void pushToDb(final Map<Long, MarkerGeoNode> poiMap, boolean replace) {
        GeoNode[] toAdd = new GeoNode[poiMap.size()];

        int i = 0;
        for (MarkerGeoNode node: poiMap.values()) {
            toAdd[i++] = node.geoNode;
        }

        if (replace) {
            Globals.appDB.nodeDao().insertNodesWithReplace(toAdd);
        } else {
            Globals.appDB.nodeDao().insertNodesWithIgnore(toAdd);
        }
    }

    /**
     * Will compute a bounding box around the coordinates.
     * @param center
     * @param maxDistance
     * @return
     */
    public static BoundingBox computeBoundingBox(final Quaternion center,
                                                 final double maxDistance) {
        double deltaLatitude = getDeltaLatitude(maxDistance);
        double deltaLongitude = getDeltaLongitude(maxDistance, center.x);
        return new BoundingBox(center.x + deltaLatitude,
                center.y + deltaLongitude,
                center.x - deltaLatitude,
                center.y - deltaLongitude);
    }

    private static double getDeltaLatitude(double maxDistance) {
        return Math.toDegrees(maxDistance / AugmentedRealityUtils.EARTH_RADIUS_M);
    }

    private static double getDeltaLongitude(double maxDistance, double decLatitude) {
        return Math.toDegrees(maxDistance / (Math.cos(Math.toRadians(decLatitude)) * AugmentedRealityUtils.EARTH_RADIUS_M));
    }

    private boolean buildPOIsMapFromJsonString(String data, Map<Long, MarkerGeoNode> poiMap, String countryIso) throws JSONException {
        JSONObject jObject = new JSONObject(data);
        JSONArray jArray = jObject.getJSONArray("elements");

        boolean newNode = false;

        for (int i=0; i < jArray.length(); i++) {
            JSONObject nodeInfo = jArray.getJSONObject(i);
            //open street maps ID should be unique since it is a DB ID.
            long nodeID = nodeInfo.getLong(GeoNode.KEY_ID);
            if (poiMap.containsKey(nodeID)) {
                if (poiMap.get(nodeID).geoNode.toJSONString().equalsIgnoreCase(nodeInfo.toString())) {
                    continue;
                }
            }
            MarkerGeoNode tmpPoi = new MarkerGeoNode(new GeoNode(nodeInfo));
            if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(tmpPoi.geoNode))) {
                tmpPoi.geoNode.countryIso = countryIso;
                poiMap.put(nodeID, tmpPoi);
                newNode = true;
            }
        }
        return newNode;
    }

    protected boolean canDownload() {
        if (!Globals.allowDataDownload(parent)) {
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

    private boolean downloadNodes(String formData, Map<Long, MarkerGeoNode> poiMap, String countryIso) throws IOException, JSONException {
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
