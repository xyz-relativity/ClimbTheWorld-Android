package com.climbtheworld.app.map.widget;

import android.graphics.Point;

import androidx.appcompat.content.res.AppCompatResources;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

public class ClimbingViewWidget {
	final MapView osmMap;
	private final DataManagerNew downloadManagerNew;

	Map <Long, PolygonWithCenter> visibleRelationCache = new HashMap<>();
	Map <Long, Marker> visibleNodeCache = new HashMap<>();

	private final FolderOverlay climbingAreaOverlayFolder = new FolderOverlay();
	private final FolderOverlay climbingWayOverlayFolder = new FolderOverlay();
	private final FolderOverlay climbingPointOverlayFolder = new FolderOverlay();

	private boolean forceUpdate = false;
	private static final Semaphore refreshLock = new Semaphore(1);

	public ClimbingViewWidget(MapView osmMap) {
		this.osmMap = osmMap;
		downloadManagerNew = new DataManagerNew();
	}

	public void setForceUpdate(boolean forceUpdate) {
		this.forceUpdate = forceUpdate;
	}

	public List<FolderOverlay> getBackgroundOverlays() {
		return Arrays.asList(climbingAreaOverlayFolder, climbingWayOverlayFolder);
	}

	public FolderOverlay getForegroundOverlay() {
		return climbingPointOverlayFolder;
	}

	public void refresh(BoundingBox bBox, boolean cancelable, UiRelatedTask<Boolean> booleanUiRelatedTask) {
		if (!refreshLock.tryAcquire()) {
			return;
		}

		//if forced reset all markers
		if (forceUpdate) {
			climbingAreaOverlayFolder.getItems().clear();
			climbingWayOverlayFolder.getItems().clear();
			climbingPointOverlayFolder.getItems().clear();
			visibleRelationCache.clear();
			forceUpdate = false;
		}

		List<Long> osmBBox = downloadManagerNew.loadCollectionBBox(osmMap.getContext(), bBox, OsmEntity.EntityClimbingType.area);
		renderRelations(bBox, osmBBox, 0x40ffffff, 0.0001);

		osmBBox = downloadManagerNew.loadCollectionBBox(osmMap.getContext(), bBox, OsmEntity.EntityClimbingType.crag);
		renderRelations(bBox, osmBBox, 0x60ffff00, 0.00001);

		osmBBox = downloadManagerNew.loadModeBBox(osmMap.getContext(), bBox, OsmEntity.EntityClimbingType.route);
		renderNodes(bBox, osmBBox);
		refreshLock.release();
	}

	private void renderNodes(BoundingBox bBox, List<Long> osmBBox) {
		for(Iterator<Map.Entry<Long, Marker>> it = visibleNodeCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Marker> entry = it.next();
			if(!bBox.contains(entry.getValue().getPosition())) {
				climbingPointOverlayFolder.remove(entry.getValue());
				it.remove();
			}
		}

		for(Iterator<Long> it = osmBBox.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleNodeCache.containsKey(entry)) {
				it.remove();
			}
		}

		Map<Long, OsmNode> nodes = downloadManagerNew.loadNodeData(osmMap.getContext(), osmBBox);
		for (OsmNode node: nodes.values()) {
			Marker center = new Marker(osmMap);
			center.setPosition(node.toGeoPoint());
			center.setId(String.valueOf(node.osmID));
			center.setTitle(node.getTags().optString(ClimbingTags.KEY_NAME));
			center.setIcon(AppCompatResources.getDrawable(osmMap.getContext(), R.drawable.ic_poi_info));
			center.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
			visibleNodeCache.put(node.osmID, center);

			climbingPointOverlayFolder.add(center);
		}
	}

	private void renderRelations(BoundingBox bBox, List<Long> osmBBox, int fillColor, double inflateOffset) {
		for(Iterator<Map.Entry<Long, PolygonWithCenter>> it = visibleRelationCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, PolygonWithCenter> entry = it.next();
			if(!bBox.overlaps(entry.getValue().boundingBox, 99)) {
				climbingPointOverlayFolder.remove(entry.getValue().center);
				climbingAreaOverlayFolder.remove(entry.getValue().polygon);
				it.remove();
			}
		}

		for(Iterator<Long> it = osmBBox.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleRelationCache.containsKey(entry)) {
				it.remove();
			}
		}

		Map<Long, OsmCollectionEntity> area = downloadManagerNew.loadCollectionData(osmMap.getContext(), osmBBox);
		for (OsmCollectionEntity collection: area.values()) {
			if (collection.osmType != OsmEntity.EntityOsmType.relation) {
				continue;
			}

			Map<Long, OsmNode> nodesCache = downloadManagerNew.loadNodeData(osmMap.getContext(), collection.convexHall);

			PolygonWithCenter poly = new PolygonWithCenter(collection, fillColor, inflateOffset, nodesCache);
			visibleRelationCache.put(collection.osmID, poly);

			climbingPointOverlayFolder.add(poly.center);
			climbingAreaOverlayFolder.add(poly.polygon);
		}
	}

	private void zIndexMarkers() {
		if (!refreshLock.tryAcquire()) {
			return;
		}

		Collections.sort(climbingPointOverlayFolder.getItems(), new Comparator<Overlay>() {
			final Point tempPoint1 = new Point();
			final Point tempPoint2 = new Point();
			final Projection projection = osmMap.getProjection();

			@Override
			public int compare(Overlay element1, Overlay element2) {
				projection.toPixels(((Marker)element1).getPosition(), tempPoint1);
				projection.rotateAndScalePoint(tempPoint1.x, tempPoint1.y, tempPoint1);

				projection.toPixels(((Marker)element2).getPosition(), tempPoint2);
				projection.rotateAndScalePoint(tempPoint2.x, tempPoint2.y, tempPoint2);

				return Double.compare(tempPoint1.y, tempPoint2.y);
			}
		});
		refreshLock.release();
	}

	public void invalidate() {
		zIndexMarkers();
	}

	private class PolygonWithCenter {
		final Marker center;
		final Polygon polygon;
		final BoundingBox boundingBox;

		public PolygonWithCenter(OsmCollectionEntity data, int fillColor, double inflateOffset, Map<Long, OsmNode> nodesCache) {
			boundingBox = new BoundingBox(data.bBoxNorth, data.bBoxEast, data.bBoxSouth, data.bBoxWest);
			center = new Marker(osmMap);
			center.setPosition(new GeoPoint(data.centerDecimalLatitude, data.centerDecimalLongitude));
			center.setId(String.valueOf(data.osmID));
			center.setTitle(data.getTags().optString(ClimbingTags.KEY_NAME));
			center.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);

			polygon = new Polygon();
			polygon.setId(String.valueOf(data.osmID));
			polygon.setPoints(toInflatedGeoPoly(nodesCache, data.convexHall, inflateOffset));
			polygon.getFillPaint().setColor(fillColor);
			polygon.getOutlinePaint().setStrokeWidth(Globals.convertDpToPixel(2).floatValue());
		}

		private List<GeoPoint> toInflatedGeoPoly(Map<Long, OsmNode> nodesCache, List<Long> relation, double offset) {
			List<Coordinate> convexHall = new LinkedList<>();
			for (Long nodeId: relation) {
				OsmNode node = nodesCache.get(nodeId);
				convexHall.add(new Coordinate(node.decimalLongitude, node.decimalLatitude));
			}
			convexHall.add(convexHall.get(0));

			GeometryFactory geometryFactory = new GeometryFactory();
			org.locationtech.jts.geom.Polygon inflatedPolygon = geometryFactory.createPolygon(convexHall.toArray(new Coordinate[0]));

			List<GeoPoint> bgRectPoints = new ArrayList<>();
			for (Coordinate nodeId: inflatedPolygon.buffer(offset).getCoordinates()) {
				bgRectPoints.add(new GeoPoint(nodeId.y, nodeId.x));
			}

			return bgRectPoints;
		}
	}
}
