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

/**
 * Created by xyz on 11/30/17.
 */

public class PointOfInterest implements Comparable {
    private static final String KEY_SEPARATOR = ":";
    private static final String ID_KEY = "id";
    private static final String NAME_KEY = "name";
    private static final String TAGS_KEY = "tags";
    private static final String LAT_KEY = "lat";
    private static final String LON_KEY = "lon";
    private static final String ELEVATION_KEY = "ele";
    private static final String CLIMBING_KEY = "climbing";
    private static final String LENGTH_KEY = CLIMBING_KEY + KEY_SEPARATOR +"length";
    private static final String DESCRIPTION_KEY = "description";
    private static final String GRADE_KEY = "grade";
    private static final String PITCHES_KEY = CLIMBING_KEY + KEY_SEPARATOR +"pitches";

    public enum ClimbingStyle {
        sport(R.string.sport),
        boulder(R.string.boulder),
        toprope(R.string.toprope),
        trad(R.string.trad),
        multipitch(R.string.multipitch),
        ice(R.string.ice),
        mixed(R.string.mixed),
        deepwater(R.string.deepwater);

        public int stringId;
        ClimbingStyle(int pStringId) {
            this.stringId = pStringId;
        }
    }

    //This are kept as variables since they are accessed often during AR rendering.
    public double decimalLongitude = 0;
    public double decimalLatitude = 0;
    public double elevationMeters = 0;
    public double distanceMeters = 0;
    public double deltaDegAzimuth = 0;
    public double difDegAngle = 0;

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

        this.updatePOILocation(Double.parseDouble(nodeInfo.optString(LAT_KEY, "0")),
                Double.parseDouble(nodeInfo.optString(LON_KEY, "0")),
                Double.parseDouble(getTags().optString(ELEVATION_KEY, "0").replaceAll("[^\\d.]", "")));
    }

    public PointOfInterest(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude)
    {
        nodeInfo = new JSONObject();
        this.updatePOILocation(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }

    public String toJSONString() {
        return nodeInfo.toString();
    }

    public long getID() {
        return nodeInfo.optLong(ID_KEY);
    }

    public String getDescription() {
        return getTags().optString(DESCRIPTION_KEY, "");
    }

    public void setDescription(String pDescription) {
        try {
            getTags().put(DESCRIPTION_KEY, pDescription);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public double getLengthMeters() {
        return getTags().optDouble(LENGTH_KEY, 0);
    }

    public void setLengthMeters(double pLengthMeters) {
        try {
            getTags().put(LENGTH_KEY, pLengthMeters);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return getTags().optString(NAME_KEY, "");
    }

    public void setName (String pName) {
        try {
            getTags().put(NAME_KEY, pName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public List<ClimbingStyle> getClimbingStyles() {
        List<ClimbingStyle> result = new ArrayList<>();

        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            for (ClimbingStyle style : ClimbingStyle.values()) {
                if (key.equalsIgnoreCase(CLIMBING_KEY + KEY_SEPARATOR + style.toString())
                        && !getTags().optString(key).equalsIgnoreCase("no")) {
                    result.add(style);
                }
            }
        }

        Collections.sort(result);
        return result;
    }

    public void setClimbingStyles(List<ClimbingStyle> styles) {
        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            for (ClimbingStyle style : ClimbingStyle.values()) {
                if (key.equalsIgnoreCase(CLIMBING_KEY + KEY_SEPARATOR + style.toString())) {
                    if (styles.contains(style)) {
                        try {
                            getTags().put(CLIMBING_KEY + KEY_SEPARATOR + style.toString(), "yes");
                            styles.remove(style);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        getTags().remove(key);
                    }
                }
            }
        }

        for (ClimbingStyle style: styles) {
            try {
                getTags().put(CLIMBING_KEY + KEY_SEPARATOR + style.toString(), "yes");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public int getLevelId() {
        Iterator<String> keyIt = getTags().keys();
        int result = 0;
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            String noCaseKey = key.toLowerCase();
            if (noCaseKey.startsWith(CLIMBING_KEY + KEY_SEPARATOR + GRADE_KEY + KEY_SEPARATOR)) {
                String[] keySplit = noCaseKey.split(":");
                if (keySplit.length == 3) {
                    String grade = getTags().optString(key, Constants.UNKNOWN_GRADE_STRING);
                    return GradeConverter.getConverter().getGradeOrder(keySplit[2], grade);
                }
            }
        }
        return result;
    }

    public void setLevelFromID(int id) {
        List<String> toRemove = new ArrayList<>();

        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            String noCaseKey = key.toLowerCase();
            if (noCaseKey.startsWith(CLIMBING_KEY + KEY_SEPARATOR + GRADE_KEY + KEY_SEPARATOR)) {
                String[] keySplit = noCaseKey.split(KEY_SEPARATOR);
                if (keySplit.length == 3) {
                    toRemove.add(key);
                }
            }
        }

        for (String item: toRemove) {
            getTags().remove(item);
        }

        try {
            String gradeInStandardSystem = GradeConverter.getConverter().getGradeFromOrder(Constants.STANDARD_SYSTEM, id);
            getTags().put((CLIMBING_KEY + KEY_SEPARATOR + GRADE_KEY + KEY_SEPARATOR + Constants.STANDARD_SYSTEM).toLowerCase(), gradeInStandardSystem);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updatePOILocation(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude)
    {
        this.decimalLongitude = pDecimalLongitude;
        this.decimalLatitude = pDecimalLatitude;
        this.elevationMeters = pMetersAltitude;

        try {
            nodeInfo.put(LAT_KEY, this.decimalLatitude);
            nodeInfo.put(LON_KEY, this.decimalLongitude);
            getTags().put(ELEVATION_KEY, this.elevationMeters);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updatePOIInfo(JSONObject pNodeInfo)
    {
        this.nodeInfo = pNodeInfo;
    }

    private JSONObject getTags() {
        if (!nodeInfo.has(TAGS_KEY)) {
            try {
                nodeInfo.put(TAGS_KEY, new JSONObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return nodeInfo.optJSONObject(TAGS_KEY);
    }
}
