package com.climbtheworld.app.map.widget.climbing;

import android.graphics.Point;

import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

public class ClimbingViewWidget {
	final MapView osmMap;
	final DataManagerNew downloadManagerNew;

	Map <OsmEntity.EntityClimbingType, Map<Long, Marker>> visibleMarkerCache = new HashMap<>();

	final FolderOverlay climbingAreaOverlayFolder = new FolderOverlay();
	final FolderOverlay climbingWayOverlayFolder = new FolderOverlay();
	final FolderOverlay climbingPointOverlayFolder = new FolderOverlay();

	private final ClimbingAreaOverlayWidget climbingAreaOverlayWidget;
	private final ClimbingCragOverlayWidget climbingCragOverlayWidget;
	private final ClimbingRouteOverlayWidget climbingRouteOverlayWidget;
	private final ClimbingArtificialOverlayWidget climbingArtificialOverlayWidget;
	private final ClimbingOthersOverlayWidget climbingOthersOverlayWidget;

	private boolean forceUpdate = false;
	private static final Semaphore refreshLock = new Semaphore(1);

	public ClimbingViewWidget(MapView osmMap) {
		this.osmMap = osmMap;
		downloadManagerNew = new DataManagerNew();
		climbingAreaOverlayWidget = new ClimbingAreaOverlayWidget(this);
		climbingCragOverlayWidget = new ClimbingCragOverlayWidget(this);
		climbingRouteOverlayWidget = new ClimbingRouteOverlayWidget(this);
		climbingArtificialOverlayWidget = new ClimbingArtificialOverlayWidget(this);
		climbingOthersOverlayWidget = new ClimbingOthersOverlayWidget(this);

		for (OsmEntity.EntityClimbingType type: OsmEntity.EntityClimbingType.values()) {
			visibleMarkerCache.put(type, new HashMap<>());
		}
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
			forceUpdate = false;
		}

		System.out.println("Zoom level: " + osmMap.getZoomLevelDouble());

		climbingAreaOverlayWidget.refresh(bBox, cancelable, booleanUiRelatedTask);
		climbingCragOverlayWidget.refresh(bBox, cancelable, booleanUiRelatedTask);
		climbingRouteOverlayWidget.refresh(bBox, cancelable, booleanUiRelatedTask);
		climbingArtificialOverlayWidget.refresh(bBox, cancelable, booleanUiRelatedTask);
		climbingOthersOverlayWidget.refresh(bBox, cancelable, booleanUiRelatedTask);

		refreshLock.release();
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
}
