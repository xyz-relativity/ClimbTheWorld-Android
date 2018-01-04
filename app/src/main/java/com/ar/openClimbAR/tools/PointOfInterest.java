package com.ar.openClimbAR.tools;

import android.support.annotation.NonNull;

import com.ar.openClimbAR.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest implements Comparable {
    public enum POIType {observer, climbing};
    public final POIType type;

    public float decimalLongitude = 0;
    public float decimalLatitude = 0;
    public float altitudeMeters = 0;
    public float distanceMeters = 0;
    public float deltaDegAzimuth = 0;
    public float difDegAngle = 0;

    //climb topo
    public String name = "";
    protected JSONObject nodeInfo;

    @Override
    public int compareTo(@NonNull Object o) {
        if (o instanceof PointOfInterest) {
            if (this.distanceMeters > ((PointOfInterest) o).distanceMeters) return 1;
            if (this.distanceMeters < ((PointOfInterest) o).distanceMeters) return -1;
            else return 0;
        }
        return 0;
    }

    public PointOfInterest(POIType pType, String stringNodeInfo) throws JSONException {
        this(pType, new JSONObject(stringNodeInfo));
    }

    public PointOfInterest(POIType pType, JSONObject jsonNodeInfo)
    {
        this.type = pType;
        this.updatePOIInfo(jsonNodeInfo.optString("name", "id: " + jsonNodeInfo.optString("id")), jsonNodeInfo);

        this.updatePOILocation(Float.parseFloat(nodeInfo.optString("lon", "0")),
                Float.parseFloat(nodeInfo.optString("lat", "0")),
                Float.parseFloat(getTags().optString("ele", "0").replaceAll("[^\\d.]", "")));
    }

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOILocation(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
    }

    public String getNodeInfo() {
        return nodeInfo.toString();
    }

    public String getDescription() {
        return getTags().optString("description", "");
    }

    public float getLengthMeters() {
        return (float) getTags().optDouble("climbing:length", 0);
    }

    public int getLevelId() {
        Iterator<String> keyIt = getTags().keys();
        int result = 0;
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            String noCaseKey = key.toLowerCase();
            if (noCaseKey.startsWith("climbing:grade:")) {
                {
                    if (noCaseKey.endsWith(":mean")) {
                        String grade = getTags().optString(key, Constants.UNKNOWN_GRADE_STRING);
                        return GradeConverter.getConverter().getGradeOrder(noCaseKey.split(":")[2], grade);
                    } else if (noCaseKey.endsWith(":max") && result==0) {
                        String grade = getTags().optString(key, Constants.UNKNOWN_GRADE_STRING);
                        result = GradeConverter.getConverter().getGradeOrder(noCaseKey.split(":")[2], grade);
                    } else if (noCaseKey.endsWith(":min") && result==0) {
                        String grade = getTags().optString(key, Constants.UNKNOWN_GRADE_STRING);
                        result = GradeConverter.getConverter().getGradeOrder(noCaseKey.split(":")[2], grade);
                    }
                }
            }
        }
        return result;
    }

    public void updatePOILocation(float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.decimalLongitude = pDecimalLongitude;
        this.decimalLatitude = pDecimalLatitude;
        this.altitudeMeters = pMetersAltitude;
    }

    public void updatePOIInfo(String pName, JSONObject pNodeInfo)
    {
        this.name = pName;
        this.nodeInfo = pNodeInfo;
    }

    private JSONObject getTags() {
        return nodeInfo.optJSONObject("tags");
    }
}
