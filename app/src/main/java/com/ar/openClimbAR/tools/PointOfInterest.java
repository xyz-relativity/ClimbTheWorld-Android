package com.ar.openClimbAR.tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest {
    enum POIType {observer, climbing, cardinal};
    private final POIType type;

    private float decimalLongitude;
    private float decimalLatitude;
    private float altitudeMeters;

    //climb topo
    private float lengthMeters = 0;
    private String name = "";
    private JSONObject tags;

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOILocation(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
    }

    public POIType getType() {
        return type;
    }

    public float getDecimalLongitude() {
        return decimalLongitude;
    }

    public float getDecimalLatitude() {
        return decimalLatitude;
    }

    public float getAltitudeMeters() {
        return altitudeMeters;
    }

    public String getName() {
        return name;
    }

    public JSONObject getTags() {
        return tags;
    }

    public int getLevel() {
        Iterator<String> keyIt = tags.keys();
        int result = 0;
        while (keyIt.hasNext()) {
            String key = keyIt.next().toLowerCase();
            if (key.startsWith("climbing:grade:")) {
                {
                    if (key.endsWith(":mean") && tags.has(key)) {
                        String grade = tags.optString(key, "");
                        return GradeConverter.getConverter().getGradeOrder(key.split(":")[2], grade);
                    } else if (key.endsWith(":max") && result!=0 && tags.has(key)) {
                        String grade = tags.optString(key, "");
                        result = GradeConverter.getConverter().getGradeOrder(key.split(":")[2], grade);
                    } else if (key.endsWith(":min") && result!=0 && tags.has(key)) {
                        String grade = tags.optString(key, "");
                        result = GradeConverter.getConverter().getGradeOrder(key.split(":")[2], grade);
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

    public void updatePOIInfo(String pName, JSONObject pTags)
    {
        this.name = pName;
        this.tags = pTags;
    }
}
