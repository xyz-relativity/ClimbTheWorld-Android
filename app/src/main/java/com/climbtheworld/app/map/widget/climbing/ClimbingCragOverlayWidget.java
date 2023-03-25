package com.climbtheworld.app.map.widget.climbing;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;

import org.locationtech.jts.geom.Geometry;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.Map;

public class ClimbingCragOverlayWidget extends ClimbingOverlayWidget {
	private static final double BOTTOM_ZOOM_THRESHOLD = 21;
	private static final double TOP_ZOOM_THRESHOLD = 16;
	private static final int AREA_FILL_COLOR = 0x40ffff00;
	private static final double INFLATE_RATIO = 0.00001; //could be proportional to latitude, but the difference is negligible.

	public ClimbingCragOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew, FolderOverlay climbingAreaOverlayFolder, FolderOverlay climbingPointOverlayFolder) {
		super(osmMap, downloadManagerNew, climbingAreaOverlayFolder, climbingPointOverlayFolder);
	}

	boolean inVisibleZoom() {
		return osmMap.getZoomLevelDouble() <= BOTTOM_ZOOM_THRESHOLD && osmMap.getZoomLevelDouble() >= TOP_ZOOM_THRESHOLD;
	}

	@Override
	protected Geometry generateGeometry(Map<Long, OsmNode> nodesCache, OsmCollectionEntity collection) {
		return osmToGeometry(nodesCache, collection.osmNodes).buffer(INFLATE_RATIO);
	}

	@Override
	OsmEntity.EntityClimbingType[] getEntityClimbingType() {
		return new OsmEntity.EntityClimbingType[]{OsmEntity.EntityClimbingType.crag};
	}

	@Override
	Polygon buildPolygon(MapView osmMap, OsmCollectionEntity collection, Geometry areaGeometry) {
		Polygon polygon = new Polygon();

		polygon.setId(String.valueOf(collection.osmID));
		polygon.setPoints(geometryToOsmCollection(areaGeometry));
		polygon.getFillPaint().setColor(AREA_FILL_COLOR);
		polygon.getOutlinePaint().setStrokeWidth(Globals.convertDpToPixel(2).floatValue());
		return polygon;
	}

	@Override
	Marker buildMarker(MapView osmMap, OsmCollectionEntity collection, Geometry areaGeometry) {
		Marker center = new Marker(osmMap);
		org.locationtech.jts.geom.Point centroid = areaGeometry.getCentroid();
		center.setPosition(new GeoPoint(centroid.getY(), centroid.getX()));
		center.setId(String.valueOf(collection.osmID));
		center.setTitle(collection.getTags().optString(ClimbingTags.KEY_NAME) + " CRAG " + collection.osmID);
		center.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
		return center;
	}
}
