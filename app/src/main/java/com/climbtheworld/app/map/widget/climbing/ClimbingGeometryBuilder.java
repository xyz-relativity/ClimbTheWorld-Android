package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds immutable climbing geometry off the UI thread for MapLibre rendering.
 */
public final class ClimbingGeometryBuilder {
	public static final class GeometrySpec {
		public final String key;
		public final List<MapCoordinate> coordinates;
		public final boolean polygon;
		public final int fillColor;
		public final int strokeColor;
		public final float minZoom;
		public final float maxZoom;

		private GeometrySpec(String key, List<MapCoordinate> coordinates, boolean polygon, int fillColor,
		                     int strokeColor, float minZoom, float maxZoom) {
			this.key = key;
			this.coordinates = coordinates;
			this.polygon = polygon;
			this.fillColor = fillColor;
			this.strokeColor = strokeColor;
			this.minZoom = minZoom;
			this.maxZoom = maxZoom;
		}
	}

	private static final int AREA_FILL_COLOR = 0x200000ff;
	private static final int YELLOW_FILL_COLOR = 0x40ffff00;
	private static final int ROUTE_FILL_COLOR = 0xaaff0000;
	private static final int WAY_COLOR = 0xee3c3c3c;
	private static final float MIN_RENDER_ZOOM = 9;
	private static final float AREA_MAX_ZOOM = 18;
	private static final float CRAG_MIN_ZOOM = 17;
	private static final float CRAG_MAX_ZOOM = 20;
	private static final float ROUTE_MIN_ZOOM = 19;

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
				addGeometry(result, collection, coordinates);
			}
		}
		return result;
	}

	private void addGeometry(List<GeometrySpec> result, OsmCollectionEntity collection,
	                         List<MapCoordinate> coordinates) {
		switch (collection.entityClimbingType) {
			case area:
				addHullPolygon(result, collection, coordinates, AREA_FILL_COLOR, AREA_MAX_ZOOM, 0);
				break;
			case route:
				addHullPolygon(result, collection, coordinates, ROUTE_FILL_COLOR, 0, ROUTE_MIN_ZOOM);
				break;
			case crag:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0, WAY_COLOR, CRAG_MIN_ZOOM, CRAG_MAX_ZOOM));
				} else {
					addHullPolygon(result, collection, coordinates, YELLOW_FILL_COLOR, CRAG_MIN_ZOOM, CRAG_MAX_ZOOM);
				}
				break;
			case artificial:
			case others:
				if (collection.osmType == OsmEntity.EntityOsmType.way) {
					result.add(new GeometrySpec(geometryKey(collection), coordinates, false, 0, WAY_COLOR, MIN_RENDER_ZOOM, 0));
				} else {
					addHullPolygon(result, collection, coordinates, YELLOW_FILL_COLOR, MIN_RENDER_ZOOM, 0);
				}
				break;
			default:
				break;
		}
	}

	private void addHullPolygon(List<GeometrySpec> result, OsmCollectionEntity collection,
	                            List<MapCoordinate> coordinates, int fillColor, float minZoom, float maxZoom) {
		if (coordinates.size() < 3) {
			return;
		}
		Coordinate[] points = new Coordinate[coordinates.size()];
		for (int index = 0; index < coordinates.size(); index++) {
			MapCoordinate coordinate = coordinates.get(index);
			points[index] = new Coordinate(coordinate.getLongitude(), coordinate.getLatitude());
		}
		Geometry hull = new ConvexHull(points, new GeometryFactory()).getConvexHull();
		List<MapCoordinate> hullCoordinates = new ArrayList<>();
		for (Coordinate coordinate : hull.getCoordinates()) {
			hullCoordinates.add(new MapCoordinate(coordinate.y, coordinate.x));
		}
		if (hullCoordinates.size() >= 3) {
			result.add(new GeometrySpec(geometryKey(collection), hullCoordinates, true, fillColor, WAY_COLOR, minZoom, maxZoom));
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
