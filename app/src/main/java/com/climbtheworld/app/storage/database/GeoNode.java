package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

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

	public enum Type {
		node, way, relation;

		public static Type getTypeFromJson(JSONObject tags) {
			return Type.valueOf(tags.optString(ClimbingTags.KEY_TYPE, node.name()));
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
			throw new UnsupportedOperationException("Do not use toString. Use asString");
		}

		public String asString(AppCompatActivity parent) {
			return parent.getString(stringTypeNameId);
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
			throw new UnsupportedOperationException("Do not use toString. Use asString");
		}

		public String asString(AppCompatActivity parent) {
			return parent.getString(stringTypeNameId);
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

		this.updatePOILocation(Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LAT, "0")),
				Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LON, "0")),
				Double.parseDouble(getTags().optString(ClimbingTags.KEY_ELEVATION, "0").replaceAll("[^\\d.]", "")));

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
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

		String oldClimbingTag = tagsMap.optString(ClimbingTags.KEY_CLIMBING);
		//cleanup
		tagsMap.remove(ClimbingTags.KEY_SPORT);
		tagsMap.remove(ClimbingTags.KEY_CLIMBING);
		tagsMap.remove(ClimbingTags.KEY_LEISURE);

		try {
			switch (nodeType) {
				case route:
					tagsMap.put(ClimbingTags.KEY_SPORT, "climbing");
					if ((oldClimbingTag != null)
							&& (oldClimbingTag.toLowerCase().startsWith("route_"))) {
						tagsMap.put(ClimbingTags.KEY_CLIMBING, oldClimbingTag);
					} else {
						tagsMap.put(ClimbingTags.KEY_CLIMBING, "route_bottom");
					}
					break;
				case crag:
					tagsMap.put(ClimbingTags.KEY_SPORT, "climbing");
					tagsMap.put(ClimbingTags.KEY_CLIMBING, "crag");
					break;
				case artificial:
					tagsMap.put(ClimbingTags.KEY_SPORT, "climbing");
					tagsMap.put(ClimbingTags.KEY_LEISURE, "sports_centre");
					break;
				case unknown:
				default:
					tagsMap.put(ClimbingTags.KEY_SPORT, "climbing");
					break;
			}

		} catch (JSONException ignore) {
		}
	}

	public long getID() {
		return jsonNodeInfo.optLong(ClimbingTags.KEY_ID, osmID);
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
		return getTags().optString(ClimbingTags.KEY_WEBSITE, getTags().optString(ClimbingTags.KEY_CONTACT_WEBSITE, ""));
	}

	public void setWebsite(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(ClimbingTags.KEY_CONTACT_WEBSITE);
			return;
		}

		try {
			getTags().put(ClimbingTags.KEY_CONTACT_WEBSITE, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public String getPhone() {
		return getTags().optString(ClimbingTags.KEY_PHONE, getTags().optString(ClimbingTags.KEY_CONTACT_PHONE, ""));
	}

	public void setPhone(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(ClimbingTags.KEY_CONTACT_PHONE);
			return;
		}

		try {
			getTags().put(ClimbingTags.KEY_CONTACT_PHONE, value);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public String getName() {
		return getTags().optString(ClimbingTags.KEY_NAME, "");
	}

	public void setName(String value) {
		if (value == null || value.isEmpty()) {
			getTags().remove(ClimbingTags.KEY_NAME);
			return;
		}

		try {
			getTags().put(ClimbingTags.KEY_NAME, value);
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
				if (key.equalsIgnoreCase(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name())
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
				if (key.equalsIgnoreCase(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name())) {
					if (styles.contains(style)) {
						try {
							getTags().put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name(), "yes");
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
				getTags().put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name(), "yes");
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
				String[] keySplit = noCaseKey.split(ClimbingTags.KEY_SEPARATOR);
				String grade = getTags().optString(key, ClimbingTags.UNKNOWN_GRADE_STRING);
				return GradeSystem.fromString(keySplit[2]).indexOf(grade);
			}
		}
		return result;
	}

	public void setLevelFromID(int id, String gradeKey) {
		try {
			String gradeInStandardSystem = Constants.STANDARD_SYSTEM.getGrade(id);
			if (gradeInStandardSystem.equalsIgnoreCase(ClimbingTags.UNKNOWN_GRADE_STRING)) {
				removeLevelTags(gradeKey);
			}
			removeLevelTags(gradeKey);
			String gradeTagKey = String.format(Locale.getDefault(), gradeKey, Constants.STANDARD_SYSTEM).toLowerCase();
			getTags().put(gradeTagKey, gradeInStandardSystem);
		} catch (JSONException ignore) {
		}
	}

	private boolean matchKey(String keyFilter, String keyJson) {
		String[] keyFilterSplit = keyFilter.toLowerCase().split(ClimbingTags.KEY_SEPARATOR);
		String[] keyJsonSplit = keyJson.toLowerCase().split(ClimbingTags.KEY_SEPARATOR);

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
			jsonNodeInfo.put(ClimbingTags.KEY_LAT, this.decimalLatitude);
			jsonNodeInfo.put(ClimbingTags.KEY_LON, this.decimalLongitude);
			if (elevationMeters != 0) {
				getTags().put(ClimbingTags.KEY_ELEVATION, this.elevationMeters);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void setJSONData(JSONObject pNodeInfo) {
		this.jsonNodeInfo = pNodeInfo;
	}

	public JSONObject getTags() {
		if (!jsonNodeInfo.has(ClimbingTags.KEY_TAGS)) {
			try {
				jsonNodeInfo.put(ClimbingTags.KEY_TAGS, new JSONObject());
			} catch (JSONException ignore) {
			}
		}
		return jsonNodeInfo.optJSONObject(ClimbingTags.KEY_TAGS);
	}

	public void setTags(JSONObject tags) {
		try {
			jsonNodeInfo.put(ClimbingTags.KEY_TAGS, tags);
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
		return this.getKey(ClimbingTags.KEY_MAN_MADE).equalsIgnoreCase(ClimbingTags.KEY_TOWER)
				|| (this.getKey(ClimbingTags.KEY_TOWER_TYPE).equalsIgnoreCase(ClimbingTags.KEY_CLIMBING));

	}
}
