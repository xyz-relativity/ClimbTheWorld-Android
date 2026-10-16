package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.constants.UIConstants;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An OSM element as the screens show and edit it: an OsmNode, or a way or relation placed at its
 * center. It is not stored; edits go back through OsmEditStore.
 */
public class GeoNode implements Comparable {
	private static final Pattern ELEVATION_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
	public long osmID;
	public String countryIso;
	public JSONObject jsonNodeInfo;
	//This are kept as variables since they are accessed often during AR rendering.
	public double decimalLatitude = 0;
	public double decimalLongitude = 0;
	public double elevationMeters = 0;
	public double distanceMeters = 0;
	public double deltaDegAzimuth = 0;
	public double difDegAngle = 0;
	public double elevationDegAngle = 0;
	NodeTypes nodeType;
	public GeoNode(String stringNodeInfo) throws JSONException {
		this(new JSONObject(stringNodeInfo));
	}
	public GeoNode(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude) {
		this(new JSONObject());
		this.updatePOILocation(pDecimalLatitude, pDecimalLongitude, pMetersAltitude);
	}

	/** Reads the element without changing it, so the tags it would upload stay as downloaded. */
	public GeoNode(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.decimalLatitude = Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LAT, "0"));
		this.decimalLongitude = Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LON, "0"));
		this.elevationMeters = parseElevation(getTags().optString(ClimbingTags.KEY_ELEVATION));

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
		this.nodeType = NodeTypes.getNodeTypeFromJson(getTags());
	}

	public NodeTypes getNodeType() {
		return nodeType;
	}

	/**
	 * Changes the type, rewriting the sport, climbing and leisure tags to match. Keeping the type
	 * leaves the tags alone, so values the app would not write itself, like
	 * sport=climbing;hiking, survive an edit.
	 */
	public void setClimbingType(NodeTypes nodeType) {
		if (this.nodeType == nodeType) {
			return;
		}
		this.nodeType = nodeType;
		setTypeTags(this.getTags());
	}

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

	public String toJSONString() {
		return jsonNodeInfo.toString();
	}

	private void setTypeTags(JSONObject tagsMap) {

		String oldClimbingTag = tagsMap.optString(ClimbingTags.KEY_CLIMBING);
		String oldSport = tagsMap.optString(ClimbingTags.KEY_SPORT);
		//cleanup
		tagsMap.remove(ClimbingTags.KEY_CLIMBING);
		if (ClimbingTags.isClimbingFacility(tagsMap.optString(ClimbingTags.KEY_LEISURE))) {
			tagsMap.remove(ClimbingTags.KEY_LEISURE);
		}

		try {
			// Other sports listed with climbing stay.
			tagsMap.put(ClimbingTags.KEY_SPORT, ClimbingTags.hasClimbingSport(oldSport)
					? oldSport : ClimbingTags.VALUE_CLIMBING);
			switch (nodeType) {
				case route:
					if (oldClimbingTag.toLowerCase().startsWith("route_")) {
						tagsMap.put(ClimbingTags.KEY_CLIMBING, oldClimbingTag);
					} else {
						tagsMap.put(ClimbingTags.KEY_CLIMBING, "route_bottom");
					}
					break;
				case crag:
					tagsMap.put(ClimbingTags.KEY_CLIMBING, "crag");
					break;
				case area:
					tagsMap.put(ClimbingTags.KEY_CLIMBING, "area");
					break;
				case artificial:
					tagsMap.put(ClimbingTags.KEY_LEISURE, ClimbingTags.VALUE_SPORTS_CENTRE);
					break;
				case unknown:
				default:
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
		return getTags().optString(ClimbingTags.KEY_WEBSITE,
				getTags().optString(ClimbingTags.KEY_CONTACT_WEBSITE, ""));
	}

	public void setWebsite(String value) {
		setContactKey(ClimbingTags.KEY_WEBSITE, ClimbingTags.KEY_CONTACT_WEBSITE, value);
	}

	public String getPhone() {
		return getTags().optString(ClimbingTags.KEY_PHONE,
				getTags().optString(ClimbingTags.KEY_CONTACT_PHONE, ""));
	}

	public void setPhone(String value) {
		setContactKey(ClimbingTags.KEY_PHONE, ClimbingTags.KEY_CONTACT_PHONE, value);
	}

	/**
	 * Writes a value that can be tagged with or without the contact: prefix under the key the
	 * element already uses, the plain one, as the OSM wiki's Climbing page has it, for a new one.
	 * Clearing it removes both.
	 */
	private void setContactKey(String key, String contactKey, String value) {
		JSONObject tags = getTags();
		if (value == null || value.isEmpty()) {
			tags.remove(key);
			tags.remove(contactKey);
			return;
		}

		try {
			tags.put(tags.has(contactKey) && !tags.has(key) ? contactKey : key, value);
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
		return getClimbingStyles(getTags());
	}

	/**
	 * Climbing styles declared by climbing:&lt;style&gt; tags whose value is not "no".
	 */
	public static List<ClimbingStyle> getClimbingStyles(JSONObject tags) {
		Set<ClimbingStyle> result = new TreeSet<>();

		Iterator<String> keyIt = tags.keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
				if (key.equalsIgnoreCase(
						ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name())
						&& !tags.optString(key).equalsIgnoreCase("no")) {
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
				if (key.equalsIgnoreCase(
						ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name())) {
					if (styles.contains(style)) {
						try {
							getTags().put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR +
									style.name(), "yes");
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
				getTags().put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name(),
						"yes");
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	public int getLevelId(String gradeKey) {
		return getLevelId(getTags(), gradeKey);
	}

	public int getLevelId(String gradeKey, GradeSystem system) {
		return getLevelId(getTags(), gradeKey, system);
	}

	public GradeSystem getLevelSystem(String gradeKey, GradeSystem preferred) {
		return getLevelSystem(getTags(), gradeKey, preferred);
	}

	/**
	 * Grade index from the climbing:grade:&lt;system&gt;-style tags matching gradeKey, or -1.
	 * Tags with an unknown system or value are skipped; the standard system wins over others.
	 */
	public static int getLevelId(JSONObject tags, String gradeKey) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		int result = -1;
		Iterator<String> keyIt = tags.keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			String noCaseKey = key.toLowerCase();
			if (!matchKey(regex, noCaseKey)) {
				continue;
			}
			String[] keySplit = noCaseKey.split(ClimbingTags.KEY_SEPARATOR);
			GradeSystem system = GradeSystem.fromOsmKey(keySplit[2]);
			int levelId = system.indexOf(tags.optString(key, ClimbingTags.UNKNOWN_GRADE_STRING));
			if (levelId < 0) {
				continue;
			}
			if (system == UIConstants.STANDARD_SYSTEM) {
				return levelId;
			}
			if (result < 0) {
				result = levelId;
			}
		}
		return result;
	}

	/**
	 * Grade index as the element's grade in system has it, so that a grade falling between two of
	 * the standard system's is read as written; as getLevelId(JSONObject, String) has it when the
	 * element has no grade in system the app can read.
	 */
	public static int getLevelId(JSONObject tags, String gradeKey, GradeSystem system) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		Iterator<String> keyIt = tags.keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			if (!matchKey(regex, key.toLowerCase())
					|| GradeSystem.fromOsmKey(key.split(ClimbingTags.KEY_SEPARATOR)[2]) != system) {
				continue;
			}
			int levelId = system.indexOf(tags.optString(key, ClimbingTags.UNKNOWN_GRADE_STRING));
			if (levelId >= 0) {
				return levelId;
			}
		}
		return getLevelId(tags, gradeKey);
	}

	/**
	 * The system the element is graded in, of the climbing:grade:&lt;system&gt;-style tags
	 * matching gradeKey: preferred when it is one of them, otherwise the first of them in
	 * GradeSystem order, which doesn't depend on the order OSM serves the tags in. Tags with an
	 * unknown system or value are skipped; preferred is also the answer when none is left.
	 */
	public static GradeSystem getLevelSystem(JSONObject tags, String gradeKey, GradeSystem preferred) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		GradeSystem result = null;
		Iterator<String> keyIt = tags.keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			if (!matchKey(regex, key.toLowerCase())) {
				continue;
			}
			GradeSystem system = GradeSystem.fromOsmKey(key.split(ClimbingTags.KEY_SEPARATOR)[2]);
			if (system.indexOf(tags.optString(key, ClimbingTags.UNKNOWN_GRADE_STRING)) < 0) {
				continue;
			}
			if (system == preferred) {
				return preferred;
			}
			if (result == null || system.ordinal() < result.ordinal()) {
				result = system;
			}
		}
		return result != null ? result : preferred;
	}

	/** The climbing:grade:&lt;system&gt;-style tags matching gradeKey, as they are. */
	public Map<String, String> getLevelTags(String gradeKey) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		Map<String, String> result = new LinkedHashMap<>();
		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			if (matchKey(regex, key.toLowerCase())) {
				result.put(key, getTags().optString(key));
			}
		}
		return result;
	}

	/**
	 * Puts back the climbing:grade:&lt;system&gt;-style tags matching gradeKey that getLevelTags
	 * gave, in place of the ones the element has now.
	 */
	public void setLevelTags(String gradeKey, Map<String, String> levelTags) {
		removeLevelTags(gradeKey);
		try {
			for (Map.Entry<String, String> tag : levelTags.entrySet()) {
				getTags().put(tag.getKey(), tag.getValue());
			}
		} catch (JSONException ignore) {
		}
	}

	/**
	 * Sets the grade of the climbing:grade:&lt;system&gt;-style tags matching gradeKey in the
	 * standard system, see writeLevel.
	 */
	public void setLevelFromID(int id, String gradeKey) {
		writeLevel(id, gradeKey, UIConstants.STANDARD_SYSTEM);
	}

	/**
	 * Sets a grade picked from system's grades, saving it in system, see writeLevel. When system
	 * already shows the element's grade as the picked one, the grades are kept, apart from adding
	 * the grade in system when it is missing: a grade that falls between two of system's would
	 * otherwise be rounded to the one shown.
	 */
	public void setLevelFromID(int id, String gradeKey, GradeSystem system) {
		int current = getLevelId(gradeKey, system);
		if (!system.getGrade(id).equals(system.getGrade(current))) {
			writeLevel(id, gradeKey, system);
			return;
		}
		if (current < 0) {
			return;
		}

		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			if (matchKey(regex, key.toLowerCase()) && GradeSystem.fromOsmKey(
					key.split(ClimbingTags.KEY_SEPARATOR)[2]) == system) {
				return;
			}
		}
		try {
			getTags().put(levelKey(gradeKey, system), system.getGrade(current));
		} catch (JSONException ignore) {
		}
	}

	/**
	 * Sets the grade of the climbing:grade:&lt;system&gt;-style tags matching gradeKey: in
	 * savedIn, added if the element has no grade in it, and converted into every other system the
	 * element is graded in. Grades in systems the app can't read are left alone. An unknown grade
	 * removes the grades in the systems the app can read.
	 */
	private void writeLevel(int id, String gradeKey, GradeSystem savedIn) {
		String regex = String.format(Locale.getDefault(), gradeKey, "*");
		boolean unknown = savedIn.getGrade(id).equalsIgnoreCase(ClimbingTags.UNKNOWN_GRADE_STRING);
		boolean hasSaved = false;
		List<String> keys = new ArrayList<>();
		Iterator<String> keyIt = getTags().keys();
		while (keyIt.hasNext()) {
			keys.add(keyIt.next());
		}

		try {
			for (String key : keys) {
				if (!matchKey(regex, key.toLowerCase())) {
					continue;
				}
				GradeSystem system = GradeSystem.fromOsmKey(key.split(ClimbingTags.KEY_SEPARATOR)[2]);
				if (system == GradeSystem.undef) {
					continue;
				}
				if (unknown) {
					getTags().remove(key);
					continue;
				}
				hasSaved |= system == savedIn;
				getTags().put(key, system.getGrade(id));
			}

			if (!unknown && !hasSaved) {
				getTags().put(levelKey(gradeKey, savedIn), savedIn.getGrade(id));
			}
		} catch (JSONException ignore) {
		}
	}

	private static String levelKey(String gradeKey, GradeSystem system) {
		return String.format(Locale.getDefault(), gradeKey, system.getOsmKey());
	}

	private static boolean matchKey(String keyFilter, String keyJson) {
		String[] keyFilterSplit = keyFilter.toLowerCase().split(ClimbingTags.KEY_SEPARATOR);
		String[] keyJsonSplit = keyJson.toLowerCase().split(ClimbingTags.KEY_SEPARATOR);

		if (keyFilterSplit.length != keyJsonSplit.length) {
			return false;
		} else {
			for (int i = 0; i < keyFilterSplit.length; ++i) {
				String keyFilterPart = keyFilterSplit[i];
				String keyJsonPart = keyJsonSplit[i];

				if (!keyFilterPart.equalsIgnoreCase("*") &&
						!keyFilterPart.equalsIgnoreCase(keyJsonPart)) {
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

	public void updatePOILocation(double pDecimalLatitude, double pDecimalLongitude,
	                              double pMetersAltitude) {
		this.decimalLongitude = pDecimalLongitude;
		this.decimalLatitude = pDecimalLatitude;
		this.elevationMeters = pMetersAltitude;

		try {
			jsonNodeInfo.put(ClimbingTags.KEY_LAT, this.decimalLatitude);
			jsonNodeInfo.put(ClimbingTags.KEY_LON, this.decimalLongitude);
			if (elevationMeters != 0) {
				getTags().put(ClimbingTags.KEY_ELEVATION, formatElevation(elevationMeters));
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the elevation and its ele tag, 0 included, or removes the tag for null. Unlike
	 * updatePOILocation, which takes an elevation of 0 as unknown and leaves the tag alone.
	 */
	public void setElevation(Double meters) {
		if (meters == null) {
			elevationMeters = 0;
			getTags().remove(ClimbingTags.KEY_ELEVATION);
			return;
		}
		elevationMeters = meters;
		try {
			getTags().put(ClimbingTags.KEY_ELEVATION, formatElevation(meters));
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/** As text, the way OSM holds it: 1234 rather than 1234.0. */
	private static String formatElevation(double meters) {
		return BigDecimal.valueOf(meters).stripTrailingZeros().toPlainString();
	}

	/**
	 * The first number of an ele value, with its sign: 1234 for "1,234 m", -5 for "-5"; 0 when
	 * there is none.
	 */
	static double parseElevation(String ele) {
		Matcher number = ELEVATION_NUMBER.matcher(ele.replace(",", ""));
		return number.find() ? Double.parseDouble(number.group()) : 0;
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
		// Keyed on osmID alone, so that it stays consistent with equals() and stable while the
		// node's tags and measured distance change.
		return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
				append(this.osmID).
				toHashCode();
	}

	/**
	 * @return whether this is the top of a route (climbing=route_top) rather than its bottom.
	 */
	public boolean isRouteTop() {
		return getNodeType() == NodeTypes.route
				&& this.getKey(ClimbingTags.KEY_CLIMBING)
				.equalsIgnoreCase(ClimbingTags.VALUE_ROUTE_TOP);
	}

	public boolean isArtificialTower() {
		return this.getKey(ClimbingTags.KEY_MAN_MADE).equalsIgnoreCase(ClimbingTags.KEY_TOWER)
				|| (this.getKey(ClimbingTags.KEY_TOWER_TYPE)
				.equalsIgnoreCase(ClimbingTags.KEY_CLIMBING));

	}

	/**
	 * @return whether this is a simple climbing wall (leisure=pitch), neither a tower nor a gym.
	 */
	public boolean isClimbingWall() {
		return !isArtificialTower()
				&& this.getKey(ClimbingTags.KEY_LEISURE).equalsIgnoreCase(ClimbingTags.VALUE_PITCH);
	}

	public enum NodeTypes {
		//individual route
		route(R.string.route, R.string.route_description, R.layout.icon_node_topo_display,
				"(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ")" + ClimbingTags.ROUTE_PATTERN + ".*"),
		//a crag will contain one or more routes
		crag(R.string.crag, R.string.crag_description, R.layout.icon_climbing_area_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*\"climbing\":\"crag\".*).*"),
		//a site will contain one or more crags
		area(R.string.area, R.string.area_description, R.layout.icon_climbing_area_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*\"climbing\":\"area\".*).*"),

		artificial(R.string.artificial, R.string.artificial_description,
				R.layout.icon_climbing_artificial_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*" + ClimbingTags.ARTIFICIAL_PATTERN + ".*).*"),
		unknown(R.string.unknown, R.string.unknown_description, R.layout.icon_node_topo_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*).*");

		private static final NodeTypes[] SELECTABLE_VALUES = buildSelectableValues();

		private final int stringTypeNameId;
		private final int stringTypeDescriptionId;
		private final int iconId;
		private final String regexFilter;

		NodeTypes(int pStringId, int pStringDescriptionId, int iconID, String regexFilter) {
			this.regexFilter = regexFilter;
			this.stringTypeNameId = pStringId;
			this.stringTypeDescriptionId = pStringDescriptionId;
			this.iconId = iconID;
		}

		private static NodeTypes[] buildSelectableValues() {
			List<NodeTypes> result = new ArrayList<>();
			for (NodeTypes type : values()) {
				if (type != unknown) {
					result.add(type);
				}
			}
			return result.toArray(new NodeTypes[0]);
		}

		/**
		 * The types a user can pick between. {@link #unknown} is the classification fallback for
		 * anything tagged for climbing that is none of the real types, so it is not offered as a
		 * choice and is never downloaded.
		 */
		public static NodeTypes[] selectableValues() {
			return SELECTABLE_VALUES.clone();
		}

		/**
		 * The types the node editor offers a node: a route or artificial climbing. Crags and areas
		 * are site relations, as the OSM wiki's Climbing page has them, so a node keeps a crag, area
		 * or unknown type only when it already has it.
		 */
		public static NodeTypes[] nodeEditorValues(NodeTypes current) {
			List<NodeTypes> result = new ArrayList<>();
			for (NodeTypes type : values()) {
				if (type == route || type == artificial || type == current) {
					result.add(type);
				}
			}
			return result.toArray(new NodeTypes[0]);
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

		@NotNull
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

		public int getIconId() {
			return iconId;
		}
	}

	public enum ClimbingStyle {
		ice(R.string.ice, R.string.ice_short, R.string.ice_description),
		mixed(R.string.mixed, R.string.mixed_short, R.string.mixed_description),
		toprope(R.string.toprope, R.string.toprope_short, R.string.toprope_description),
		boulder(R.string.boulder, R.string.boulder_short, R.string.boulder_description),
		sport(R.string.sport, R.string.sport_short, R.string.sport_description),
		trad(R.string.trad, R.string.trad_short, R.string.trad_description),
		multipitch(R.string.multipitch, R.string.multipitch_short,
				R.string.multipitch_description),
		deepwater(R.string.deepwater, R.string.deepwater_short, R.string.deepwater_description);

		private final int stringTypeNameId;
		private final int stringTypeShortNameId;
		private final int stringTypeDescriptionId;

		ClimbingStyle(int pStringId, int pStringShortId, int pStringDescriptionId) {
			this.stringTypeNameId = pStringId;
			this.stringTypeShortNameId = pStringShortId;
			this.stringTypeDescriptionId = pStringDescriptionId;
		}

		@NotNull
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
}
