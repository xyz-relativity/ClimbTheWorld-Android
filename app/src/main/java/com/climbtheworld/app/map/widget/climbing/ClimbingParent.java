package com.climbtheworld.app.map.widget.climbing;

import android.content.Context;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What a climbing feature is part of, as its info dialog and the lists of climbing POIs name it:
 * the smallest named climbing relation listing it, so the next level up, like the crag of a route
 * rather than the area holding the crag. At the top, such as for an area, it is the countries the
 * feature was downloaded with instead. Loads off the UI thread.
 */
public final class ClimbingParent {
	private static final ClimbingParent NONE =
			new ClimbingParent(null, Collections.emptyList());

	/** The relation the feature is part of, null when it is in none. */
	public final OsmCollectionEntity relation;
	/**
	 * The ISO codes of the feature's countries when it is in no relation, more than one along a
	 * border.
	 */
	public final List<String> countries;

	ClimbingParent(OsmCollectionEntity relation, List<String> countries) {
		this.relation = relation;
		this.countries = countries;
	}

	/**
	 * @param location a point of the feature
	 */
	public static ClimbingParent load(Context context, OsmEntity.EntityOsmType osmType,
	                                  long osmId, GeoNode location) {
		DataManager dataManager = new DataManager();
		OsmCollectionEntity relation = closest(dataManager.loadParentRelations(context, osmType,
				osmId, location.decimalLatitude, location.decimalLongitude));
		if (relation != null) {
			return new ClimbingParent(relation, Collections.emptyList());
		}

		List<String> countries = new ArrayList<>(dataManager.loadCountries(context, osmType, osmId));
		if (countries.isEmpty() && location.countryIso != null && !location.countryIso.isEmpty()) {
			countries.add(location.countryIso);
		}
		return new ClimbingParent(null, countries);
	}

	/**
	 * For a POI as the map pins it: the route it stands for, see RouteStandIn, as the POI's info
	 * dialog shows that route.
	 */
	public static ClimbingParent load(Context context, GeoNode poi) {
		GeoNode feature = RouteStandIn.routeOf(poi);
		OsmEntity.EntityOsmType osmType;
		try {
			osmType = OsmEntity.EntityOsmType.valueOf(
					feature.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE, "node"));
		} catch (IllegalArgumentException e) {
			// Not an OSM entity type, so not a member of anything.
			return NONE;
		}
		return load(context, osmType, feature.osmID, feature);
	}

	/**
	 * What the POI, as the map pins it, is part of, see getLabel, for the lists of climbing POIs.
	 */
	public static String labelOf(Context context, GeoNode poi) {
		return load(context, poi).getLabel(context);
	}

	/**
	 * The relation's name, or its kind when it has none, else the countries' names, empty when it
	 * is in neither.
	 */
	public String getName(Context context) {
		if (relation != null) {
			return nameOf(context, relation);
		}
		return countryNames(countries);
	}

	/**
	 * A relation's name, or its kind when it has none.
	 */
	public static String nameOf(Context context, OsmCollectionEntity relation) {
		String name = relation.getTags().optString(ClimbingTags.KEY_NAME, "").trim();
		return !name.isEmpty() ? name
				: context.getString(relation.entityClimbingType.getNameId());
	}

	/**
	 * What the parent is and its name, like "Crag: Presles" or "Country: France", for the lists of
	 * climbing POIs: only what it is when it has no name, empty when the feature is in neither.
	 */
	public String getLabel(Context context) {
		if (relation != null) {
			String kind = context.getString(relation.entityClimbingType.getNameId());
			String name = relation.getTags().optString(ClimbingTags.KEY_NAME, "").trim();
			return name.isEmpty() ? kind
					: context.getString(R.string.climbing_parent_label, kind, name);
		}
		if (countries.isEmpty()) {
			return "";
		}
		return context.getString(R.string.climbing_parent_label,
				context.getString(R.string.country), countryNames(countries));
	}

	/**
	 * The countries in the user's language, or their ISO code when it has no name for one.
	 */
	public static String countryNames(List<String> countries) {
		StringBuilder names = new StringBuilder();
		for (String countryIso : countries) {
			String name = new Locale("", countryIso).getDisplayCountry();
			if (names.length() > 0) {
				names.append(" / ");
			}
			names.append(!name.isEmpty() ? name : countryIso);
		}
		return names.toString();
	}

	/**
	 * Named relations come first, then the smaller one, as it is the closest level up.
	 *
	 * @return null when there are none
	 */
	public static OsmCollectionEntity closest(List<OsmCollectionEntity> relations) {
		OsmCollectionEntity result = null;
		for (OsmCollectionEntity relation : relations) {
			if (result == null || isCloser(relation, result)) {
				result = relation;
			}
		}
		return result;
	}

	private static boolean isCloser(OsmCollectionEntity relation, OsmCollectionEntity other) {
		boolean named = !relation.getTags().optString(ClimbingTags.KEY_NAME, "").trim().isEmpty();
		boolean otherNamed = !other.getTags().optString(ClimbingTags.KEY_NAME, "").trim().isEmpty();
		if (named != otherNamed) {
			return named;
		}
		return boundingBoxSize(relation) < boundingBoxSize(other);
	}

	private static double boundingBoxSize(OsmCollectionEntity collection) {
		double width = collection.bBoxEast - collection.bBoxWest;
		if (width < 0) {
			// Across the antimeridian.
			width += 360;
		}
		return (collection.bBoxNorth - collection.bBoxSouth) * width;
	}
}
