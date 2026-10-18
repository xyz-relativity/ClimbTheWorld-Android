package com.climbtheworld.app.map.widget.climbing;

import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONObject;

/**
 * A pin that stands for a route whose details are elsewhere: the top of a route mapped as a way,
 * see {@link RouteWay.Top}, or the start of a route relation, see {@link RouteRelation.Start}.
 * A tap on it opens the route, and the display filters treat it as the route.
 */
public abstract class RouteStandIn extends GeoNode {
	// The route, which holds its details: the bottom of a route way, or the relation.
	public final GeoNode route;

	RouteStandIn(JSONObject jsonNodeInfo, GeoNode route) {
		super(jsonNodeInfo);
		this.route = route;
	}

	/**
	 * @return the route the POI stands for, or the POI itself when it stands for none
	 */
	public static GeoNode routeOf(GeoNode poi) {
		return poi instanceof RouteStandIn ? ((RouteStandIn) poi).route : poi;
	}
}
