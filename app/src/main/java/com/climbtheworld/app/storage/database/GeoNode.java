package com.climbtheworld.app.storage.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.NonNull;

import com.climbtheworld.app.R;
import com.climbtheworld.app.tools.DataConverter;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Constants;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by xyz on 2/8/18.
 */

@Entity (indices = {@Index(value = "decimalLatitude"), @Index(value = "decimalLongitude")})
@TypeConverters(DataConverter.class)
public class GeoNode implements Comparable {
    public static final int CLEAN_STATE = 0;
    public static final int TO_DELETE_STATE = 1;
    public static final int TO_UPDATE_STATE = 2;

    public static final String QUERY_ROUTE_BOTTOM = "node[\"sport\"=\"climbing\"][\"climbing\"=\"route_bottom\"]";
    public static final String QUERY_CLIMBING_CRAG = "node[\"sport\"=\"climbing\"][\"climbing\"=\"crag\"]";
    public static final String QUERY_CLIMBING_GYM = "node[\"sport\"=\"climbing\"][\"leisure\"=\"sports_centre\"]";

    private static final String KEY_SEPARATOR = ":";
    public static final String ID_KEY = "id";
    public static final String ROUTE_BOTTOM_KEY = "climbing";
    public static final String SPORT_KEY = "sport";
    public static final String NAME_KEY = "name";
    public static final String TAGS_KEY = "tags";
    public static final String LAT_KEY = "lat";
    public static final String LON_KEY = "lon";
    public static final String ELEVATION_KEY = "ele";
    public static final String CLIMBING_KEY = "climbing";
    public static final String LENGTH_KEY = CLIMBING_KEY + KEY_SEPARATOR +"length";
    public static final String DESCRIPTION_KEY = "description";
    public static final String GRADE_KEY = "grade";
    public static final String PITCHES_KEY = CLIMBING_KEY + KEY_SEPARATOR +"pitches";
    public static final String BOLTED_KEY = "bolted";

    public enum NodeTypes {
        route (QUERY_ROUTE_BOTTOM),
        crag (QUERY_CLIMBING_CRAG),
        artificial (QUERY_CLIMBING_GYM);

        public String overpassQuery;

        NodeTypes(String query) {
            overpassQuery = query;
        }
    }

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

    @PrimaryKey
    public long osmID;
    public String countryIso;

    //uses type converter
    public NodeTypes nodeType = NodeTypes.route;
    public long updateDate;
    public int localUpdateState = CLEAN_STATE;

    //uses type converter
    public JSONObject jsonNodeInfo;

    //This are kept as variables since they are accessed often during AR rendering.
    public double decimalLatitude = 0;
    public double decimalLongitude = 0;
    public double elevationMeters = 0;

    @Ignore
    public double distanceMeters = 0;
    @Ignore
    public double deltaDegAzimuth = 0;
    @Ignore
    public double difDegAngle = 0;


    @Override
    public int compareTo(@NonNull Object o) {
        if (o instanceof GeoNode) {
            if (this.distanceMeters > ((GeoNode) o).distanceMeters) {
                return 1;
            }
            if (this.distanceMeters < ((GeoNode) o).distanceMeters) {
                return -1;
            }
        }
        return 0;
    }

    public GeoNode(String stringNodeInfo) throws JSONException {
        this(new JSONObject(stringNodeInfo));
    }

    public GeoNode(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude)
    {
        this(new JSONObject());
        this.updatePOILocation(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
    }

    public GeoNode(JSONObject jsonNodeInfo)
    {
        this.setJSONData(jsonNodeInfo);

        this.updatePOILocation(Double.parseDouble(this.jsonNodeInfo.optString(LAT_KEY, "0")),
                Double.parseDouble(this.jsonNodeInfo.optString(LON_KEY, "0")),
                Double.parseDouble(getTags().optString(ELEVATION_KEY, "0").replaceAll("[^\\d.]", "")));

        this.osmID = this.jsonNodeInfo.optLong(ID_KEY, 0);
        this.updateDate = System.currentTimeMillis();
    }

    public String toJSONString() {
        return jsonNodeInfo.toString();
    }

    public long getID() {
        return jsonNodeInfo.optLong(ID_KEY, osmID);
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

    public Set<ClimbingStyle> getClimbingStyles() {
        Set<GeoNode.ClimbingStyle> result = new TreeSet<>();

        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
                if (key.equalsIgnoreCase(CLIMBING_KEY + KEY_SEPARATOR + style.toString())
                        && !getTags().optString(key).equalsIgnoreCase("no")) {
                    result.add(style);
                }
            }
        }

        return result;
    }

    public void setClimbingStyles(List<GeoNode.ClimbingStyle> styles) {
        Set<String> toDelete = new TreeSet<>();
        Iterator<String> keyIt = getTags().keys();
        while (keyIt.hasNext()) {
            String key = keyIt.next();
            for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
                if (key.equalsIgnoreCase(CLIMBING_KEY + KEY_SEPARATOR + style.toString())) {
                    if (styles.contains(style)) {
                        try {
                            getTags().put(CLIMBING_KEY + KEY_SEPARATOR + style.toString(), "yes");
                            styles.remove(style);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        toDelete.add(key);
                    }
                }
            }
        }

        for (String key: toDelete) {
            getTags().remove(key);
        }

        for (GeoNode.ClimbingStyle style: styles) {
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

    public boolean isBolted () {
        if (getTags().optString(BOLTED_KEY, "no").equalsIgnoreCase("yes")){
            return true;
        }
        return getTags().optBoolean(BOLTED_KEY, false);
    }

    public void setBolted (boolean isBolted) {
        try {
            if (isBolted) {
                getTags().put(BOLTED_KEY, "yes");
            } else {
                getTags().remove(BOLTED_KEY);
            }
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
            jsonNodeInfo.put(LAT_KEY, this.decimalLatitude);
            jsonNodeInfo.put(LON_KEY, this.decimalLongitude);
            if (elevationMeters != 0) {
                getTags().put(ELEVATION_KEY, this.elevationMeters);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setJSONData(JSONObject pNodeInfo)
    {
        this.jsonNodeInfo = pNodeInfo;

        try {
            this.getTags().put(ROUTE_BOTTOM_KEY, "route_bottom");
            this.getTags().put(SPORT_KEY, "climbing");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONObject getTags() {
        if (!jsonNodeInfo.has(TAGS_KEY)) {
            try {
                jsonNodeInfo.put(TAGS_KEY, new JSONObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonNodeInfo.optJSONObject(TAGS_KEY);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!GeoNode.class.isAssignableFrom(obj.getClass())) {
            return false;
        }

        final GeoNode other = (GeoNode) obj;
        if ((this.osmID) != (other.osmID)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
                        appendSuper(super.hashCode()).
                        append(this.osmID).
                        append(this.jsonNodeInfo).
                        toHashCode();
    }
    
}
