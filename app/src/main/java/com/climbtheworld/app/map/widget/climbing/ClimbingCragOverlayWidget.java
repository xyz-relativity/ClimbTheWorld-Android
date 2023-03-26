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
import com.climbtheworld.app.utils.Globals;

import org.locationtech.jts.geom.Geometry;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayWithIW;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.Map;

public class ClimbingCragOverlayWidget extends ClimbingOverlayWidget {
	private static final double BOTTOM_ZOOM_THRESHOLD = 20;
	private static final double TOP_ZOOM_THRESHOLD = 17;
	private static final int AREA_FILL_COLOR = 0x40ffff00;
	private static final double INFLATE_RATIO = 0.00001; //could be proportional to latitude, but the difference is negligible.

	public ClimbingCragOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew, FolderOverlay climbingAreaOverlayFolder, FolderOverlay climbingPointOverlayFolder) {
		super(osmMap, downloadManagerNew, climbingAreaOverlayFolder, climbingPointOverlayFolder);
	}

	boolean inVisibleZoom() {
		return osmMap.getZoomLevelDouble() <= BOTTOM_ZOOM_THRESHOLD && osmMap.getZoomLevelDouble() >= TOP_ZOOM_THRESHOLD;
	}

	@Override
	OsmEntity.EntityClimbingType[] getEntityClimbingType() {
		return new OsmEntity.EntityClimbingType[]{OsmEntity.EntityClimbingType.crag};
	}

	@Override
	protected PolygonWithCenter buildCollectionOverlay(OsmEntity collection, Map<Long, OsmNode> nodesCache) {
		OverlayWithIW polygon = null;
		Marker center = null;

		if (collection.osmType == OsmEntity.EntityOsmType.relation) {
			Geometry geometry = osmToConvexHullGeometry(nodesCache, ((OsmCollectionEntity)collection).osmNodes).buffer(INFLATE_RATIO);
			polygon = buildPolygon(collection, geometry);
			center = buildMarker(collection, geometry);
		}

		if (collection.osmType == OsmEntity.EntityOsmType.way) {
			polygon = buildPolyline(collection, nodesCache);
		}

		if (collection.osmType == OsmEntity.EntityOsmType.node) {
			assert collection instanceof OsmNode;
			center = buildMarker(collection, ((OsmNode)collection).toGeoPoint());
		}

		return new PolygonWithCenter(polygon, center);
	}

	private OverlayWithIW buildPolyline(OsmEntity collection, Map<Long, OsmNode> nodesCache) {
		Polyline polyLine = new Polyline(osmMap);
		polyLine.setPoints(osmCollectionToGeoPoints((OsmCollectionEntity) collection, nodesCache));
		polyLine.getOutlinePaint().setColor(0xee3c3c3c);
		return polyLine;
	}

	Polygon buildPolygon(OsmEntity collection, Geometry areaGeometry) {
		Polygon polygon = new Polygon();

		polygon.setId(String.valueOf(collection.osmID));
		polygon.setPoints(geometryToGeoPoints(areaGeometry));
		polygon.getFillPaint().setColor(AREA_FILL_COLOR);
		polygon.getOutlinePaint().setStrokeWidth(Globals.convertDpToPixel(2).floatValue());
		return polygon;
	}

	Marker buildMarker(OsmEntity collection, Geometry coordinates) {
		org.locationtech.jts.geom.Point centroid = coordinates.getCentroid();
		return buildMarker(collection, new GeoPoint(centroid.getY(), centroid.getX()));
	}

	Marker buildMarker(OsmEntity collection, GeoPoint coordinates) {
		Marker center = new Marker(osmMap);
		center.setPosition(coordinates);
		center.setPanToView(false);
		center.setId(String.valueOf(collection.osmID));
		center.setTitle(collection.getTags().optString(ClimbingTags.KEY_NAME) + " ROUTE " + collection.osmID);
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
		return center;
	}
}
