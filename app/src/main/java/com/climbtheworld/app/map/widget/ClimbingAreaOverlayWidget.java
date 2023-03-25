package com.climbtheworld.app.map.widget;

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
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

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
				entry.getValue().center.closeInfoWindow();
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

	private void cleanupNodes(BoundingBox bBox, List<Long> osmBBox) {
		for(Iterator<Map.Entry<Long, Marker>> it = visibleMarkerCache.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Marker> entry = it.next();
			if (osmMap.getZoomLevelDouble() > ZOOM_THRESHOLD) {
				entry.getValue().closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue());
				it.remove();
			} else if(!bBox.contains(entry.getValue().getPosition())) {
				entry.getValue().closeInfoWindow();
				climbingPointOverlayFolder.remove(entry.getValue());
				it.remove();
			}
		}

		for(Iterator<Long> it = osmBBox.iterator(); it.hasNext(); ) {
			Long entry = it.next();
			if(visibleMarkerCache.containsKey(entry)) {
				it.remove();
			}
		}
	}

	private void renderNodes(List<Long> osmBBox) {
		Map<Long, OsmNode> nodes = downloadManagerNew.loadNodeData(osmMap.getContext(), osmBBox);
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
}
