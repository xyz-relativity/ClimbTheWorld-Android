package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds immutable climbing geometry off the UI thread for MapLibre rendering.
 */
public final class ClimbingGeometryBuilder {
	public static final String LABEL_KEY_PROPERTY = "labelKey";

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

		GeometrySpec(String key, List<MapCoordinate> coordinates, boolean polygon, int fillColor,
		             int strokeColor, float minZoom, float maxZoom, MapCoordinate labelCoordinate,
		             String labelName, int relationElementCount, float labelMaxZoom,
		             OsmCollectionEntity collection) {
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
		}

		public String getLabelImageId() {
			return LABEL_IMAGE_PREFIX + key.replace('|', '-') + "-" + relationElementCount;
		}

		public boolean isLabelVisibleAt(double zoom) {
			return labelCoordinate != null && labelName != null && !labelName.isEmpty()
					&& zoom >= minZoom && labelMaxZoom > 0 && zoom < labelMaxZoom;
		}
	}

	private static final int AREA_FILL_COLOR = 0x200000ff;
	private static final int HULL_OUTLINE_COLOR = 0xff000000;
	private static final double AREA_PADDING_DEGREES = 0.00005;
	private static final double CRAG_PADDING_DEGREES = 0.00001;
	private static final double ROUTE_PADDING_DEGREES = 0.000005;
	private static final double OTHER_PADDING_DEGREES = 0.00001;
	private static final int HULL_BUFFER_QUADRANT_SEGMENTS = 16;
	private static final int YELLOW_FILL_COLOR = 0x40ffff00;
	private static final int ROUTE_FILL_COLOR = 0xaaff0000;
	private static final int WAY_COLOR = 0xee3c3c3c;
	private static final float MIN_RENDER_ZOOM = 9;

	private final DataManagerNew dataManager = new DataManagerNew();

	public List<GeometrySpec> load(Context context, MapBounds bounds) {
		List<GeometrySpec> result = new ArrayList<>();
		for (OsmEntity.EntityClimbingType type : OsmEntity.EntityClimbingType.values()) {
			if (type == OsmEntity.EntityClimbingType.NAN) {
				continue;
			}

			List<Long> ids = dataManager.loadCollectionBBox(context, bounds, type);
			Map<Long, OsmCollectionEntity> collections = dataManager.loadCollectionData(context, ids);
			for (OsmCollectionEntity collection : collections.values()) {
				Map<Long, OsmNode> nodes = dataManager.loadNodeData(context, collection.osmNodes);
				List<MapCoordinate> coordinates = toCoordinates(collection, nodes);
				if (coordinates.size() < 2) {
					continue;
				}
				int routeCount = -1;
				if (collection.entityClimbingType == OsmEntity.EntityClimbingType.area
						|| collection.entityClimbingType == OsmEntity.EntityClimbingType.crag) {
					routeCount = 0;
					for (OsmNode node : nodes.values()) {
						if (node.entityClimbingType == OsmEntity.EntityClimbingType.route) {
							routeCount++;
						}
					}
					Set<String> visited = new HashSet<>();
					visited.add(collection.osmType.name() + "|" + collection.osmID);
					routeCount += countContainedRouteCollections(context, collection, visited);
				}
				addGeometry(result, collection, coordinates, routeCount);
			}
		}
		return result;
	}

	public String buildHullGeoJson(List<GeometrySpec> geometries, double zoom, boolean outlines) {
		StringBuilder result = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
		boolean firstFeature = true;
		for (GeometrySpec geometry : geometries) {
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
		boolean firstFeature = true;
		for (GeometrySpec geometry : geometries) {
			if (!geometry.isLabelVisibleAt(zoom)) {
				continue;
			}
			if (!firstFeature) {
				result.append(',');
			}
			firstFeature = false;
			result.append("{\"type\":\"Feature\",\"properties\":{\"")
					.append(LABEL_KEY_PROPERTY).append("\":");
			appendJsonString(result, geometry.key);
			result.append(",\"name\":");
			appendJsonString(result, geometry.labelName);
			result.append(",\"labelIcon\":");
			appendJsonString(result, geometry.getLabelImageId());
			result.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":");
			appendCoordinate(result, geometry.labelCoordinate);
			result.append("}}");
		}
		return result.append("]}").toString();
	}

	public String buildWayGeoJson(List<GeometrySpec> geometries, double zoom) {
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
			result.append("{\"type\":\"Feature\",\"properties\":{},")
					.append("\"geometry\":{\"type\":\"LineString\",\"coordinates\":");
			appendCoordinates(result, geometry.coordinates, false);
			result.append("}}");
		}
		return result.append("]}").toString();
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

	private String toRgba(int color) {
		int alpha = color >>> 24;
		int red = color >> 16 & 0xff;
		int green = color >> 8 & 0xff;
		int blue = color & 0xff;
		return "rgba(" + red + "," + green + "," + blue + "," + alpha / 255.0 + ")";
	}

	private int countContainedRouteCollections(Context context, OsmCollectionEntity parent,
	                                           Set<String> visited) {
		JSONArray members = parent.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (members == null) {
			return 0;
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
			return 0;
		}

		Map<Long, OsmCollectionEntity> collections =
				dataManager.loadCollectionData(context, collectionIds);
		int count = 0;
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
			if (!visited.add(memberType + "|" + memberId)) {
				continue;
			}
			OsmCollectionEntity collection = collections.get(memberId);
			if (collection == null || !collection.osmType.name().equals(memberType)) {
				continue;
			}
			if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
				count++;
			}
			if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				count += countContainedRouteCollections(context, collection, visited);
			}
		}
		return count;
	}

	void addGeometry(List<GeometrySpec> result, OsmCollectionEntity collection,
	                 List<MapCoordinate> coordinates) {
		addGeometry(result, collection, coordinates, -1);
	}

	void addGeometry(List<GeometrySpec> result, OsmCollectionEntity collection,
	                 List<MapCoordinate> coordinates, int relationElementCountOverride) {
		switch (collection.entityClimbingType) {
			case area:
				addHullPolygon(result, collection, coordinates, AREA_FILL_COLOR, HULL_OUTLINE_COLOR,
						AREA_PADDING_DEGREES, MapZoomLevels.AREA_MIN, 0, MapZoomLevels.CRAG_MIN,
						relationElementCountOverride);
				break;
			case route:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							WAY_COLOR, MapZoomLevels.POI_AND_ROUTE_MIN, 0,
							null, null, 0, 0, collection));
				} else {
					addHullPolygon(result, collection, coordinates, ROUTE_FILL_COLOR,
							HULL_OUTLINE_COLOR, ROUTE_PADDING_DEGREES,
							MapZoomLevels.POI_AND_ROUTE_MIN, 0, 0, -1);
				}
				break;
			case crag:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							WAY_COLOR, MapZoomLevels.CRAG_MIN, 0, null, null, 0, 0, collection));
				} else {
					addHullPolygon(result, collection, coordinates, YELLOW_FILL_COLOR,
							HULL_OUTLINE_COLOR, CRAG_PADDING_DEGREES, MapZoomLevels.CRAG_MIN, 0,
							MapZoomLevels.POI_AND_ROUTE_MIN, relationElementCountOverride);
				}
				break;
			case artificial:
			case others:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0,
							WAY_COLOR, MIN_RENDER_ZOOM, 0, null, null, 0, 0, collection));
				} else {
					addHullPolygon(result, collection, coordinates, YELLOW_FILL_COLOR,
							HULL_OUTLINE_COLOR, OTHER_PADDING_DEGREES, MIN_RENDER_ZOOM, 0, 0, -1);
				}
				break;
			default:
				break;
		}
	}

	private void addHullPolygon(List<GeometrySpec> result, OsmCollectionEntity collection,
	                            List<MapCoordinate> coordinates, int fillColor, int strokeColor,
	                            double paddingDegrees, float minZoom, float maxZoom,
	                            float labelMaxZoom, int relationElementCountOverride) {
		if (coordinates.size() < 3) {
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
					relationElementCount, labelMaxZoom, collection));
		}
	}

	private String geometryKey(OsmCollectionEntity collection) {
		return collection.entityClimbingType.name() + "|" + collection.osmID;
	}

	private List<MapCoordinate> toCoordinates(OsmCollectionEntity collection, Map<Long, OsmNode> nodes) {
		List<MapCoordinate> coordinates = new ArrayList<>();
		for (Long nodeId : collection.osmNodes) {
			OsmNode node = nodes.get(nodeId);
			if (node != null) {
				coordinates.add(new MapCoordinate(node.decimalLatitude, node.decimalLongitude));
			}
		}
		return coordinates;
	}
}
