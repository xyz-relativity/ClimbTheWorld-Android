package com.ar.openClimbAR.tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest {
    enum POIType {observer, climbing, cardinal};
    public final POIType type;

    public float decimalLongitude;
    public float decimalLatitude;
    public float altitudeMeters;

    //climb topo
    public String name = "";

    protected JSONObject tags;

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOILocation(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
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
                    if (key.endsWith(":mean")) {
                        String grade = tags.optString(key, "");
                        return GradeConverter.getConverter().getGradeOrder(key.split(":")[2], grade);
                    } else if (key.endsWith(":max") && result==0) {
                        String grade = tags.optString(key, "");
                        result = GradeConverter.getConverter().getGradeOrder(key.split(":")[2], grade);
                    } else if (key.endsWith(":min") && result==0) {
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
