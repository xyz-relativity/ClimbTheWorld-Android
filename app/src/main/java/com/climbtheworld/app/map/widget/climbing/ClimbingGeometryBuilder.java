package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONException;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds immutable climbing geometry off the UI thread for MapLibre rendering.
 */
public final class ClimbingGeometryBuilder {
	public static final String LABEL_KEY_PROPERTY = "labelKey";
	public static final String LABEL_ICON_PROPERTY = "labelIcon";
	public static final String WAY_COLOR_PROPERTY = "color";
	// Only the routes mapped as a way have the dark edge, as in the AR view.
	public static final String WAY_CASING_COLOR_PROPERTY = "casingColor";

	public static final class GeometrySpec {
		private static final String LABEL_IMAGE_PREFIX = "ctw-hull-label-";

		public final String key;
		public final List<MapCoordinate> coordinates;
		public final boolean polygon;
		public final int fillColor;
		public final int strokeColor;
		public final float minZoom;
		public final float maxZoom;
		public final MapCoordinate labelCoordinate;
		public final String labelName;
		public final int relationElementCount;
		public final float labelMaxZoom;
		public final OsmCollectionEntity collection;
		// The route a climbing=route way is drawn as, null for anything else.
		public final RouteWay route;

		GeometrySpec(String key, List<MapCoordinate> coordinates, boolean polygon, int fillColor,
		             int strokeColor, float minZoom, float maxZoom, MapCoordinate labelCoordinate,
		             String labelName, int relationElementCount, float labelMaxZoom,
		             OsmCollectionEntity collection, RouteWay route) {
			this.key = key;
			this.coordinates = coordinates;
			this.polygon = polygon;
			this.fillColor = fillColor;
			this.strokeColor = strokeColor;
			this.minZoom = minZoom;
			this.maxZoom = maxZoom;
			this.labelCoordinate = labelCoordinate;
			this.labelName = labelName;
			this.relationElementCount = relationElementCount;
			this.labelMaxZoom = labelMaxZoom;
			this.collection = collection;
			this.route = route;
		}

		public String getLabelImageId() {
			return LABEL_IMAGE_PREFIX + key.replace('|', '-') + "-" + relationElementCount;
		}

		public boolean isLabelVisibleAt(double zoom) {
			return labelCoordinate != null && labelName != null && !labelName.isEmpty()
					&& zoom >= minZoom && labelMaxZoom > 0 && zoom < labelMaxZoom;
		}
	}

	private static final int AREA_FILL_COLOR = DisplayableGeoNode.AREA_HULL_COLOR;
	private static final int CRAG_FILL_COLOR = DisplayableGeoNode.CRAG_HULL_COLOR;
	private static final int HULL_OUTLINE_COLOR = 0xff000000;
	private static final double AREA_PADDING_DEGREES = 0.00005;
	private static final double CRAG_PADDING_DEGREES = 0.00001;
	private static final double OTHER_PADDING_DEGREES = 0.00001;
	private static final int HULL_BUFFER_QUADRANT_SEGMENTS = 16;
	private static final int YELLOW_FILL_COLOR =
			ColorUtils.setAlphaComponent(0xffff00, DisplayableGeoNode.HULL_FILL_ALPHA);
	private static final int WAY_COLOR = 0xee3c3c3c;

	private final DataManager dataManager = new DataManager();

	public List<GeometrySpec> load(Context context, MapBounds bounds) {
		return load(context, bounds, OsmEntity.EntityClimbingType.features());
	}

	public List<GeometrySpec> load(Context context, MapBounds bounds,
	                               OsmEntity.EntityClimbingType... types) {
		List<GeometrySpec> result = new ArrayList<>();
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(context);
		for (OsmEntity.EntityClimbingType type : types) {
			if (type == OsmEntity.EntityClimbingType.NAN) {
				continue;
			}

			List<Long> ids = dataManager.loadCollectionBBox(context, bounds, type);
			Map<String, OsmCollectionEntity> collections = dataManager.loadCollectionData(context, ids);
			for (OsmCollectionEntity collection : collections.values()) {
				Map<Long, OsmNode> nodes = dataManager.loadNodeData(context, collection.osmNodes);
				List<MapCoordinate> coordinates = toCoordinates(collection, nodes);
				// Hulls are drawn around any number of points, but a way needs two for its line.
				// A crag or area without any route start has no hull, see toCoordinates.
				if (coordinates.isEmpty() || (coordinates.size() < 2
						&& collection.osmType == OsmEntity.EntityOsmType.way)) {
					continue;
				}
				int routeCount = -1;
				if (collection.entityClimbingType == OsmEntity.EntityClimbingType.area
						|| collection.entityClimbingType == OsmEntity.EntityClimbingType.crag) {
					routeCount = routeCounter.summarize(collection, nodes).getRouteCount();
				}
				addGeometry(result, collection, coordinates, routeCount,
						toRouteWay(collection, nodes));
			}
		}
		return result;
	}

	public String buildHullGeoJson(List<GeometrySpec> geometries, double zoom, boolean outlines) {
		StringBuilder result = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
		boolean firstFeature = true;
		for (GeometrySpec geometry : sortByHullDrawOrder(geometries)) {
			if (!geometry.polygon || zoom < geometry.minZoom
					|| (geometry.maxZoom > 0 && zoom > geometry.maxZoom)) {
				continue;
			}
			if (!firstFeature) {
				result.append(',');
			}
			firstFeature = false;
			result.append("{\"type\":\"Feature\",\"properties\":{");
			if (!outlines) {
				result.append("\"fillColor\":\"").append(toRgba(geometry.fillColor)).append('\"');
			}
			result.append("},\"geometry\":{\"type\":\"")
					.append(outlines ? "LineString" : "Polygon")
					.append("\",\"coordinates\":");
			if (!outlines) {
				result.append('[');
			}
			appendClosedCoordinates(result, geometry.coordinates);
			if (!outlines) {
				result.append(']');
			}
			result.append("}}");
		}
		return result.append("]}").toString();
	}

	public String buildHullLabelGeoJson(List<GeometrySpec> geometries, double zoom) {
		StringBuilder result = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
		List<String> features = buildHullLabelFeatures(geometries, zoom);
		for (int index = 0; index < features.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append(features.get(index));
		}
		return result.append("]}").toString();
	}

	/**
	 * Builds one GeoJSON point feature per visible relation label, so the labels can share a
	 * source (and therefore a depth-sorted symbol layer) with the POI pins.
	 */
	public List<String> buildHullLabelFeatures(List<GeometrySpec> geometries, double zoom) {
		List<String> features = new ArrayList<>();
		for (GeometrySpec geometry : geometries) {
			if (!geometry.isLabelVisibleAt(zoom)) {
				continue;
			}
			StringBuilder result = new StringBuilder("{\"type\":\"Feature\",\"properties\":{\"")
					.append(LABEL_KEY_PROPERTY).append("\":");
			appendJsonString(result, geometry.key);
			result.append(",\"name\":");
			appendJsonString(result, geometry.labelName);
			result.append(",\"").append(LABEL_ICON_PROPERTY).append("\":");
			appendJsonString(result, geometry.getLabelImageId());
			result.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":");
			appendCoordinate(result, geometry.labelCoordinate);
			result.append("}}");
			features.add(result.toString());
		}
		return features;
	}

	/**
	 * @param ghosted whether a route mapped as a way fades out like a ghost pin, see
	 *                DisplayableGeoNode#setGhost
	 */
	public String buildWayGeoJson(List<GeometrySpec> geometries, double zoom,
	                              Predicate<GeometrySpec> ghosted) {
		StringBuilder result = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
		boolean firstFeature = true;
		for (GeometrySpec geometry : geometries) {
			if (geometry.polygon || zoom < geometry.minZoom
					|| (geometry.maxZoom > 0 && zoom > geometry.maxZoom)) {
				continue;
			}
			if (!firstFeature) {
				result.append(',');
			}
			firstFeature = false;
			boolean ghost = geometry.route != null && ghosted.test(geometry);
			result.append("{\"type\":\"Feature\",\"properties\":{\"")
					.append(WAY_COLOR_PROPERTY).append("\":\"")
					.append(toRgba(ghost ? toGhost(geometry.strokeColor) : geometry.strokeColor))
					.append('\"');
			if (geometry.route != null) {
				int casingColor = DisplayableGeoNode.LINE_CASING_COLOR;
				result.append(",\"").append(WAY_CASING_COLOR_PROPERTY).append("\":\"")
						.append(toRgba(ghost ? toGhost(casingColor) : casingColor)).append('\"');
			}
			result.append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":");
			appendCoordinates(result, geometry.coordinates, false);
			result.append("}}");
		}
		return result.append("]}").toString();
	}

	/**
	 * MapLibre paints features of one layer in source order, so hulls are emitted bottom-up:
	 * areas first, then crags, then other climbing collections.
	 * The sort is stable, keeping the load order within each type.
	 */
	private static List<GeometrySpec> sortByHullDrawOrder(List<GeometrySpec> geometries) {
		List<GeometrySpec> sorted = new ArrayList<>(geometries);
		Collections.sort(sorted, (first, second) ->
				Integer.compare(hullDrawRank(first), hullDrawRank(second)));
		return sorted;
	}

	private static int hullDrawRank(GeometrySpec geometry) {
		if (geometry.collection == null) {
			return 2;
		}
		switch (geometry.collection.entityClimbingType) {
			case area:
				return 0;
			case crag:
				return 1;
			default:
				return 2;
		}
	}

	private void appendClosedCoordinates(StringBuilder result, List<MapCoordinate> coordinates) {
		appendCoordinates(result, coordinates, true);
	}

	private void appendCoordinates(StringBuilder result, List<MapCoordinate> coordinates, boolean close) {
		result.append('[');
		for (int index = 0; index < coordinates.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			appendCoordinate(result, coordinates.get(index));
		}
		if (close && !coordinates.isEmpty()
				&& !coordinates.get(0).equals(coordinates.get(coordinates.size() - 1))) {
			result.append(',');
			appendCoordinate(result, coordinates.get(0));
		}
		result.append(']');
	}

	private void appendCoordinate(StringBuilder result, MapCoordinate coordinate) {
		result.append('[').append(coordinate.getLongitude()).append(',')
				.append(coordinate.getLatitude()).append(']');
	}

	private void appendJsonString(StringBuilder result, String value) {
		result.append('"');
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"':
					result.append("\\\"");
					break;
				case '\\':
					result.append("\\\\");
					break;
				case '\b':
					result.append("\\b");
					break;
				case '\f':
					result.append("\\f");
					break;
				case '\n':
					result.append("\\n");
					break;
				case '\r':
					result.append("\\r");
					break;
				case '\t':
					result.append("\\t");
					break;
				default:
					if (character < 0x20) {
						result.append("\\u00");
						String hex = Integer.toHexString(character);
						if (hex.length() == 1) {
							result.append('0');
						}
						result.append(hex);
					} else {
						result.append(character);
					}
			}
		}
		result.append('"');
	}

	private static int toGhost(int color) {
		return ColorUtils.setAlphaComponent(color,
				Color.alpha(color) * DisplayableGeoNode.POI_ICON_ALPHA_HIDDEN / 255);
	}

	private String toRgba(int color) {
		int alpha = color >>> 24;
		int red = color >> 16 & 0xff;
		int green = color >> 8 & 0xff;
		int blue = color & 0xff;
		return "rgba(" + red + "," + green + "," + blue + "," + alpha / 255.0 + ")";
	}

	void addGeometry(List<GeometrySpec> result, OsmCollectionEntity collection,
	                 List<MapCoordinate> coordinates) {
		addGeometry(result, collection, coordinates, -1, null);
	}

	/**
	 * @param route the route a climbing=route way is drawn as, see {@link #toRouteWay}
	 */
	void addGeometry(List<GeometrySpec> result, OsmCollectionEntity collection,
	                 List<MapCoordinate> coordinates, int relationElementCountOverride,
	                 RouteWay route) {
		// Route relations, such as the pitches of a multi-pitch route, get no hull, as in the AR
		// view.
		if (isRouteRelation(collection)) {
			return;
		}
		switch (collection.entityClimbingType) {
			case area:
				addHullPolygon(result, collection, coordinates, AREA_FILL_COLOR, HULL_OUTLINE_COLOR,
						AREA_PADDING_DEGREES, MapZoomLevels.AREA_MIN, 0, MapZoomLevels.CRAG_MIN,
						relationElementCountOverride);
				break;
			case route:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					// In the colour of its grade, as in the AR view.
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							route != null ? ColorUtils.setAlphaComponent(route.color,
									DisplayableGeoNode.LINE_ALPHA) : WAY_COLOR,
							wayMinZoom(collection), 0, null, null, 0, 0, collection, route));
				}
				break;
			case crag:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							WAY_COLOR, wayMinZoom(collection), 0, null, null, 0, 0, collection,
							null));
				} else {
					addHullPolygon(result, collection, coordinates, CRAG_FILL_COLOR,
							HULL_OUTLINE_COLOR, CRAG_PADDING_DEGREES, MapZoomLevels.CRAG_MIN, 0,
							MapZoomLevels.POI_AND_ROUTE_MIN, relationElementCountOverride);
				}
				break;
			case artificial:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							WAY_COLOR, wayMinZoom(collection), 0, null, null, 0, 0, collection,
							null));
				} else {
					addHullPolygon(result, collection, coordinates, YELLOW_FILL_COLOR,
							HULL_OUTLINE_COLOR, OTHER_PADDING_DEGREES, MapZoomLevels.AREA_MIN, 0, 0, -1);
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Ways are drawn only while their POIs are: gyms from the area level, everything else from the
	 * POI level (see MapLibreMapWidget#isPoiVisibleAtZoom).
	 */
	private static float wayMinZoom(OsmCollectionEntity collection) {
		return collection.entityClimbingType == OsmEntity.EntityClimbingType.artificial
				? MapZoomLevels.AREA_MIN : MapZoomLevels.POI_AND_ROUTE_MIN;
	}

	private void addHullPolygon(List<GeometrySpec> result, OsmCollectionEntity collection,
	                            List<MapCoordinate> coordinates, int fillColor, int strokeColor,
	                            double paddingDegrees, float minZoom, float maxZoom,
	                            float labelMaxZoom, int relationElementCountOverride) {
		// One or two points are enough: their hull is a point or a line, which the padding buffers
		// into a polygon.
		if (coordinates.isEmpty()) {
			return;
		}
		Coordinate[] points = new Coordinate[coordinates.size()];
		for (int index = 0; index < coordinates.size(); index++) {
			MapCoordinate coordinate = coordinates.get(index);
			points[index] = new Coordinate(coordinate.getLongitude(), coordinate.getLatitude());
		}
		Geometry hull = new ConvexHull(points, new GeometryFactory()).getConvexHull();
		if (paddingDegrees > 0) {
			hull = hull.buffer(paddingDegrees, HULL_BUFFER_QUADRANT_SEGMENTS);
		}
		List<MapCoordinate> hullCoordinates = new ArrayList<>();
		for (Coordinate coordinate : hull.getCoordinates()) {
			hullCoordinates.add(new MapCoordinate(coordinate.y, coordinate.x));
		}
		if (hullCoordinates.size() >= 3) {
			String labelName = null;
			MapCoordinate labelCoordinate = null;
			int relationElementCount = 0;
			if (labelMaxZoom > 0 && collection.osmType == OsmEntity.EntityOsmType.relation) {
				labelName = collection.getTags().optString(ClimbingTags.KEY_NAME, "").trim();
				if (!labelName.isEmpty()) {
					Coordinate centroid = hull.getCentroid().getCoordinate();
					labelCoordinate = new MapCoordinate(centroid.y, centroid.x);
					relationElementCount = relationElementCountOverride >= 0
							? relationElementCountOverride : collection.osmMembers.size();
				}
			}
			result.add(new GeometrySpec(geometryKey(collection), hullCoordinates, true, fillColor,
					strokeColor, minZoom, maxZoom, labelCoordinate, labelName,
					relationElementCount, labelMaxZoom, collection, null));
		}
	}

	/**
	 * @return whether the collection is a relation of a route, tagged climbing=route or
	 * type=route
	 */
	static boolean isRouteRelation(OsmCollectionEntity collection) {
		return collection.osmType == OsmEntity.EntityOsmType.relation
				&& (collection.entityClimbingType == OsmEntity.EntityClimbingType.route
				|| ClimbingTags.VALUE_ROUTE.equals(
						collection.getTags().optString(ClimbingTags.KEY_TYPE)));
	}

	/**
	 * @return the route a climbing=route way is drawn as, null for anything else
	 */
	static RouteWay toRouteWay(OsmCollectionEntity collection, Map<Long, OsmNode> nodes) {
		if (collection.entityClimbingType != OsmEntity.EntityClimbingType.route
				|| collection.osmType != OsmEntity.EntityOsmType.way) {
			return null;
		}
		try {
			return RouteWay.fromWay(collection, nodes);
		} catch (JSONException ignore) {
			return null;
		}
	}

	private String geometryKey(OsmCollectionEntity collection) {
		return collection.entityClimbingType.name() + "|" + collection.osmType.name() + "|"
				+ collection.osmID;
	}

	/**
	 * The hull of a crag or area relation is drawn around the starts of its routes only, leaving
	 * out their tops, belays and any outline of the rock mapped with them; without any route
	 * start, it has none. Anything else is drawn through all its nodes.
	 */
	static List<MapCoordinate> toCoordinates(OsmCollectionEntity collection,
	                                         Map<Long, OsmNode> nodes) {
		boolean routeStartsOnly = collection.osmType == OsmEntity.EntityOsmType.relation
				&& (collection.entityClimbingType == OsmEntity.EntityClimbingType.area
				|| collection.entityClimbingType == OsmEntity.EntityClimbingType.crag);
		List<MapCoordinate> coordinates = new ArrayList<>();
		for (Long nodeId : collection.osmNodes) {
			OsmNode node = nodes.get(nodeId);
			if (node != null && (!routeStartsOnly || isRouteStart(node))) {
				coordinates.add(new MapCoordinate(node.decimalLatitude, node.decimalLongitude));
			}
		}
		return coordinates;
	}

	/**
	 * @return whether the node is where a route starts: a route_bottom, or a route or boulder
	 * mapped as a node, but not a route_top
	 */
	private static boolean isRouteStart(OsmNode node) {
		return ClimbingTags.VALUE_ROUTE_BOTTOM.equals(
				node.getTags().optString(ClimbingTags.KEY_CLIMBING))
				|| ClimbingRouteCounter.takesRouteNumber(node);
	}
}
