package com.climbtheworld.app.storage.logbook;

import android.content.Context;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Works out the groups a log book entry is listed under, from the downloaded OSM data: its
 * country, its area and its crag, each left out when it has none. A crag or area entry is in its
 * own group. Loads off the UI thread; keeps what it loaded, so use one per reading of the log
 * book.
 */
public class LogBookPlaces {
	// Enough for a pitch in a route relation in a crag in an area, without looping forever on
	// relations listing each other.
	private static final int MAX_LEVELS = 4;
	// Between the ISO codes of a country group's key, more than one along a border.
	private static final String COUNTRY_SEPARATOR = "|";

	private final Context context;
	private final DataManager dataManager = new DataManager();
	private final Map<String, List<OsmCollectionEntity>> parentCache = new HashMap<>();
	private final Map<String, List<String>> countryCache = new HashMap<>();

	public LogBookPlaces(Context context) {
		this.context = context;
	}

	/**
	 * For an entry whose element is not in the downloaded data.
	 */
	public List<LogBookGroup> notDownloaded() {
		return Collections.singletonList(new LogBookGroup(LogBookGroup.Kind.notDownloaded,
				LogBookGroup.Kind.notDownloaded.name(),
				context.getString(R.string.log_book_group_not_downloaded)));
	}

	/**
	 * @param element the entry's element, from the downloaded data
	 */
	public List<LogBookGroup> of(LogBookEntry entry, GeoNode element) {
		Place self = new Place(entry.osmType, entry.osmID, element.decimalLatitude,
				element.decimalLongitude);
		LogBookGroup crag = null;
		LogBookGroup area = null;
		if (element.getNodeType() == OsmEntity.EntityClimbingType.crag) {
			crag = group(LogBookGroup.Kind.crag, self, element.getName());
		} else if (element.getNodeType() == OsmEntity.EntityClimbingType.area) {
			area = group(LogBookGroup.Kind.area, self, element.getName());
		}

		// The countries are those of the element, else of the first of its parents having any.
		List<Place> countrySources = new ArrayList<>();
		countrySources.add(self);
		// An area listing the element along with its crag, for when the crag is in no area.
		LogBookGroup otherArea = null;
		Set<String> visited = new HashSet<>();
		visited.add(self.key());
		Place current = self;
		for (int level = 0; level < MAX_LEVELS && area == null && current != null; level++) {
			List<OsmCollectionEntity> parents = new ArrayList<>();
			for (OsmCollectionEntity parent : parentsOf(current)) {
				if (visited.add(DataManager.collectionKey(parent.osmType, parent.osmID))) {
					parents.add(parent);
				}
			}
			OsmCollectionEntity parentCrag =
					ClimbingParent.closest(ofType(parents, OsmEntity.EntityClimbingType.crag));
			OsmCollectionEntity parentArea =
					ClimbingParent.closest(ofType(parents, OsmEntity.EntityClimbingType.area));
			if (crag == null && parentCrag != null) {
				current = Place.of(parentCrag);
				crag = group(LogBookGroup.Kind.crag, current,
						ClimbingParent.nameOf(context, parentCrag));
				if (parentArea != null) {
					otherArea = group(LogBookGroup.Kind.area, Place.of(parentArea),
							ClimbingParent.nameOf(context, parentArea));
				}
			} else if (parentArea != null) {
				current = Place.of(parentArea);
				area = group(LogBookGroup.Kind.area, current,
						ClimbingParent.nameOf(context, parentArea));
			} else {
				// Such as the route relation of a pitch: its crag is one level further up.
				OsmCollectionEntity parent = ClimbingParent.closest(parents);
				current = parent != null ? Place.of(parent) : null;
			}
			if (current != null) {
				countrySources.add(current);
			}
		}
		if (area == null) {
			area = otherArea;
		}

		List<LogBookGroup> result = new ArrayList<>();
		result.add(countryGroup(countrySources, element));
		if (area != null) {
			result.add(area);
		}
		if (crag != null) {
			result.add(crag);
		}
		return result;
	}

	private LogBookGroup countryGroup(List<Place> sources, GeoNode element) {
		List<String> countries = Collections.emptyList();
		for (Place source : sources) {
			countries = countryCache.computeIfAbsent(source.key(),
					key -> dataManager.loadCountries(context, source.osmType, source.osmId));
			if (!countries.isEmpty()) {
				break;
			}
		}
		if (countries.isEmpty() && element.countryIso != null && !element.countryIso.isEmpty()) {
			countries = Collections.singletonList(element.countryIso.toUpperCase());
		}

		if (countries.isEmpty()) {
			return new LogBookGroup(LogBookGroup.Kind.unknownCountry,
					LogBookGroup.Kind.unknownCountry.name(),
					context.getString(R.string.log_book_group_unknown_country));
		}
		return new LogBookGroup(LogBookGroup.Kind.country,
				String.join(COUNTRY_SEPARATOR, countries), ClimbingParent.countryNames(countries));
	}

	/**
	 * @return the ISO code of the country group's first country, the one whose flag it shows
	 */
	public static String flagCountryOf(LogBookGroup country) {
		return country.key().split(Pattern.quote(COUNTRY_SEPARATOR))[0];
	}

	private List<OsmCollectionEntity> parentsOf(Place place) {
		return parentCache.computeIfAbsent(place.key(),
				key -> dataManager.loadParentRelations(context, place.osmType, place.osmId,
						place.latitude, place.longitude));
	}

	private LogBookGroup group(LogBookGroup.Kind kind, Place place, String name) {
		return new LogBookGroup(kind, place.key(),
				!name.isEmpty() ? name : context.getString(kind == LogBookGroup.Kind.crag
						? R.string.crag : R.string.area));
	}

	private static List<OsmCollectionEntity> ofType(List<OsmCollectionEntity> collections,
	                                                OsmEntity.EntityClimbingType type) {
		List<OsmCollectionEntity> result = new ArrayList<>();
		for (OsmCollectionEntity collection : collections) {
			if (collection.entityClimbingType == type) {
				result.add(collection);
			}
		}
		return result;
	}

	/**
	 * An element and a point of it, where its parents' bounding boxes are looked for.
	 */
	private record Place(OsmEntity.EntityOsmType osmType, long osmId, double latitude,
	                     double longitude) {
		/**
		 * A relation's bounding box lies within its parents', so its middle is a point of it
		 * for that lookup.
		 */
		private static Place of(OsmCollectionEntity collection) {
			double east = collection.bBoxEast;
			if (east < collection.bBoxWest) {
				// Across the antimeridian.
				east += 360;
			}
			double longitude = (collection.bBoxWest + east) / 2;
			return new Place(collection.osmType, collection.osmID,
					(collection.bBoxNorth + collection.bBoxSouth) / 2,
					longitude > 180 ? longitude - 360 : longitude);
		}

		private String key() {
			return DataManager.collectionKey(osmType, osmId);
		}
	}
}
