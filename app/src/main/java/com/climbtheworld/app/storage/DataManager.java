package com.climbtheworld.app.storage;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
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

/*
[out:json][timeout:60];node["sport"="climbing"]["leisure"!="sports_centre"]["climbing"!="route_bottom"]["climbing"!="route_top"]["climbing"!="route"]["climbing"!="crag"][!"shop"]["leisure"!="pitch"]({{bbox}});out body meta;
 */

public class DataManager {
    private static final long HTTP_TIMEOUT_SECONDS = 240;

    //Get all climbing routes: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}});out body meta;
    //Get all climbing routes that were not done by me: [out:json][timeout:60];node["sport"="climbing"][~"^climbing$"~"route_bottom"]({{bbox}})->.newnodes; (.newnodes; - node.newnodes(user:xyz32);)->.newnodes; .newnodes out body meta;
    //Get all states: [out:json][timeout:60];node["place"="state"]({{bbox}});out body meta;
    //Get all countries: [out:json][timeout:60];node["place"="country"]({{bbox}});out body meta;

    private static final String DOWNLOAD_BBOX_QUERY = "[out:json][timeout:" + HTTP_TIMEOUT_SECONDS + "];%s(%f,%f,%f,%f);out body meta;";
    private static final String DOWNLOAD_COUNTRY_QUERY = "[out:json][timeout:" + HTTP_TIMEOUT_SECONDS + "];area[type=boundary][\"ISO3166-1\"=\"%s\"]->.searchArea;(%s(%f,%f,%f,%f)(area.searchArea););out body meta;";
    private static final String DOWNLOAD_NODES_QUERY = "[out:json][timeout:" + HTTP_TIMEOUT_SECONDS + "];node(id:%s);out body;";
    private long lastPOINetDownload = 0;
    private AtomicBoolean isDownloading = new AtomicBoolean(false);
    private OkHttpClient httpClient;
    private boolean useFilters;

    DataManager(boolean applyFilters) {
        this.useFilters = applyFilters;
        OkHttpClient httpClientBuilder = new OkHttpClient();
        OkHttpClient.Builder builder = httpClientBuilder.newBuilder().connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS).readTimeout(HTTP_TIMEOUT_SECONDS,
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
                                  String countryIso,
                                  GeoNode.NodeTypes type) throws IOException, JSONException {
        return downloadBBox(computeBoundingBox(pDecLatitude, pDecLongitude, pMetersAltitude, maxDistance), poiMap, countryIso, type);
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
                                final String countryIso,
                                final GeoNode.NodeTypes type) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        String formData = String.format(Locale.getDefault(), DOWNLOAD_BBOX_QUERY,
                type.overpassQuery,
                bBox.getLatSouth(),
                bBox.getLonWest(),
                bBox.getLatNorth(),
                bBox.getLonEast());

        return downloadNodes(formData, poiMap, countryIso, type);
    }

    public boolean downloadCountry(final BoundingBox bBox,
                                   final Map<Long, GeoNode> poiMap,
                                   final String countryIso,
                                   final GeoNode.NodeTypes type) throws IOException, JSONException {
        if (!canDownload()) {
            return false;
        }

        String formData = String.format(Locale.getDefault(), DOWNLOAD_COUNTRY_QUERY,
                countryIso.toUpperCase(),
                type.overpassQuery,
                bBox.getLatSouth(),
                bBox.getLonWest(),
                bBox.getLatNorth(),
                bBox.getLonEast());

        return downloadNodes(formData, poiMap, countryIso, type);
    }

    /**
     * Takes a list of node IDs and will download the node data.
     * @param nodeIDs
     * @param poiMap
     * @return
     */
    public boolean downloadIDs(final List<Long> nodeIDs, final Map<Long, GeoNode> poiMap, GeoNode.NodeTypes type) throws IOException, JSONException {
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

        String formData = String.format(Locale.getDefault(),
                DOWNLOAD_NODES_QUERY, idAsString);

        return downloadNodes(formData, poiMap, "", type);
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
            Globals.appDB.nodeDao().insertNodesWithReplace(poiMap.values().toArray(new GeoNode[poiMap.size()]));
        } else {
            Globals.appDB.nodeDao().insertNodesWithIgnore(poiMap.values().toArray(new GeoNode[poiMap.size()]));
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

    private boolean buildPOIsMapFromJsonString(String data, Map<Long, GeoNode> poiMap, String countryIso, GeoNode.NodeTypes type) throws JSONException {
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
            if ((!useFilters) || (useFilters && NodeDisplayFilters.canAdd(tmpPoi))) {
                tmpPoi.countryIso = countryIso;
                tmpPoi.nodeType = type;
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

    private boolean downloadNodes(String formData, Map<Long, GeoNode> poiMap, String countryIso, GeoNode.NodeTypes type) throws IOException, JSONException {
        boolean isDirty = false;

        RequestBody body = new FormBody.Builder().add("data", formData).build();
        Request request = new Request.Builder()
                .url(Constants.OVERPASS_API)
                .post(body)
                .build();
        Response response = httpClient.newCall(request).execute();
        isDirty = buildPOIsMapFromJsonString(response.body().string(), poiMap, countryIso, type);
        return isDirty;
    }
}
