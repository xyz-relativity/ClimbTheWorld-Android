package com.climbtheworld.app.map.widget;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.locationtech.jts.geom.Geometry;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public class ClimbingAreaOverlayWidget extends ClimbingOverlayWidget {
	private final FolderOverlay climbingAreaOverlayFolder;
	private final FolderOverlay climbingPointOverlayFolder;

	Map <Long, PolygonWithCenter> visibleAreaCache = new HashMap<>();
	Map <Long, Marker> visibleMarkerCache = new HashMap<>();
	private static final double ZOOM_THRESHOLD = 16;
	private static final int AREA_FILL_COLOR = 0x200000ff;
	private static final double INFLATE_RATIO = 0.0001; //could be proportional to latitude, but the difference is negligible.

	public ClimbingAreaOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew, FolderOverlay climbingAreaOverlayFolder, FolderOverlay climbingPointOverlayFolder) {
		super(osmMap, downloadManagerNew);
		this.climbingAreaOverlayFolder = climbingAreaOverlayFolder;
		this.climbingPointOverlayFolder = climbingPointOverlayFolder;
	}

	public void refresh(BoundingBox bBox, boolean cancelable, UiRelatedTask<Boolean> booleanUiRelatedTask) {
		List<Long> osmRelations = downloadManagerNew.loadCollectionBBox(osmMap.getContext(), bBox, OsmEntity.EntityClimbingType.area);
		List<Long> osmNodes = downloadManagerNew.loadNodeBBox(osmMap.getContext(), bBox, OsmEntity.EntityClimbingType.area);
		viewCleanup(bBox, osmRelations, osmNodes);
		renderRelations(osmRelations);
	}

	private void viewCleanup(BoundingBox bBox, List<Long> osmRelations, List<Long> osmNodes) {
		for(Iterator<Map.Entry<Long, PolygonWithCenter>> it = visibleAreaCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, PolygonWithCenter> entry = it.next();
			if(!bBox.overlaps(entry.getValue().polygon.getBounds(), 99)) {
				climbingAreaOverlayFolder.remove(entry.getValue().polygon);
				climbingPointOverlayFolder.remove(entry.getValue().center);
				visibleMarkerCache.remove(entry.getKey());
				it.remove();
			} else if (osmMap.getZoomLevelDouble() > ZOOM_THRESHOLD) {
				entry.getValue().center.closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue().center);
				visibleMarkerCache.remove(entry.getKey());
			}
		}

		for(Iterator<Long> it = osmRelations.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleAreaCache.containsKey(entry)) {
				PolygonWithCenter crObject = visibleAreaCache.get(entry);
				if (osmMap.getZoomLevelDouble() <= ZOOM_THRESHOLD && !visibleMarkerCache.containsKey(entry)) {
					visibleMarkerCache.put(entry, crObject.center);
					climbingPointOverlayFolder.add(crObject.center);
				}
				it.remove();
			}
		}
	}

	private void renderRelations(List<Long> osmBBox) {
		Map<Long, OsmCollectionEntity> area = downloadManagerNew.loadCollectionData(osmMap.getContext(), osmBBox);
		for (OsmCollectionEntity collection: area.values()) {
			if (collection.osmType != OsmEntity.EntityOsmType.relation) {
				continue;
			}

			Map<Long, OsmNode> nodesCache = downloadManagerNew.loadNodeData(osmMap.getContext(), collection.osmNodes);

			Marker center = new Marker(osmMap);
			Geometry areaGeometry = osmToGeometry(nodesCache, collection.osmNodes).buffer(INFLATE_RATIO);
			org.locationtech.jts.geom.Point centroid = areaGeometry.getCentroid();
			center.setPosition(new GeoPoint(centroid.getY(), centroid.getX()));
			center.setId(String.valueOf(collection.osmID));
			center.setTitle(collection.getTags().optString(ClimbingTags.KEY_NAME));
			center.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);

			Polygon polygon = new Polygon();
			polygon.setId(String.valueOf(collection.osmID));
			polygon.setPoints(geometryToOsmCollection(areaGeometry));
			polygon.getFillPaint().setColor(AREA_FILL_COLOR);
			polygon.getOutlinePaint().setStrokeWidth(Globals.convertDpToPixel(2).floatValue());

			PolygonWithCenter poly = new PolygonWithCenter(center, polygon);
			visibleAreaCache.put(collection.osmID, poly);
			climbingAreaOverlayFolder.add(polygon);

			climbingPointOverlayFolder.add(center);
			visibleMarkerCache.put(collection.osmID, center);
		}
	}
}
