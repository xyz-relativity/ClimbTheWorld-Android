package com.climbtheworld.app.map.widget.climbing;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayWithIW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public abstract class ClimbingOverlayWidget {
	static class PolygonWithCenter {
		Marker center;
		OverlayWithIW polygon;

		public PolygonWithCenter(OverlayWithIW polygon, Marker center) {
			this.center = center;
			this.polygon = polygon;
		}

		public boolean hasPolygon() {
			return polygon != null;
		}

		public boolean hasCenter() {
			return center != null;
		}
	}

	OsmEntity.EntityClimbingType[] entityClimbingType;

	private final FolderOverlay climbingAreaOverlayFolder;
	private final FolderOverlay climbingWayOverlayFolder;
	private final FolderOverlay climbingPointOverlayFolder;
	final DataManagerNew downloadManagerNew;
	final MapView osmMap;

	Map <Long, PolygonWithCenter> visibleAreaCache = new HashMap<>();
	Map <Long, PolygonWithCenter> visibleWayCache = new HashMap<>();
	Map <Long, Marker> visibleMarkerCache = new HashMap<>();

	public ClimbingOverlayWidget(ClimbingViewWidget climbingViewWidget) {
		this.osmMap = climbingViewWidget.osmMap;
		this.downloadManagerNew = climbingViewWidget.downloadManagerNew;
		this.climbingAreaOverlayFolder = climbingViewWidget.climbingAreaOverlayFolder;
		this.climbingWayOverlayFolder = climbingViewWidget.climbingWayOverlayFolder;
		this.climbingPointOverlayFolder = climbingViewWidget.climbingPointOverlayFolder;
	}

	public void refresh(BoundingBox bBox, boolean cancelable, UiRelatedTask<Boolean> booleanUiRelatedTask) {
		List<Long> osmRelations = downloadManagerNew.loadCollectionBBox(osmMap.getContext(), bBox, entityClimbingType);
		List<Long> osmNodes = downloadManagerNew.loadNodeBBox(osmMap.getContext(), bBox, entityClimbingType);
		cleanupRelations(bBox, osmRelations);
		renderRelations(osmRelations);

		cleanupNodes(bBox, osmNodes);
		renderNodes(osmNodes);
	}

	private void cleanupRelations(BoundingBox bBox, List<Long> osmRelations) {
		for(Iterator<Map.Entry<Long, PolygonWithCenter>> it = visibleAreaCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, PolygonWithCenter> entry = it.next();
			if(!bBox.overlaps(entry.getValue().polygon.getBounds(), 99)) {
				climbingAreaOverlayFolder.remove(entry.getValue().polygon);
				if (entry.getValue().hasCenter()) {
					entry.getValue().center.closeInfoWindow();
					climbingPointOverlayFolder.remove(entry.getValue().center);
					visibleMarkerCache.remove(entry.getKey());
				}
				it.remove();
				continue;
			}

			if (!inVisibleZoom() && entry.getValue().hasCenter()) {
				entry.getValue().center.closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue().center);
				visibleMarkerCache.remove(entry.getKey());
			}
		}

		for(Iterator<Long> it = osmRelations.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleAreaCache.containsKey(entry)) {
				PolygonWithCenter crObject = visibleAreaCache.get(entry);
				if (inVisibleZoom() && !visibleMarkerCache.containsKey(entry) && crObject.hasCenter()) {
					visibleMarkerCache.put(entry, crObject.center);
					climbingPointOverlayFolder.add(crObject.center);
				}
				it.remove();
			}
		}
	}

	private void renderRelations(List<Long> osmRelations) {
		Map<Long, OsmCollectionEntity> area = downloadManagerNew.loadCollectionData(osmMap.getContext(), osmRelations);
		for (OsmCollectionEntity collection: area.values()) {
			Map<Long, OsmNode> nodesCache = downloadManagerNew.loadNodeData(osmMap.getContext(), collection.osmNodes);

			PolygonWithCenter poly = buildCollectionOverlay(collection, nodesCache);
			if (poly.hasPolygon()) {
				visibleAreaCache.put(collection.osmID, poly);
				climbingAreaOverlayFolder.add(poly.polygon);
			}

			if (inVisibleZoom() && poly.hasCenter()) {
				climbingPointOverlayFolder.add(poly.center);
				visibleMarkerCache.put(collection.osmID, poly.center);
			}
		}
	}

	private void cleanupNodes(BoundingBox bBox, List<Long> osmNodes) {
		for(Iterator<Map.Entry<Long, Marker>> it = visibleMarkerCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Marker> entry = it.next();
			if (!inVisibleZoom()) {
				entry.getValue().closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue());
				it.remove();
				continue;
			}
			if(!bBox.contains(entry.getValue().getPosition())) {
				entry.getValue().closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue());
				it.remove();
			}
		}

		for(Iterator<Long> it = osmNodes.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(!inVisibleZoom() || visibleMarkerCache.containsKey(entry)) {
				it.remove();
			}
		}
	}

	private void renderNodes(List<Long> osmNodes) {
		Map<Long, OsmNode> nodes = downloadManagerNew.loadNodeData(osmMap.getContext(), osmNodes);
		for (OsmNode node: nodes.values()) {
			PolygonWithCenter poly = buildCollectionOverlay(node, null);

			if (poly.hasCenter()) {
				visibleMarkerCache.put(node.osmID, poly.center);
				climbingPointOverlayFolder.add(poly.center);
			}
		}
	}

	static Geometry osmToConvexHullGeometry(Map<Long, OsmNode> nodesCache, List<Long> relation) {
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

	static List<GeoPoint> osmCollectionToGeoPoints(OsmCollectionEntity geometry, Map<Long, OsmNode> nodesCache) {
		List<GeoPoint> bgRectPoints = new ArrayList<>();
		for (Long nodeId: geometry.osmNodes) {
			bgRectPoints.add(nodesCache.get(nodeId).toGeoPoint());
		}

		return bgRectPoints;
	}

	static List<GeoPoint> geometryToGeoPoints(Geometry geometry) {
		List<GeoPoint> bgRectPoints = new ArrayList<>();
		for (Coordinate nodeId: geometry.getCoordinates()) {
			bgRectPoints.add(new GeoPoint(nodeId.y, nodeId.x));
		}

		return bgRectPoints;
	}

	abstract boolean inVisibleZoom();
	protected abstract PolygonWithCenter buildCollectionOverlay(OsmEntity collection, Map<Long, OsmNode> nodesCache);
}
