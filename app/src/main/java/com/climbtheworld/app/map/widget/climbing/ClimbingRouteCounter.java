package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.logbook.LogBookFilter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Counts the unique climbing routes contained in an OSM entity, recursively through nested
 * relations, and groups them by climbing style. A route tagged with several styles counts
 * towards each of them. A route mapped with several elements (a route way and its bottom and
 * top nodes, or a bottom and a top node with the same name) counts once. Must run off the UI
 * thread when backed by the database.
 */
public final class ClimbingRouteCounter {

	public interface EntitySource {
		Map<Long, OsmNode> loadNodes(List<Long> ids);

		Map<String, OsmCollectionEntity> loadCollections(List<Long> ids);
	}

	public static final class RouteSummary {
		private final int routeCount;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts;
		private final Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts;
		private final double minLength;
		private final double maxLength;
		private final int minGrade;
		private final int maxGrade;
		private final List<String> routeNames;

		private RouteSummary(int routeCount, Map<GeoNode.ClimbingStyle, Integer> styleCounts,
		                     Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts,
		                     double minLength, double maxLength, int minGrade, int maxGrade,
		                     List<String> routeNames) {
			this.routeCount = routeCount;
			this.styleCounts = Collections.unmodifiableMap(styleCounts);
			this.styleGradeCounts = Collections.unmodifiableMap(styleGradeCounts);
			this.minLength = minLength;
			this.maxLength = maxLength;
			this.minGrade = minGrade;
			this.maxGrade = maxGrade;
			this.routeNames = Collections.unmodifiableList(routeNames);
		}

		public int getRouteCount() {
			return routeCount;
		}

		/**
		 * Shortest route length in meters, or {@link Double#NaN} when no route has a length.
		 */
		public double getMinLength() {
			return minLength;
		}

		/**
		 * Longest route length in meters, or {@link Double#NaN} when no route has a length.
		 */
		public double getMaxLength() {
			return maxLength;
		}

		/**
		 * Easiest route grade index, or {@link #UNKNOWN_GRADE} when no route has a grade.
		 */
		public int getMinGrade() {
			return minGrade;
		}

		/**
		 * Hardest route grade index, or {@link #UNKNOWN_GRADE} when no route has a grade.
		 */
		public int getMaxGrade() {
			return maxGrade;
		}

		/**
		 * Route count per climbing style; styles without routes are absent.
		 */
		public Map<GeoNode.ClimbingStyle, Integer> getStyleCounts() {
			return styleCounts;
		}

		/**
		 * Route count per grade index for one style, ordered easiest first. Routes without a
		 * recognised grade are counted under {@link #UNKNOWN_GRADE}. Grades without routes are
		 * absent.
		 */
		public SortedMap<Integer, Integer> getGradeCounts(GeoNode.ClimbingStyle style) {
			SortedMap<Integer, Integer> result = styleGradeCounts.get(style);
			return result != null ? Collections.unmodifiableSortedMap(result)
					: Collections.unmodifiableSortedMap(new TreeMap<>());
		}

		/**
		 * Names of the counted routes; unnamed routes are absent.
		 */
		public List<String> getRouteNames() {
			return routeNames;
		}
	}

	public static final int UNKNOWN_GRADE = -1;

	private static final class Accumulator {
		private int routeCount = 0;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts =
				new EnumMap<>(GeoNode.ClimbingStyle.class);
		private final Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts =
				new EnumMap<>(GeoNode.ClimbingStyle.class);
		private double minLength = Double.NaN;
		private double maxLength = Double.NaN;
		private int minGrade = UNKNOWN_GRADE;
		private int maxGrade = UNKNOWN_GRADE;
		private final List<String> routeNames = new ArrayList<>();

		private void addRoute(JSONObject tags) {
			routeCount++;
			String name = tags.optString(ClimbingTags.KEY_NAME).trim();
			if (!name.isEmpty()) {
				routeNames.add(name);
			}
			double length = parseLength(tags.optString(ClimbingTags.KEY_LENGTH));
			if (!Double.isNaN(length)) {
				minLength = Double.isNaN(minLength) ? length : Math.min(minLength, length);
				maxLength = Double.isNaN(maxLength) ? length : Math.max(maxLength, length);
			}
			int grade = Math.max(GeoNode.getLevelId(tags, ClimbingTags.KEY_GRADE_TAG), UNKNOWN_GRADE);
			if (grade != UNKNOWN_GRADE) {
				minGrade = minGrade == UNKNOWN_GRADE ? grade : Math.min(minGrade, grade);
				maxGrade = Math.max(maxGrade, grade);
			}
			for (GeoNode.ClimbingStyle style : GeoNode.getClimbingStyles(tags)) {
				increment(styleCounts, style);
				SortedMap<Integer, Integer> grades = styleGradeCounts.get(style);
				if (grades == null) {
					grades = new TreeMap<>(GRADE_ORDER);
					styleGradeCounts.put(style, grades);
				}
				increment(grades, grade);
			}
		}

		private static <K> void increment(Map<K, Integer> counts, K key) {
			Integer current = counts.get(key);
			counts.put(key, current == null ? 1 : current + 1);
		}

		private RouteSummary build() {
			return new RouteSummary(routeCount, styleCounts, styleGradeCounts, minLength,
					maxLength, minGrade, maxGrade, routeNames);
		}
	}

	/**
	 * Parses a route length in meters, such as "25" or "25 m". Other units are ignored.
	 */
	static double parseLength(String value) {
		String length = value.trim();
		if (length.endsWith("m")) {
			length = length.substring(0, length.length() - 1).trim();
		}
		try {
			double result = Double.parseDouble(length);
			return result > 0 && !Double.isInfinite(result) ? result : Double.NaN;
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	// Easiest grade first, unknown grades last.
	private static final Comparator<Integer> GRADE_ORDER = (left, right) -> {
		if (left.equals(right)) {
			return 0;
		}
		if (left == UNKNOWN_GRADE) {
			return 1;
		}
		if (right == UNKNOWN_GRADE) {
			return -1;
		}
		return Integer.compare(left, right);
	};

	private final EntitySource source;

	public ClimbingRouteCounter(EntitySource source) {
		this.source = source;
	}

	public static ClimbingRouteCounter forDatabase(Context context) {
		DataManagerNew dataManager = new DataManagerNew();
		return new ClimbingRouteCounter(new EntitySource() {
			@Override
			public Map<Long, OsmNode> loadNodes(List<Long> ids) {
				return ids.isEmpty() ? Collections.emptyMap()
						: dataManager.loadNodeData(context, ids);
			}

			@Override
			public Map<String, OsmCollectionEntity> loadCollections(List<Long> ids) {
				return ids.isEmpty() ? Collections.emptyMap()
						: dataManager.loadCollectionData(context, ids);
			}
		});
	}

	public RouteSummary summarize(OsmNode node) {
		Accumulator accumulator = new Accumulator();
		if (node.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			accumulator.addRoute(node.getTags());
		}
		return accumulator.build();
	}

	public RouteSummary summarize(OsmCollectionEntity collection) {
		return summarize(collection, null);
	}

	/**
	 * @param collectionNodes the already loaded {@link OsmCollectionEntity#osmNodes} of the
	 *                        collection, or null to load them from the source.
	 */
	public RouteSummary summarize(OsmCollectionEntity collection,
	                              Map<Long, OsmNode> collectionNodes) {
		Accumulator accumulator = new Accumulator();
		Set<String> visited = new HashSet<>();
		visited.add(entityKey(collection.osmType.name(), collection.osmID));
		// Nodes of a route way or relation, such as its tagged bottom and top, are part of that
		// route rather than routes of their own.
		Set<Long> routePartNodes = new HashSet<>();
		if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			accumulator.addRoute(collection.getTags());
			routePartNodes.addAll(collection.osmNodes);
		}
		addContainedRouteCollections(collection, visited, accumulator, routePartNodes);

		Map<Long, OsmNode> nodes = collectionNodes != null
				? collectionNodes : source.loadNodes(collection.osmNodes);
		List<OsmNode> routeTops = new ArrayList<>();
		for (OsmNode node : nodes.values()) {
			if (node.entityClimbingType != OsmEntity.EntityClimbingType.route
					|| routePartNodes.contains(node.osmID)
					|| !visited.add(entityKey(OsmEntity.EntityOsmType.node.name(), node.osmID))) {
				continue;
			}
			if (ClimbingTags.VALUE_ROUTE_TOP.equals(
					node.getTags().optString(ClimbingTags.KEY_CLIMBING))) {
				routeTops.add(node);
			} else {
				accumulator.addRoute(node.getTags());
			}
		}

		// A route_top usually marks the top of a route whose bottom is mapped too, and nothing
		// links the two but the name. So a top counts only when no other route has its name;
		// unnamed tops cannot be matched and never count.
		Set<String> routeNames = new HashSet<>();
		for (String name : accumulator.routeNames) {
			routeNames.add(LogBookFilter.normalize(name));
		}
		for (OsmNode routeTop : routeTops) {
			String name = LogBookFilter.normalize(
					routeTop.getTags().optString(ClimbingTags.KEY_NAME).trim());
			if (!name.isEmpty() && !routeNames.contains(name)) {
				accumulator.addRoute(routeTop.getTags());
			}
		}
		return accumulator.build();
	}

	/**
	 * Numbers the routes of a crag relation in the order of its members, which mappers keep left
	 * to right along the wall, as on a topo. Route ways and relations among the members take a
	 * number too, but a route top does not: it belongs to the route its bottom numbers.
	 *
	 * @return the number of each route node member, keyed by node ID; empty for anything but a
	 * crag relation.
	 */
	public Map<Long, Integer> numberRoutes(OsmCollectionEntity crag) {
		Map<Long, Integer> result = new HashMap<>();
		JSONArray members = crag.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (crag.osmType != OsmEntity.EntityOsmType.relation
				|| crag.entityClimbingType != OsmEntity.EntityClimbingType.crag
				|| members == null) {
			return result;
		}

		List<Long> nodeIds = new ArrayList<>();
		List<Long> collectionIds = new ArrayList<>();
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			if (OsmEntity.EntityOsmType.node.name().equals(member.optString(ClimbingTags.KEY_TYPE))) {
				nodeIds.add(member.optLong(ClimbingTags.KEY_REF));
			} else {
				collectionIds.add(member.optLong(ClimbingTags.KEY_REF));
			}
		}
		Map<Long, OsmNode> nodes = source.loadNodes(nodeIds);
		Map<String, OsmCollectionEntity> collections = collectionIds.isEmpty()
				? Collections.emptyMap() : source.loadCollections(collectionIds);

		int number = 0;
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			long memberId = member.optLong(ClimbingTags.KEY_REF);
			String memberType = member.optString(ClimbingTags.KEY_TYPE);
			if (OsmEntity.EntityOsmType.node.name().equals(memberType)) {
				OsmNode node = nodes.get(memberId);
				if (node != null && takesRouteNumber(node)) {
					result.put(memberId, ++number);
				}
				continue;
			}
			OsmEntity.EntityOsmType memberOsmType;
			try {
				memberOsmType = OsmEntity.EntityOsmType.valueOf(memberType);
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(
					DataManagerNew.collectionKey(memberOsmType, memberId));
			if (collection != null && takesRouteNumber(collection)) {
				++number;
			}
		}
		return result;
	}

	/** @return whether the member of a crag takes a route number; see numberRoutes. */
	public static boolean takesRouteNumber(OsmEntity member) {
		return member.entityClimbingType == OsmEntity.EntityClimbingType.route
				&& !ClimbingTags.VALUE_ROUTE_TOP.equals(
				member.getTags().optString(ClimbingTags.KEY_CLIMBING));
	}

	private void addContainedRouteCollections(OsmCollectionEntity parent, Set<String> visited,
	                                          Accumulator accumulator, Set<Long> routePartNodes) {
		JSONArray members = parent.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (members == null) {
			return;
		}

		List<Long> collectionIds = new ArrayList<>();
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member != null && !OsmEntity.EntityOsmType.node.name()
					.equals(member.optString(ClimbingTags.KEY_TYPE))) {
				collectionIds.add(member.optLong(ClimbingTags.KEY_REF));
			}
		}
		if (collectionIds.isEmpty()) {
			return;
		}

		Map<String, OsmCollectionEntity> collections = source.loadCollections(collectionIds);
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			String memberType = member.optString(ClimbingTags.KEY_TYPE);
			if (OsmEntity.EntityOsmType.node.name().equals(memberType)) {
				continue;
			}
			long memberId = member.optLong(ClimbingTags.KEY_REF);
			if (!visited.add(entityKey(memberType, memberId))) {
				continue;
			}
			OsmEntity.EntityOsmType memberOsmType;
			try {
				memberOsmType = OsmEntity.EntityOsmType.valueOf(memberType);
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(
					DataManagerNew.collectionKey(memberOsmType, memberId));
			if (collection == null || !collection.osmType.name().equals(memberType)) {
				continue;
			}
			if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
				accumulator.addRoute(collection.getTags());
				routePartNodes.addAll(collection.osmNodes);
			}
			if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				addContainedRouteCollections(collection, visited, accumulator, routePartNodes);
			}
		}
	}

	private static String entityKey(String osmType, long osmId) {
		return osmType + "|" + osmId;
	}
}
