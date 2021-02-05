package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.climbtheworld.app.ClimbTheWorld;
import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Constants;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by xyz on 2/8/18.
 */

@Entity(indices = {@Index(value = "decimalLatitude"), @Index(value = "decimalLongitude")})
@TypeConverters(DataConverter.class)
public class GeoNode implements Comparable {
	public static final int CLEAN_STATE = 0;
	public static final int TO_DELETE_STATE = 1;
	public static final int TO_UPDATE_STATE = 2;

	public static String UNKNOWN_GRADE_STRING = "?";

	public static final String KEY_SEPARATOR = ":";
	public static final String KEY_ID = "id";
	public static final String KEY_TYPE = "type";
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
	public static final String KEY_MAN_MADE = "man_made";
	public static final String KEY_LENGTH = KEY_CLIMBING + KEY_SEPARATOR + "length";
	public static final String KEY_MIN_LENGTH = KEY_LENGTH + KEY_SEPARATOR + "min";
	public static final String KEY_MAX_LENGTH = KEY_LENGTH + KEY_SEPARATOR + "max";
	public static final String KEY_PITCHES = KEY_CLIMBING + KEY_SEPARATOR + "pitches";
	public static final String KEY_BOLTS = KEY_CLIMBING + KEY_SEPARATOR + "bolts";
	public static final String KEY_ROUTES = KEY_CLIMBING + KEY_SEPARATOR + "routes";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_CONTACT = "contact";
	public static final String KEY_WEBSITE = "website";
	public static final String KEY_CONTACT_WEBSITE = KEY_CONTACT + KEY_SEPARATOR + KEY_WEBSITE;
	public static final String KEY_PHONE = "phone";
	public static final String KEY_CONTACT_PHONE = KEY_CONTACT + KEY_SEPARATOR + KEY_PHONE;
	public static final String KEY_ADDRESS = "addr";
	public static final String KEY_STREETNO = "housenumber";
	public static final String KEY_ADDR_STREETNO = KEY_ADDRESS + KEY_SEPARATOR + KEY_STREETNO;
	public static final String KEY_STREET = "street";
	public static final String KEY_ADDR_STREET = KEY_ADDRESS + KEY_SEPARATOR + KEY_STREET;
	public static final String KEY_UNIT = "unit";
	public static final String KEY_ADDR_UNIT = KEY_ADDRESS + KEY_SEPARATOR + KEY_UNIT;
	public static final String KEY_CITY = "city";
	public static final String KEY_ADDR_CITY = KEY_ADDRESS + KEY_SEPARATOR + KEY_CITY;
	public static final String KEY_PROVINCE = "province";
	public static final String KEY_ADDR_PROVINCE = KEY_ADDRESS + KEY_SEPARATOR + KEY_PROVINCE;
	public static final String KEY_POSTCODE = "postcode";
	public static final String KEY_ADDR_POSTCODE = KEY_ADDRESS + KEY_SEPARATOR + KEY_POSTCODE;
	public static final String KEY_GRADE = "grade";
	public static final String KEY_GRADE_TAG = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s";
	public static final String KEY_MIN = "min";
	public static final String KEY_GRADE_TAG_MIN = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MIN;
	public static final String KEY_MAX = "max";
	public static final String KEY_GRADE_TAG_MAX = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MAX;
	public static final String KEY_MEAN = "mean";
	public static final String KEY_GRADE_TAG_MEAN = KEY_CLIMBING + KEY_SEPARATOR + KEY_GRADE + KEY_SEPARATOR + "%s" + KEY_SEPARATOR + KEY_MEAN;
	public static final String KEY_BOLTED = "bolted";

	public enum Type {
		node, way, relation;

		public static Type getTypeFromJson(JSONObject tags) {
			return Type.valueOf(tags.optString(KEY_TYPE, node.name()));
		}
	}

	public enum NodeTypes {
		route(R.string.route, R.string.route_description, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"climbing\":\"route_.*\".*).*"),
		crag(R.string.crag, R.string.crag_description, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"climbing\":\"crag\".*).*"),
		artificial(R.string.artificial, R.string.artificial_description, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"leisure\":\"sports_centre\".*).*"),
		unknown(R.string.unknown, R.string.unknown_description, ".*(?=.*\"sport\":\"climbing\".*).*");

		private int stringTypeNameId;
		private int stringTypeDescriptionId;
		private String regexFilter;

		NodeTypes(int pStringId, int pStringDescriptionId, String regexFilter) {
			this.regexFilter = regexFilter;
			this.stringTypeNameId = pStringId;
			this.stringTypeDescriptionId = pStringDescriptionId;
		}

		public static NodeTypes getNodeTypeFromJson(JSONObject tags) {
			String tagsString = tags.toString().trim();
			for (NodeTypes type : NodeTypes.values()) {
				if (tagsString.matches(type.regexFilter)) {
					return type;
				}
			}

			return NodeTypes.unknown;
		}

		@Override
		public String toString() {
			return ClimbTheWorld.getContext().getString(stringTypeNameId);
		}

		public int getNameId() {
			return stringTypeNameId;
		}

		public int getDescriptionId() {
			return stringTypeDescriptionId;
		}
	}

	public enum ClimbingStyle {
		ice(R.string.ice, R.string.ice_short, R.string.ice_description),
		mixed(R.string.mixed, R.string.mixed_short, R.string.mixed_description),
		toprope(R.string.toprope, R.string.toprope_short, R.string.toprope_description),
		boulder(R.string.boulder, R.string.boulder_short, R.string.boulder_description),
		sport(R.string.sport, R.string.sport_short, R.string.sport_description),
		trad(R.string.trad, R.string.trad_short, R.string.trad_description),
		multipitch(R.string.multipitch, R.string.multipitch_short, R.string.multipitch_description),
		deepwater(R.string.deepwater, R.string.deepwater_short, R.string.deepwater_description);

		private int stringTypeNameId;
		private int stringTypeShortNameId;
		private int stringTypeDescriptionId;

		ClimbingStyle(int pStringId, int pStringShortId, int pStringDescriptionId) {
			this.stringTypeNameId = pStringId;
			this.stringTypeShortNameId = pStringShortId;
			this.stringTypeDescriptionId = pStringDescriptionId;
		}

		@Override
		public String toString() {
			return ClimbTheWorld.getContext().getString(stringTypeNameId);
		}

		public int getNameId() {
			return stringTypeNameId;
		}

		public int getShortNameId() {
			return stringTypeShortNameId;
		}

		public int getDescriptionId() {
			return stringTypeDescriptionId;
		}
	}

	@PrimaryKey
	public long osmID;
	public String countryIso;

	//uses type converter
	NodeTypes nodeType;

	public NodeTypes getNodeType() {
		return nodeType;
	}

	public void setClimbingType(NodeTypes nodeType) {
		this.nodeType = nodeType;
		setTypeTags(this.getTags());
	}

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
	@Ignore
	public Type type;


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

	public GeoNode(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude) {
		this(new JSONObject());
		this.updatePOILocation(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
	}

	public GeoNode(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.updatePOILocation(Double.parseDouble(this.jsonNodeInfo.optString(KEY_LAT, "0")),
				Double.parseDouble(this.jsonNodeInfo.optString(KEY_LON, "0")),
				Double.parseDouble(getTags().optString(KEY_ELEVATION, "0").replaceAll("[^\\d.]", "")));

		this.osmID = this.jsonNodeInfo.optLong(KEY_ID, 0);
		this.updateDate = System.currentTimeMillis();
		this.type = Type.getTypeFromJson(this.jsonNodeInfo);
		setClimbingType(NodeTypes.getNodeTypeFromJson(getTags()));
	}

	public String toJSONString() {
		return jsonNodeInfo.toString();
	}

	public Map<String, Object> getNodeTagsMap() {
		Map<String, Object> result = new HashMap<>();

		JSONObject tags = getTags();

		Iterator<String> keysItr = tags.keys();
		while (keysItr.hasNext()) {
			try {
				String key = keysItr.next();
				Object value = tags.get(key);

				result.put(key, value);
			} catch (JSONException ignore) {
			}
		}

		return result;
	}

	private void setTypeTags(JSONObject tagsMap) {

		String oldClimbingTag = tagsMap.optString(KEY_CLIMBING);
		//cleanup
		tagsMap.remove(KEY_SPORT);
		tagsMap.remove(KEY_CLIMBING);
		tagsMap.remove(KEY_LEISURE);

		try {
			switch (nodeType) {
				case route:
					tagsMap.put(KEY_SPORT, "climbing");
					if ((oldClimbingTag != null)
							&& (oldClimbingTag.toLowerCase().startsWith("route_"))) {
						tagsMap.put(KEY_CLIMBING, oldClimbingTag);
					} else {
						tagsMap.put(KEY_CLIMBING, "route_bottom");
					}
					break;
				case crag:
					tagsMap.put(KEY_SPORT, "climbing");
					tagsMap.put(KEY_CLIMBING, "crag");
					break;
				case artificial:
					tagsMap.put(KEY_SPORT, "climbing");
					tagsMap.put(KEY_LEISURE, "sports_centre");
					break;
				case unknown:
				default:
					tagsMap.put(KEY_SPORT, "climbing");
					break;
			}

		} catch (JSONException ignore) {
		}
	}

	public long getID() {
		return jsonNodeInfo.optLong(KEY_ID, osmID);
	}

	public String getKey(String key) {
		return getTags().optString(key, "");
	}

	public void setKey(String key, String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(key);
			return;
		}
		try {
			getTags().put(key, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public String getWebsite() {
		return getTags().optString(KEY_WEBSITE, getTags().optString(KEY_CONTACT_WEBSITE, ""));
	}

	public void setWebsite(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(KEY_CONTACT_WEBSITE);
			return;
		}

		try {
			getTags().put(KEY_CONTACT_WEBSITE, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public String getPhone() {
		return getTags().optString(KEY_PHONE, getTags().optString(KEY_CONTACT_PHONE, ""));
	}

	public void setPhone(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(KEY_CONTACT_PHONE);
			return;
		}

		try {
			getTags().put(KEY_CONTACT_PHONE, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public String getName() {
		return getTags().optString(KEY_NAME, "");
	}

	public void setName(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(KEY_NAME);
			return;
		}

		try {
			getTags().put(KEY_NAME, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public List<ClimbingStyle> getClimbingStyles() {
		Set<ClimbingStyle> result = new TreeSet<>();

		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
				if (key.equalsIgnoreCase(KEY_CLIMBING + KEY_SEPARATOR + style.name())
						&& !getTags().optString(key).equalsIgnoreCase("no")) {
					result.add(style);
				}
			}
		}

		return new ArrayList<>(result);
	}

	public void setClimbingStyles(List<GeoNode.ClimbingStyle> styles) {
		Set<String> toDelete = new TreeSet<>();
		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
				if (key.equalsIgnoreCase(KEY_CLIMBING + KEY_SEPARATOR + style.name())) {
					if (styles.contains(style)) {
						try {
							getTags().put(KEY_CLIMBING + KEY_SEPARATOR + style.name(), "yes");
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

		for (String key : toDelete) {
			getTags().remove(key);
		}

		for (GeoNode.ClimbingStyle style : styles) {
			try {
				getTags().put(KEY_CLIMBING + KEY_SEPARATOR + style.name(), "yes");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	public int getLevelId(String gradeKey) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		Iterator<String> keyIt = getTags().keys();
		int result = -1;
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			String noCaseKey = key.toLowerCase();
			if (matchKey(regex, noCaseKey)) {
				String[] keySplit = noCaseKey.split(KEY_SEPARATOR);
				String grade = getTags().optString(key, UNKNOWN_GRADE_STRING);
				return GradeSystem.fromString(keySplit[2]).indexOf(grade);
			}
		}
		return result;
	}

	public void setLevelFromID(int id, String gradeKey) {
		try {
			String gradeInStandardSystem = Constants.STANDARD_SYSTEM.getGrade(id);
			if (gradeInStandardSystem.equalsIgnoreCase(UNKNOWN_GRADE_STRING)) {
				removeLevelTags(gradeKey);
			}
			removeLevelTags(gradeKey);
			String gradeTagKey = String.format(Locale.getDefault(), gradeKey, Constants.STANDARD_SYSTEM).toLowerCase();
			getTags().put(gradeTagKey, gradeInStandardSystem);
		} catch (JSONException ignore) {
		}
	}

	private boolean matchKey(String keyFilter, String keyJson) {
		String[] keyFilterSplit = keyFilter.toLowerCase().split(KEY_SEPARATOR);
		String[] keyJsonSplit = keyJson.toLowerCase().split(KEY_SEPARATOR);

		if (keyFilterSplit.length != keyJsonSplit.length) {
			return false;
		} else {
			for (int i = 0; i < keyFilterSplit.length; ++i) {
				String keyFilterPart = keyFilterSplit[i];
				String keyJsonPart = keyJsonSplit[i];

				if (!keyFilterPart.equalsIgnoreCase("*") && !keyFilterPart.equalsIgnoreCase(keyJsonPart)) {
					return false;
				}
			}
		}
		return true;
	}

	public void removeLevelTags(String gradeKey) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		List<String> toRemove = new ArrayList<>();
		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			String noCaseKey = key.toLowerCase();
			if (matchKey(regex, noCaseKey)) {
				toRemove.add(key);
			}
		}

		for (String item : toRemove) {
			getTags().remove(item);
		}
	}

	public void updatePOILocation(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude) {
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

	private void setJSONData(JSONObject pNodeInfo) {
		this.jsonNodeInfo = pNodeInfo;
	}

	public JSONObject getTags() {
		if (!jsonNodeInfo.has(KEY_TAGS)) {
			try {
				jsonNodeInfo.put(KEY_TAGS, new JSONObject());
			} catch (JSONException ignore) {
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

	public boolean isArtificialTower() {
		return this.getKey(GeoNode.KEY_MAN_MADE).equalsIgnoreCase(KEY_TOWER)
				|| (this.getKey(GeoNode.KEY_TOWER_TYPE).equalsIgnoreCase(KEY_CLIMBING));

	}
}
