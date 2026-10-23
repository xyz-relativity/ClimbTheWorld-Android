package com.climbtheworld.app.map.widget.climbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;

import com.climbtheworld.app.storage.database.OsmCollectionEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
public class ClimbingParentTest {
	private final Context context = RuntimeEnvironment.getApplication();
	private Locale defaultLocale;

	@Before
	public void setUp() {
		defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.ENGLISH);
	}

	@After
	public void tearDown() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	public void theSmallerRelationIsTheCloserLevelUp() throws JSONException {
		OsmCollectionEntity area = relation(1, "area", "Vercors", 45.0, 6.0, 44.0, 5.0);
		OsmCollectionEntity crag = relation(2, "crag", "Presles", 45.01, 5.51, 45.0, 5.5);

		assertSame(crag, ClimbingParent.closest(Arrays.asList(area, crag)));
		assertSame(crag, ClimbingParent.closest(Arrays.asList(crag, area)));
	}

	@Test
	public void aNamedRelationComesBeforeASmallerUnnamedOne() throws JSONException {
		OsmCollectionEntity area = relation(1, "area", "Vercors", 45.0, 6.0, 44.0, 5.0);
		OsmCollectionEntity unnamedCrag = relation(2, "crag", "", 45.01, 5.51, 45.0, 5.5);

		assertSame(area, ClimbingParent.closest(Arrays.asList(unnamedCrag, area)));
	}

	@Test
	public void aBoxAcrossTheAntimeridianIsMeasuredTheShortWayRound() throws JSONException {
		// West of the antimeridian to east of it: two degrees wide, not 358.
		OsmCollectionEntity across = relation(1, "crag", "Across", 1.0, -179.0, 0.0, 179.0);
		OsmCollectionEntity wide = relation(2, "crag", "Wide", 1.0, 10.0, 0.0, 0.0);

		assertSame(across, ClimbingParent.closest(Arrays.asList(wide, across)));
	}

	@Test
	public void noRelationIsNoParent() {
		assertNull(ClimbingParent.closest(Collections.emptyList()));
	}

	@Test
	public void countriesAreNamedInTheUsersLanguage() {
		assertEquals("France", ClimbingParent.countryNames(Collections.singletonList("FR")));
		// Along a border.
		assertEquals("France / Switzerland",
				ClimbingParent.countryNames(Arrays.asList("FR", "CH")));
		assertEquals("", ClimbingParent.countryNames(Collections.emptyList()));
	}

	@Test
	public void theListsLabelSaysWhatTheParentIs() throws JSONException {
		assertEquals("Crag: Presles", label(relation(2, "crag", "Presles", 1, 1, 0, 0)));
		assertEquals("Area: Vercors", label(relation(1, "area", "Vercors", 1, 1, 0, 0)));
		// Its kind says enough without a name.
		assertEquals("Crag", label(relation(3, "crag", "", 1, 1, 0, 0)));
	}

	@Test
	public void theListsLabelOfATopLevelFeatureIsItsCountry() {
		assertEquals("Country: France / Switzerland",
				new ClimbingParent(null, Arrays.asList("FR", "CH")).getLabel(context));
		assertEquals("", new ClimbingParent(null, Collections.emptyList()).getLabel(context));
	}

	private String label(OsmCollectionEntity relation) {
		return new ClimbingParent(relation, Collections.emptyList()).getLabel(context);
	}

	private static OsmCollectionEntity relation(long id, String climbing, String name,
	                                            double north, double east, double south,
	                                            double west) throws JSONException {
		JSONObject tags = new JSONObject()
				.put("type", "site")
				.put("site", "climbing")
				.put("sport", "climbing")
				.put("climbing", climbing);
		if (!name.isEmpty()) {
			tags.put("name", name);
		}
		OsmCollectionEntity relation = new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "relation")
				.put("tags", tags)
				.put("members", new JSONArray()));
		relation.bBoxNorth = north;
		relation.bBoxEast = east;
		relation.bBoxSouth = south;
		relation.bBoxWest = west;
		return relation;
	}
}
