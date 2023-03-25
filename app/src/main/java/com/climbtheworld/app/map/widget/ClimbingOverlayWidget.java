package com.climbtheworld.app.map.widget;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.OsmNode;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ClimbingOverlayWidget {
	static class PolygonWithCenter {
		Marker center;
		Polygon polygon;

		public PolygonWithCenter(Marker center, Polygon polygon) {
			this.center = center;
			this.polygon = polygon;
		}
	}

	final DataManagerNew downloadManagerNew;
	final MapView osmMap;

	public ClimbingOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew) {
		this.osmMap = osmMap;
		this.downloadManagerNew = downloadManagerNew;
	}

	static Geometry osmToGeometry(Map<Long, OsmNode> nodesCache, List<Long> relation) {
		Coordinate[] convexHall = new Coordinate[relation.size()];
		int i = 0;
		for (Long nodeId: relation) {
			OsmNode node = nodesCache.get(nodeId);
			convexHall[i] = new Coordinate(node.decimalLongitude, node.decimalLatitude);
			i++;
		}

		GeometryFactory geometryFactory = new GeometryFactory();

		ConvexHull convexHullBuilder = new ConvexHull(convexHall, geometryFactory);

		return convexHullBuilder.getConvexHull();
	}

	static List<GeoPoint> geometryToOsmCollection(Geometry geometry) {
		List<GeoPoint> bgRectPoints = new ArrayList<>();
		for (Coordinate nodeId: geometry.getCoordinates()) {
			bgRectPoints.add(new GeoPoint(nodeId.y, nodeId.x));
		}

		return bgRectPoints;
	}

	abstract boolean inVisibleZoom();
}
