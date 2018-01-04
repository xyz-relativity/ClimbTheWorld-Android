package com.ar.openClimbAR.tools;

import android.support.annotation.NonNull;

import com.ar.openClimbAR.utils.Constants;
import org.json.JSONObject;
import java.util.Iterator;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest implements Comparable {
    enum POIType {observer, climbing, cardinal};
    public final POIType type;

    public float decimalLongitude = 0;
    public float decimalLatitude = 0;
    public float altitudeMeters = 0;
    public float distance = 0;
    public float deltaDegAzimuth = 0;
    public float difDegAngle = 0;

    //climb topo
    public String name = "";

    protected JSONObject tags;

    @Override
    public int compareTo(@NonNull Object o) {
        if (o instanceof PointOfInterest) {
            if (this.distance > ((PointOfInterest) o).distance) return 1;
            if (this.distance < ((PointOfInterest) o).distance) return -1;
            else return 0;
        }
        return 0;
    }

    public PointOfInterest(POIType pType, float pDecimalLongitude, float pDecimalLatitude, float pMetersAltitude)
    {
        this.type = pType;
        this.updatePOILocation(pDecimalLongitude, pDecimalLatitude, pMetersAltitude);
    }

    public JSONObject getTags() {
        return tags;
    }

    public String getDescription() {
        return tags.optString("description", "");
    }

    public int getLevel() {
        Iterator<String> keyIt = tags.keys();
        int result = 0;
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            String noCaseKey = key.toLowerCase();
            if (noCaseKey.startsWith("climbing:grade:")) {
                {
                    if (noCaseKey.endsWith(":mean")) {
                        String grade = tags.optString(key, Constants.UNKNOWN_GRADE_STRING);
                        return GradeConverter.getConverter().getGradeOrder(noCaseKey.split(":")[2], grade);
                    } else if (noCaseKey.endsWith(":max") && result==0) {
                        String grade = tags.optString(key, Constants.UNKNOWN_GRADE_STRING);
                        result = GradeConverter.getConverter().getGradeOrder(noCaseKey.split(":")[2], grade);
                    } else if (noCaseKey.endsWith(":min") && result==0) {
                        String grade = tags.optString(key, Constants.UNKNOWN_GRADE_STRING);
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

    public void updatePOIInfo(String pName, JSONObject pTags)
    {
        this.name = pName;
        this.tags = pTags;
    }
}
