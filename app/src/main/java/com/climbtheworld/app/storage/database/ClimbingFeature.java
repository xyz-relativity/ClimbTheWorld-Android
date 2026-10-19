package com.climbtheworld.app.storage.database;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * What an OSM element is as a climbing feature, decided from its tags and element type as the OSM
 * wiki maps them: https://wiki.openstreetmap.org/wiki/Climbing and its Tag:sport=climbing and
 * Climbing/Gyms pages. Anything mapped some other way is {@link #none}.
 * <p>
 * The wiki asks data consumers to take either climbing=* or sport=climbing as the root tag, as
 * one of them is often missing, so a climbing value counts without sport=climbing. Facilities are
 * only climbing ones with sport=climbing.
 */
public enum ClimbingFeature {
	route, crag, area, artificial, none;

	/** @param element an element as Overpass returns it; without a type it is a node. */
	public static ClimbingFeature of(JSONObject element) {
		OsmEntity.EntityOsmType elementType;
		try {
			elementType = OsmEntity.EntityOsmType.valueOf(element.optString(ClimbingTags.KEY_TYPE));
		} catch (IllegalArgumentException ignored) {
			// Only stand-ins built in the app have no type, and they are pins.
			elementType = OsmEntity.EntityOsmType.node;
		}
		return of(elementType, element.optJSONObject(ClimbingTags.KEY_TAGS));
	}

	public static ClimbingFeature of(OsmEntity.EntityOsmType elementType, JSONObject tags) {
		if (tags == null) {
			return none;
		}
		ClimbingFeature feature = fromClimbingValue(elementType, tags);
		if (feature != none) {
			return feature;
		}
		return isClimbingFacility(elementType, tags) ? artificial : none;
	}

	private static ClimbingFeature fromClimbingValue(OsmEntity.EntityOsmType elementType,
	                                                 JSONObject tags) {
		switch (tags.optString(ClimbingTags.KEY_CLIMBING)) {
			case ClimbingTags.VALUE_ROUTE_BOTTOM:
			case ClimbingTags.VALUE_ROUTE_TOP:
				return elementType == OsmEntity.EntityOsmType.node ? route : none;
			case ClimbingTags.VALUE_ROUTE:
				// A node or way, or the relation of a multi-pitch route.
				return route;
			case ClimbingTags.VALUE_BOULDER:
				// A node or an area. The wiki has it as a free-standing rock with a single problem;
				// one without a grade is mostly a rock whose problems are mapped apart.
				return elementType != OsmEntity.EntityOsmType.relation && hasGrade(tags)
						? route : none;
			case ClimbingTags.VALUE_CRAG:
				return elementType == OsmEntity.EntityOsmType.node || isSite(elementType, tags)
						? crag : none;
			case ClimbingTags.VALUE_AREA:
				return isSite(elementType, tags) ? area : none;
			default:
				return none;
		}
	}

	/**
	 * A gym (leisure=sports_centre or sports_hall), a wall (leisure=pitch) or a tower
	 * (tower:type=climbing) as a node or an area, or a climbing hall mapped as a building area.
	 */
	private static boolean isClimbingFacility(OsmEntity.EntityOsmType elementType,
	                                          JSONObject tags) {
		if (!ClimbingTags.hasClimbingSport(tags.optString(ClimbingTags.KEY_SPORT, null))) {
			return false;
		}
		if (ClimbingTags.isClimbingFacility(tags.optString(ClimbingTags.KEY_LEISURE))
				|| ClimbingTags.VALUE_CLIMBING.equals(tags.optString(ClimbingTags.KEY_TOWER_TYPE))) {
			return true;
		}
		String building = tags.optString(ClimbingTags.KEY_BUILDING);
		return elementType != OsmEntity.EntityOsmType.node && !building.isEmpty()
				&& !ClimbingTags.VALUE_NO.equals(building);
	}

	private static boolean isSite(OsmEntity.EntityOsmType elementType, JSONObject tags) {
		return elementType == OsmEntity.EntityOsmType.relation
				&& ClimbingTags.VALUE_SITE.equals(tags.optString(ClimbingTags.KEY_TYPE));
	}

	private static boolean hasGrade(JSONObject tags) {
		for (Iterator<String> keys = tags.keys(); keys.hasNext(); ) {
			if (keys.next().startsWith(ClimbingTags.KEY_GRADE_PREFIX)) {
				return true;
			}
		}
		return false;
	}
}
