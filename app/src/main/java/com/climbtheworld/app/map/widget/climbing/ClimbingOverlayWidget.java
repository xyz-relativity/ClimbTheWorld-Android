package com.climbtheworld.app.map.widget.climbing;

import android.view.View;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
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
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public abstract class ClimbingOverlayWidget {
	static class PolygonWithCenter {
		Marker center;
		Polygon polygon;

		public PolygonWithCenter(Marker center, Polygon polygon) {
			this.center = center;
			this.polygon = polygon;
		}
	}

	private final FolderOverlay climbingAreaOverlayFolder;
	private final FolderOverlay climbingPointOverlayFolder;

	Map <Long, PolygonWithCenter> visibleAreaCache = new HashMap<>();
	Map <Long, Marker> visibleMarkerCache = new HashMap<>();

	final DataManagerNew downloadManagerNew;
	final MapView osmMap;

	public ClimbingOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew, FolderOverlay climbingAreaOverlayFolder, FolderOverlay climbingPointOverlayFolder) {
		this.osmMap = osmMap;
		this.downloadManagerNew = downloadManagerNew;
		this.climbingAreaOverlayFolder = climbingAreaOverlayFolder;
		this.climbingPointOverlayFolder = climbingPointOverlayFolder;
	}

	public void refresh(BoundingBox bBox, boolean cancelable, UiRelatedTask<Boolean> booleanUiRelatedTask) {
		List<Long> osmRelations = downloadManagerNew.loadCollectionBBox(osmMap.getContext(), bBox, getEntityClimbingType());
		List<Long> osmNodes = downloadManagerNew.loadNodeBBox(osmMap.getContext(), bBox, getEntityClimbingType());
		cleanupRelations(bBox, osmRelations);
		cleanupNodes(bBox, osmNodes);

		renderRelations(osmRelations);
		renderNodes(osmNodes);
	}

	private void cleanupRelations(BoundingBox bBox, List<Long> osmRelations) {
		for(Iterator<Map.Entry<Long, PolygonWithCenter>> it = visibleAreaCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, PolygonWithCenter> entry = it.next();
			if(!bBox.overlaps(entry.getValue().polygon.getBounds(), 99)) {
				climbingAreaOverlayFolder.remove(entry.getValue().polygon);
				entry.getValue().center.closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue().center);
				visibleMarkerCache.remove(entry.getKey());
				it.remove();
				continue;
			}

			if (!inVisibleZoom()) {
				entry.getValue().center.closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue().center);
				visibleMarkerCache.remove(entry.getKey());
			}
		}

		for(Iterator<Long> it = osmRelations.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleAreaCache.containsKey(entry)) {
				PolygonWithCenter crObject = visibleAreaCache.get(entry);
				if (inVisibleZoom() && !visibleMarkerCache.containsKey(entry)) {
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

			Geometry areaGeometry = generateGeometry(nodesCache, collection);

			Marker center = buildMarker(osmMap, collection, areaGeometry);
			Polygon polygon = buildPolygon(osmMap, collection, areaGeometry);

			PolygonWithCenter poly = new PolygonWithCenter(center, polygon);
			visibleAreaCache.put(collection.osmID, poly);
			climbingAreaOverlayFolder.add(polygon);

			if (inVisibleZoom()) {
				climbingPointOverlayFolder.add(center);
				visibleMarkerCache.put(collection.osmID, center);
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
			Marker center = new Marker(osmMap);
			center.setPosition(node.toGeoPoint());
			center.setId(String.valueOf(node.osmID));
			center.setTitle(node.getTags().optString(ClimbingTags.KEY_NAME));
			center.setIcon(AppCompatResources.getDrawable(osmMap.getContext(), R.drawable.ic_poi_info));
			center.setInfoWindow(new InfoWindow(R.layout.fragment_info_window_route, osmMap) {
				@Override
				public void onOpen(Object item) {
					closeAllInfoWindowsOn(osmMap);
					mView.setAlpha((float) 0.94);
					((TextView)mView.findViewById(R.id.textTitle)).setText(center.getTitle());
					mView.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							close();
						}
					});
				}

				@Override
				public void onClose() {

				}
			});
			center.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
			visibleMarkerCache.put(node.osmID, center);

			climbingPointOverlayFolder.add(center);
		}
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
	abstract OsmEntity.EntityClimbingType[] getEntityClimbingType();
	protected abstract Geometry generateGeometry(Map<Long, OsmNode> nodesCache, OsmCollectionEntity collection);
	abstract Polygon buildPolygon(MapView osmMap, OsmCollectionEntity collection, Geometry areaGeometry);
	abstract Marker buildMarker(MapView osmMap, OsmCollectionEntity collection, Geometry areaGeometry);
}
