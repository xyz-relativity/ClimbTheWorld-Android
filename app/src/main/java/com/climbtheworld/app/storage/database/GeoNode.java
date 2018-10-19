package com.climbtheworld.app.storage.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.NonNull;
import android.util.Pair;

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

    private static final String KEY_SEPARATOR = ":";
    public static final String KEY_ID = "id";
    public static final String KEY_SPORT = "sport";
    public static final String KEY_NAME = "name";
    public static final String KEY_TAGS = "tags";
    public static final String KEY_LAT = "lat";
    public static final String KEY_LON = "lon";
    public static final String KEY_ELEVATION = "ele";
    public static final String KEY_CLIMBING = "climbing";
    public static final String KEY_LEISURE = "leisure";
    public static final String KEY_TOWER = "tower";
    public static final String KEY_TOWER_TYPE = KEY_TOWER + KEY_SEPARATOR + "type";
    public static final String KEY_LENGTH = KEY_CLIMBING + KEY_SEPARATOR + "length";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_CONTACT = "contact";
    public static final String KEY_CONTACT_WEBSITE = KEY_CONTACT + KEY_SEPARATOR + "website";
    public static final String KEY_GRADE = "grade";
    public static final String KEY_PITCHES = KEY_CLIMBING + KEY_SEPARATOR + "pitches";
    public static final String KEY_BOLTED = "bolted";

    public enum NodeTypes {
        route(R.string.route, new Pair<String, String>(KEY_CLIMBING, "route_bottom")),
        crag(R.string.crag, new Pair<String, String>(KEY_CLIMBING, "crag")),
        artificial(R.string.artificial, new Pair<String, String>(KEY_LEISURE, "sports_centre"), new Pair<String, String>(KEY_TOWER_TYPE, "climbing")),
        unknown(R.string.artificial);

        public int stringId;
        private Pair<String, String>[] jsonFilters;
        @SafeVarargs
        NodeTypes(int pStringId, Pair<String, String> ... jsonFilters) {
            this.jsonFilters = jsonFilters;
            this.stringId = pStringId;
        }

        public static NodeTypes getNodeTypeFromJson(GeoNode json) {
            JSONObject tags = json.getTags();
            try {
                for (NodeTypes type: NodeTypes.values()) {
                    for (Pair toCheck: type.jsonFilters) {
                        if (tags.getString((String)toCheck.first).equalsIgnoreCase((String)toCheck.second)) {
                            return type;
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return NodeTypes.unknown;
        };
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

        this.updatePOILocation(Double.parseDouble(this.jsonNodeInfo.optString(KEY_LAT, "0")),
                Double.parseDouble(this.jsonNodeInfo.optString(KEY_LON, "0")),
                Double.parseDouble(getTags().optString(KEY_ELEVATION, "0").replaceAll("[^\\d.]", "")));

        this.osmID = this.jsonNodeInfo.optLong(KEY_ID, 0);
        this.updateDate = System.currentTimeMillis();
    }

    public String toJSONString() {
        return jsonNodeInfo.toString();
    }

    public long getID() {
        return jsonNodeInfo.optLong(KEY_ID, osmID);
    }

    public String getDescription() {
        return getTags().optString(KEY_DESCRIPTION, "");
    }

    public void setDescription(String pDescription) {
        try {
            getTags().put(KEY_DESCRIPTION, pDescription);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getWebsite() {
        return getTags().optString(KEY_CONTACT_WEBSITE, "");
    }

    public void setWebsite(String pWebsite) {
        try {
            getTags().put(KEY_CONTACT_WEBSITE, pWebsite);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public double getLengthMeters() {
        return getTags().optDouble(KEY_LENGTH, 0);
    }

    public void setLengthMeters(double pLengthMeters) {
        try {
            getTags().put(KEY_LENGTH, pLengthMeters);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getName() {
        return getTags().optString(KEY_NAME, "");
    }

    public void setName (String pName) {
        try {
            getTags().put(KEY_NAME, pName);
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
                if (key.equalsIgnoreCase(KEY_CLIMBING + KEY_SEPARATOR + style.toString())
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
                if (key.equalsIgnoreCase(KEY_CLIMBING + KEY_SEPARATOR + style.toString())) {
                    if (styles.contains(style)) {
                        try {
                            getTags().put(KEY_CLIMBING + KEY_SEPARATOR + style.toString(), "yes");
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
                getTags().put(KEY_CLIMBING + KEY_SEPARATOR + style.toString(), "yes");
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
            if (noCaseKey.startsWith(KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR)) {
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
            if (noCaseKey.startsWith(KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR)) {
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
            getTags().put((KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + Constants.STANDARD_SYSTEM).toLowerCase(), gradeInStandardSystem);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean isBolted () {
        if (getTags().optString(KEY_BOLTED, "no").equalsIgnoreCase("yes")){
            return true;
        }
        return getTags().optBoolean(KEY_BOLTED, false);
    }

    public void setBolted (boolean isBolted) {
        try {
            if (isBolted) {
                getTags().put(KEY_BOLTED, "yes");
            } else {
                getTags().remove(KEY_BOLTED);
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
            jsonNodeInfo.put(KEY_LAT, this.decimalLatitude);
            jsonNodeInfo.put(KEY_LON, this.decimalLongitude);
            if (elevationMeters != 0) {
                getTags().put(KEY_ELEVATION, this.elevationMeters);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setJSONData(JSONObject pNodeInfo)
    {
        this.jsonNodeInfo = pNodeInfo;
    }

    private JSONObject getTags() {
        if (!jsonNodeInfo.has(KEY_TAGS)) {
            try {
                jsonNodeInfo.put(KEY_TAGS, new JSONObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonNodeInfo.optJSONObject(KEY_TAGS);
    }

    public void setTags(JSONObject tags) {
        try {
            jsonNodeInfo.put(KEY_TAGS, tags);
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
        return (this.osmID) == (other.osmID);
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
