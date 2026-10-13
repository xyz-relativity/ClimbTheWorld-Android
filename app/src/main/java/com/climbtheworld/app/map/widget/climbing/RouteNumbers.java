package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up the numbers of routes in their crags, see {@link ClimbingRouteCounter#numberRoutes},
 * which the map and augmented reality route pins show in front of the name. Must run off the UI
 * thread.
 */
public final class RouteNumbers {
	private RouteNumbers() {
		//hide constructor
	}

	/**
	 * @return the numbers of the routes of the crags in the bounds, keyed by route node ID.
	 */
	public static Map<Long, Integer> load(Context context, MapBounds bounds) {
		DataManager dataManager = new DataManager();
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(context);
		Map<Long, Integer> result = new HashMap<>();
		List<Long> ids = dataManager.loadCollectionBBox(context, bounds,
				OsmEntity.EntityClimbingType.crag);
		for (OsmCollectionEntity crag : dataManager.loadCollectionData(context, ids).values()) {
			result.putAll(routeCounter.numberRoutes(crag));
		}
		return result;
	}
}
