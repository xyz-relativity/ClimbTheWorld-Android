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
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.Map;

public class ClimbingRouteOverlayWidget extends ClimbingOverlayWidget {
	private static final double ZOOM_THRESHOLD = 20;
	private static final int AREA_FILL_COLOR = 0xaaff0000;
	private static final double INFLATE_RATIO = 0.000005; //could be proportional to latitude, but the difference is negligible.

	public ClimbingRouteOverlayWidget(MapView osmMap, DataManagerNew downloadManagerNew, FolderOverlay climbingAreaOverlayFolder, FolderOverlay climbingPointOverlayFolder) {
		super(osmMap, downloadManagerNew, climbingAreaOverlayFolder, climbingPointOverlayFolder);
	}

	boolean inVisibleZoom() {
		return osmMap.getZoomLevelDouble() >= ZOOM_THRESHOLD;
	}

	@Override
	protected Geometry generateGeometry(Map<Long, OsmNode> nodesCache, OsmCollectionEntity collection) {
		return osmToGeometry(nodesCache, collection.osmNodes).buffer(INFLATE_RATIO);
	}

	@Override
	OsmEntity.EntityClimbingType[] getEntityClimbingType() {
		return new OsmEntity.EntityClimbingType[]{OsmEntity.EntityClimbingType.route};
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
