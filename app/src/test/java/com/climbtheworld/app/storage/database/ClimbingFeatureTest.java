package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** One test per way the OSM wiki's Climbing pages map a feature, with the shapes they leave out. */
@RunWith(RobolectricTestRunner.class)
public class ClimbingFeatureTest {
	@Test
	public void routeBottomsAndTopsAreNodes() throws JSONException {
		assertFeature(ClimbingFeature.route, node("{\"climbing\":\"route_bottom\"}"));
		assertFeature(ClimbingFeature.route, node("{\"climbing\":\"route_top\"}"));
		assertFeature(ClimbingFeature.none, way("{\"climbing\":\"route_bottom\"}"));
		assertFeature(ClimbingFeature.none, relation("{\"climbing\":\"route_top\"}"));
	}

	@Test
	public void routesAreNodesWaysOrMultiPitchRelations() throws JSONException {
		assertFeature(ClimbingFeature.route, node("{\"climbing\":\"route\"}"));
		assertFeature(ClimbingFeature.route, way("{\"climbing\":\"route\"}"));
		assertFeature(ClimbingFeature.route, relation("{\"climbing\":\"route\"}"));
	}

	@Test
	public void otherRouteLikeValuesAreNotRoutes() throws JSONException {
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"climbing\":\"route_start\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"climbing\":\"routes\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"climbing\":\"Route\"}"));
	}

	@Test
	public void gradedBouldersAreRoutesOnNodesAndAreas() throws JSONException {
		assertFeature(ClimbingFeature.route,
				node("{\"climbing\":\"boulder\",\"climbing:grade:font\":\"6A\"}"));
		assertFeature(ClimbingFeature.route, way("{\"climbing\":\"boulder\",\"natural\":\"stone\","
				+ "\"climbing:grade:uiaa:max\":\"7\"}"));
		assertFeature(ClimbingFeature.none,
				relation("{\"climbing\":\"boulder\",\"climbing:grade:font\":\"6A\"}"));
		assertFeature(ClimbingFeature.none,
				node("{\"sport\":\"climbing\",\"climbing\":\"boulder\",\"natural\":\"stone\"}"));
	}

	@Test
	public void cragsAreNodesOrSiteRelations() throws JSONException {
		assertFeature(ClimbingFeature.crag, node("{\"climbing\":\"crag\"}"));
		assertFeature(ClimbingFeature.crag,
				relation("{\"type\":\"site\",\"site\":\"climbing\",\"climbing\":\"crag\"}"));
		assertFeature(ClimbingFeature.none,
				relation("{\"type\":\"multipolygon\",\"climbing\":\"crag\"}"));
		assertFeature(ClimbingFeature.none, relation("{\"climbing\":\"crag\"}"));
		assertFeature(ClimbingFeature.none,
				way("{\"sport\":\"climbing\",\"climbing\":\"crag\",\"natural\":\"cliff\"}"));
	}

	@Test
	public void areasAreSiteRelations() throws JSONException {
		assertFeature(ClimbingFeature.area, relation("{\"type\":\"site\",\"climbing\":\"area\"}"));
		assertFeature(ClimbingFeature.none, relation("{\"type\":\"multipolygon\",\"climbing\":\"area\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"climbing\":\"area\"}"));
		assertFeature(ClimbingFeature.none, way("{\"sport\":\"climbing\",\"climbing\":\"area\"}"));
	}

	@Test
	public void aClimbingValueCountsWhateverTheSport() throws JSONException {
		assertFeature(ClimbingFeature.route,
				node("{\"sport\":\"climbing\",\"climbing\":\"route_bottom\"}"));
		assertFeature(ClimbingFeature.route,
				node("{\"sport\":\"climbing; hiking\",\"climbing\":\"route_bottom\"}"));
		assertFeature(ClimbingFeature.crag,
				node("{\"sport\":\"ice_climbing\",\"climbing\":\"crag\"}"));
		assertFeature(ClimbingFeature.area, relation("{\"type\":\"site\",\"climbing\":\"area\","
				+ "\"name\":\"x\"}"));
	}

	@Test
	public void gymsAndWallsAreNodesOrAreasWithTheClimbingSport() throws JSONException {
		for (String leisure : new String[]{"sports_centre", "sports_hall", "pitch"}) {
			String tags = "{\"sport\":\"climbing\",\"leisure\":\"" + leisure + "\"}";
			assertFeature(ClimbingFeature.artificial, node(tags));
			assertFeature(ClimbingFeature.artificial, way(tags));
			assertFeature(ClimbingFeature.artificial, relation(tags));
		}
		assertFeature(ClimbingFeature.artificial,
				node("{\"sport\":\"hiking;climbing\",\"leisure\":\"sports_centre\"}"));
		assertFeature(ClimbingFeature.none, node("{\"leisure\":\"sports_centre\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"soccer\",\"leisure\":\"pitch\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"leisure\":\"park\"}"));
	}

	@Test
	public void towersAreClimbingTowersWithTheClimbingSport() throws JSONException {
		assertFeature(ClimbingFeature.artificial, node("{\"sport\":\"climbing\",\"leisure\":\"pitch\","
				+ "\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));
		assertFeature(ClimbingFeature.artificial, way("{\"sport\":\"climbing\",\"man_made\":\"tower\","
				+ "\"tower:type\":\"climbing\"}"));
		assertFeature(ClimbingFeature.none, node("{\"man_made\":\"tower\",\"tower:type\":\"climbing\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"man_made\":\"tower\","
				+ "\"tower:type\":\"communication\"}"));
	}

	@Test
	public void climbingHallsAreBuildingAreas() throws JSONException {
		assertFeature(ClimbingFeature.artificial, way("{\"sport\":\"climbing\",\"building\":\"yes\"}"));
		assertFeature(ClimbingFeature.artificial, relation("{\"type\":\"multipolygon\","
				+ "\"sport\":\"climbing\",\"building\":\"sports_hall\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"building\":\"yes\"}"));
		assertFeature(ClimbingFeature.none, way("{\"sport\":\"climbing\",\"building\":\"no\"}"));
		assertFeature(ClimbingFeature.none, way("{\"building\":\"yes\"}"));
	}

	@Test
	public void climbingOnUnmappedOrDeprecatedShapesIsNone() throws JSONException {
		// Cliffs with sport=climbing are to be deprecated in favour of crags.
		assertFeature(ClimbingFeature.none, way("{\"sport\":\"climbing\",\"natural\":\"cliff\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"natural\":\"peak\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"natural\":\"stone\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"climbing\":\"yes\"}"));
		assertFeature(ClimbingFeature.none, node("{\"sport\":\"climbing\",\"shop\":\"sports\"}"));
		assertFeature(ClimbingFeature.none, way("{\"sport\":\"climbing\",\"highway\":\"path\"}"));
		assertFeature(ClimbingFeature.none, node("{\"climbing\":\"bolt\"}"));
		assertFeature(ClimbingFeature.none, node("{}"));
	}

	@Test
	public void theClimbingValueComesBeforeAFacility() throws JSONException {
		assertFeature(ClimbingFeature.route, node("{\"sport\":\"climbing\",\"climbing\":\"route_bottom\","
				+ "\"leisure\":\"pitch\"}"));
		assertFeature(ClimbingFeature.artificial, node("{\"sport\":\"climbing\",\"climbing\":\"yes\","
				+ "\"leisure\":\"sports_centre\"}"));
	}

	@Test
	public void anElementWithoutATypeIsANode() throws JSONException {
		assertFeature(ClimbingFeature.crag, new JSONObject("{\"tags\":{\"climbing\":\"crag\"}}"));
		assertFeature(ClimbingFeature.none, new JSONObject("{\"tags\":{\"climbing\":\"area\"}}"));
		assertFeature(ClimbingFeature.none, new JSONObject());
	}

	private static void assertFeature(ClimbingFeature expected, JSONObject element) {
		assertEquals(element.toString(), expected, ClimbingFeature.of(element));
	}

	private static JSONObject node(String tags) throws JSONException {
		return new JSONObject("{\"type\":\"node\",\"id\":1,\"lat\":45.1,\"lon\":-73.2,\"tags\":"
				+ tags + "}");
	}

	private static JSONObject way(String tags) throws JSONException {
		return new JSONObject("{\"type\":\"way\",\"id\":2,\"nodes\":[1,3,1],\"tags\":" + tags + "}");
	}

	private static JSONObject relation(String tags) throws JSONException {
		return new JSONObject("{\"type\":\"relation\",\"id\":3,"
				+ "\"members\":[{\"type\":\"node\",\"ref\":1,\"role\":\"\"}],\"tags\":" + tags + "}");
	}
}
