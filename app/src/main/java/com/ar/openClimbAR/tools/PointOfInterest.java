package com.ar.openClimbAR.tools;

import android.support.annotation.NonNull;

import com.ar.openClimbAR.R;
import com.ar.openClimbAR.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest implements Comparable {
    public enum  climbingStyle{
        sport(R.string.sport),
        boulder(R.string.boulder),
        toprope(R.string.topo),
        trad(R.string.trad),
        multipitch(R.string.multipitch),
        ice(R.string.ice),
        mixed(R.string.mixed),
        deepwater(R.string.deepwater);

        public int stringId;
        climbingStyle(int pStringId) {
            this.stringId = pStringId;
        }
    }

    public float decimalLongitude = 0;
    public float decimalLatitude = 0;
    public float elevationMeters = 0;
    public float distanceMeters = 0;
    public float deltaDegAzimuth = 0;
    public float difDegAngle = 0;

    // climb topo
    public String name = "";

    // raw node data
    private JSONObject nodeInfo;

    @Override
    public int compareTo(@NonNull Object o) {
        if (o instanceof PointOfInterest) {
            if (this.distanceMeters > ((PointOfInterest) o).distanceMeters) {
                return 1;
            }
            if (this.distanceMeters < ((PointOfInterest) o).distanceMeters) {
                return -1;
            }
        }
        return 0;
    }

    public PointOfInterest(String stringNodeInfo) throws JSONException {
        this(new JSONObject(stringNodeInfo));
    }

    public PointOfInterest(JSONObject jsonNodeInfo)
    {
        this.updatePOIInfo(jsonNodeInfo);

        this.updatePOILocation(Float.parseFloat(nodeInfo.optString("lat", "0")),
                Float.parseFloat(nodeInfo.optString("lon", "0")),
                Float.parseFloat(getTags().optString("ele", "0").replaceAll("[^\\d.]", "")));
    }

    public PointOfInterest(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        nodeInfo = new JSONObject();
        this.updatePOILocation(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }

    public String getNodeInfo() {
        return nodeInfo.toString();
    }

    public long getID() {
        return nodeInfo.optLong("id");
    }

    public String getDescription() {
        return getTags().optString("description", "");
    }

    public float getLengthMeters() {
        return (float) getTags().optDouble("climbing:length", 0);
    }

    public List<climbingStyle> getClimbingStyles() {
        List<climbingStyle> result = new ArrayList<>();

        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            String noCaseKey = key.toLowerCase();
            for (climbingStyle style : climbingStyle.values()) {
                if (noCaseKey.endsWith("climbing:" + style.toString()) && !getTags().optString(key) .equalsIgnoreCase("no")) {
                    result.add(style);
                }
            }
        }

        Collections.sort(result);

        return result;
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

    public void updatePOILocation(float pDecimalLatitude, float pDecimalLongitude, float pMetersAltitude)
    {
        this.decimalLongitude = pDecimalLongitude;
        this.decimalLatitude = pDecimalLatitude;
        this.elevationMeters = pMetersAltitude;

        try {
            nodeInfo.put("lat", this.decimalLatitude);
            nodeInfo.put("lon", this.decimalLongitude);
            JSONObject tags = nodeInfo.has("tags") ? nodeInfo.optJSONObject("tags") : new JSONObject();
            tags.put("ele", this.elevationMeters);
            nodeInfo.put("tags", tags);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updatePOIInfo(JSONObject pNodeInfo)
    {
        this.nodeInfo = pNodeInfo;
        this.name = getTags().optString("name", "id: " + nodeInfo.optString("id"));
    }

    private JSONObject getTags() {
        return nodeInfo.optJSONObject("tags");
    }
}
