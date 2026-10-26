package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.storage.logbook.LogBookFilter;

import org.json.JSONException;
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
import java.util.function.Predicate;

/**
 * Counts the unique climbing routes contained in an OSM entity, recursively through nested
 * relations, and groups them by climbing style. A route tagged with several styles counts
 * towards each of them. A route mapped with several elements (a route way and its bottom and
 * top nodes, or a bottom and a top node with the same name) counts once. Must run off the UI
 * thread when backed by the database.
 * <p>
 * With a route filter, such as the display filters, it also counts the routes that match it,
 * which a crag's or area's pin shows instead of all of them, see {@link #pin}.
 */
public final class ClimbingRouteCounter {

	public interface EntitySource {
		Map<Long, OsmNode> loadNodes(List<Long> ids);

		Map<String, OsmCollectionEntity> loadCollections(List<Long> ids);
	}

	public static final class RouteSummary {
		private final int routeCount;
		private final int matchingRouteCount;
		private final boolean filtered;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts;
		private final Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts;
		private final double minLength;
		private final double maxLength;
		private final int minGrade;
		private final int maxGrade;
		private final List<String> routeNames;

		private RouteSummary(int routeCount, int matchingRouteCount, boolean filtered,
		                     Map<GeoNode.ClimbingStyle, Integer> styleCounts,
		                     Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts,
		                     double minLength, double maxLength, int minGrade, int maxGrade,
		                     List<String> routeNames) {
			this.routeCount = routeCount;
			this.matchingRouteCount = matchingRouteCount;
			this.filtered = filtered;
			this.styleCounts = Collections.unmodifiableMap(styleCounts);
			this.styleGradeCounts = Collections.unmodifiableMap(styleGradeCounts);
			this.minLength = minLength;
			this.maxLength = maxLength;
			this.minGrade = minGrade;
			this.maxGrade = maxGrade;
			this.routeNames = Collections.unmodifiableList(routeNames);
		}

		/** The count of all the routes, whatever the route filter; the rest of it covers them all. */
		public int getRouteCount() {
			return routeCount;
		}

		/**
		 * The count of the routes that match the route filter, or of all of them when it is not
		 * filtered; see isFiltered.
		 */
		public int getMatchingRouteCount() {
			return filtered ? matchingRouteCount : routeCount;
		}

		/**
		 * @return whether only some routes may match: a route filter is on, and there are routes
		 * for it to sort. A pin then shows the matching count, even 0, with the filter badge.
		 */
		public boolean isFiltered() {
			return filtered;
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
		// Null for none.
		private final Predicate<GeoNode> routeFilter;
		private int routeCount = 0;
		private int matchingRouteCount = 0;
		private final Map<GeoNode.ClimbingStyle, Integer> styleCounts =
				new EnumMap<>(GeoNode.ClimbingStyle.class);
		private final Map<GeoNode.ClimbingStyle, SortedMap<Integer, Integer>> styleGradeCounts =
				new EnumMap<>(GeoNode.ClimbingStyle.class);
		private double minLength = Double.NaN;
		private double maxLength = Double.NaN;
		private int minGrade = UNKNOWN_GRADE;
		private int maxGrade = UNKNOWN_GRADE;
		private final List<String> routeNames = new ArrayList<>();

		private Accumulator(Predicate<GeoNode> routeFilter) {
			this.routeFilter = routeFilter;
		}

		private void addRoute(OsmEntity route) {
			JSONObject tags = route.getTags();
			routeCount++;
			if (routeFilter != null && routeFilter.test(new GeoNode(route.jsonNodeInfo))) {
				matchingRouteCount++;
			}
			String name = tags.optString(ClimbingTags.KEY_NAME).trim();
			if (!name.isEmpty()) {
				routeNames.add(name);
			}
			double length = RouteLength.parse(tags.optString(ClimbingTags.KEY_LENGTH));
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
			return new RouteSummary(routeCount, matchingRouteCount,
					routeFilter != null && routeCount > 0, styleCounts, styleGradeCounts,
					minLength, maxLength, minGrade, maxGrade, routeNames);
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
	// Null for none.
	private final Predicate<GeoNode> routeFilter;

	public ClimbingRouteCounter(EntitySource source) {
		this(source, null);
	}

	/**
	 * @param routeFilter the routes to count apart, see RouteSummary.getMatchingRouteCount; null
	 *                    for none
	 */
	public ClimbingRouteCounter(EntitySource source, Predicate<GeoNode> routeFilter) {
		this.source = source;
		this.routeFilter = routeFilter;
	}

	/**
	 * Counts apart the routes that match the display filters, while any is on, wherever a crag or
	 * area is drawn, see NodeDisplayFilters.routeFilter.
	 */
	public static ClimbingRouteCounter forDatabase(Context context) {
		DataManager dataManager = new DataManager();
		Predicate<GeoNode> routeFilter = NodeDisplayFilters.routeFilter(Configs.instance(context));
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
		}, routeFilter);
	}

	/**
	 * A copy of the collection as its pin draws it, placed at the location. A crag's or area's
	 * pin shows the count of its mapped routes, or without any its own climbing:routes tag; the
	 * copy keeps the count out of the collection's tags. The map, the A.R. view, the dialogs and
	 * the lists all draw collections through it.
	 */
	public static GeoNode pin(OsmCollectionEntity collection, MapCoordinate location,
	                          int routeCount) {
		return pin(collection, location, routeCount, false);
	}

	/**
	 * @param filtered whether the count is that of the routes matching the display filters, see
	 *                 RouteSummary.isFiltered: it shows even when 0, rather than the collection's
	 *                 own climbing:routes tag, and the pin wears the filter badge, see
	 *                 GeoNode.routeCountFiltered
	 */
	public static GeoNode pin(OsmCollectionEntity collection, MapCoordinate location,
	                          int routeCount, boolean filtered) {
		GeoNode result;
		try {
			result = new GeoNode(new JSONObject(collection.jsonNodeInfo.toString()));
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		result.updatePOILocation(location.getLatitude(), location.getLongitude(),
				location.getAltitudeMeters());
		if (routeCount > 0 || filtered) {
			result.setKey(ClimbingTags.KEY_ROUTES, Integer.toString(routeCount));
		}
		result.routeCountFiltered = filtered;
		return result;
	}

	/** The collection's pin with the count it shows, see the static pin. */
	public static GeoNode pin(OsmCollectionEntity collection, MapCoordinate location,
	                          RouteSummary routes) {
		return pin(collection, location, routes.getMatchingRouteCount(), routes.isFiltered());
	}

	/** The collection's pin at the location, with its routes counted; see the static pin. */
	public GeoNode pin(OsmCollectionEntity collection, MapCoordinate location) {
		return pin(collection, location, summarize(collection));
	}

	public RouteSummary summarize(OsmNode node) {
		Accumulator accumulator = new Accumulator(routeFilter);
		if (node.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			accumulator.addRoute(node);
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
		Accumulator accumulator = new Accumulator(routeFilter);
		Set<String> visited = new HashSet<>();
		visited.add(entityKey(collection.osmType.name(), collection.osmID));
		// Nodes of a route way or relation, such as its tagged bottom and top, are part of that
		// route rather than routes of their own.
		Set<Long> routePartNodes = new HashSet<>();
		if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
			// The pitches of a route relation are part of it rather than routes of their own.
			accumulator.addRoute(collection);
			routePartNodes.addAll(collection.osmNodes);
		} else {
			addContainedRouteCollections(collection, visited, accumulator, routePartNodes);
		}

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
				accumulator.addRoute(node);
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
				accumulator.addRoute(routeTop);
			}
		}
		return accumulator.build();
	}

	/**
	 * Numbers the routes of a crag relation in the order of its members, which mappers keep left
	 * to right along the wall, as on a topo. Route ways and relations among the members take a
	 * number too, but a route top does not: it belongs to the route its bottom numbers.
	 *
	 * @return the number of each route node member, keyed by node ID, and of each route relation
	 * member, keyed by the ID of the node it starts at; empty for anything but a crag relation.
	 */
	public Map<Long, Integer> numberRoutes(OsmCollectionEntity crag) {
		Map<Long, Integer> result = new HashMap<>();
		List<JSONObject> members = RelationMembers.of(crag.jsonNodeInfo);
		if (crag.osmType != OsmEntity.EntityOsmType.relation
				|| crag.entityClimbingType != OsmEntity.EntityClimbingType.crag
				|| members.isEmpty()) {
			return result;
		}

		Map<Long, OsmNode> nodes = source.loadNodes(RelationMembers.nodeRefs(members));
		List<Long> collectionIds = RelationMembers.collectionRefs(members);
		Map<String, OsmCollectionEntity> collections = collectionIds.isEmpty()
				? Collections.emptyMap() : source.loadCollections(collectionIds);

		int number = 0;
		for (JSONObject member : members) {
			long memberId = RelationMembers.ref(member);
			OsmEntity.EntityOsmType memberOsmType = RelationMembers.osmType(member);
			if (memberOsmType == OsmEntity.EntityOsmType.node) {
				OsmNode node = nodes.get(memberId);
				if (node != null && takesRouteNumber(node)) {
					result.put(memberId, ++number);
				}
				continue;
			}
			if (memberOsmType == null) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(
					DataManager.collectionKey(memberOsmType, memberId));
			if (collection != null && takesRouteNumber(collection)) {
				++number;
				// A route relation's pin is at its start, see RouteRelation.Start.
				OsmNode start = collection.osmType == OsmEntity.EntityOsmType.relation
						? RouteRelation.findStart(collection,
						source.loadNodes(collection.osmNodes)) : null;
				if (start != null) {
					result.put(start.osmID, number);
				}
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
		List<JSONObject> members = RelationMembers.of(parent.jsonNodeInfo);
		List<Long> collectionIds = RelationMembers.collectionRefs(members);
		if (collectionIds.isEmpty()) {
			return;
		}

		Map<String, OsmCollectionEntity> collections = source.loadCollections(collectionIds);
		for (JSONObject member : members) {
			String memberType = member.optString(ClimbingTags.KEY_TYPE);
			OsmEntity.EntityOsmType memberOsmType = RelationMembers.osmType(member);
			if (memberOsmType == OsmEntity.EntityOsmType.node) {
				continue;
			}
			long memberId = RelationMembers.ref(member);
			if (!visited.add(entityKey(memberType, memberId)) || memberOsmType == null) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(
					DataManager.collectionKey(memberOsmType, memberId));
			if (collection == null || !collection.osmType.name().equals(memberType)) {
				continue;
			}
			if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
				// The pitches of a route relation are part of it rather than routes of their own.
				accumulator.addRoute(collection);
				routePartNodes.addAll(collection.osmNodes);
			} else if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				addContainedRouteCollections(collection, visited, accumulator, routePartNodes);
			}
		}
	}

	private static String entityKey(String osmType, long osmId) {
		return osmType + "|" + osmId;
	}
}
