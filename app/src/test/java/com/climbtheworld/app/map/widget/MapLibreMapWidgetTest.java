package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapZoomLevels;
import com.climbtheworld.app.storage.database.GeoNode;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MapLibreMapWidgetTest {
	@Test
	public void artificialPoiIsVisibleAtAreaZoom() throws Exception {
		GeoNode gym = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("leisure", "sports_centre"));
		GeoNode buildingGym = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("building", "yes"));
		GeoNode area = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "area"));

		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(gym, MapZoomLevels.AREA_MIN));
		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(buildingGym, MapZoomLevels.AREA_MIN));
		assertFalse(MapLibreMapWidget.isPoiVisibleAtZoom(area, MapZoomLevels.AREA_MIN));
		assertFalse(MapLibreMapWidget.isPoiVisibleAtZoom(gym,
				MapZoomLevels.AREA_MIN - 0.01));
	}

	@Test
	public void allPoisAreVisibleAtTheRegularPoiZoom() throws Exception {
		GeoNode area = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "area"));

		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(area,
				MapZoomLevels.POI_AND_ROUTE_MIN));
	}

	@Test
	public void headingAccuracyWedgeRoundsUpToWholeSteps() {
		assertEquals(5, MapLibreMapWidget.headingAccuracyWedgeDegrees(0.5));
		assertEquals(5, MapLibreMapWidget.headingAccuracyWedgeDegrees(5));
		assertEquals(10, MapLibreMapWidget.headingAccuracyWedgeDegrees(5.1));
		// Beyond half a turn, every way is as likely: a full circle.
		assertEquals(180, MapLibreMapWidget.headingAccuracyWedgeDegrees(400));
	}

	@Test
	public void noHeadingAccuracyWedgeWithoutAnAccuracy() {
		assertEquals(0, MapLibreMapWidget.headingAccuracyWedgeDegrees(Double.NaN));
		assertEquals(0, MapLibreMapWidget.headingAccuracyWedgeDegrees(0));
	}

	@Test
	public void theSamePoiKeepsItsPin() throws Exception {
		assertEquals(poiIconKey("route_bottom"), poiIconKey("route_bottom"));
	}

	@Test
	public void aRouteTopGetsItsOwnPin() throws Exception {
		// The same node once edited into the top of its route, which has the shorter pin.
		assertNotEquals(poiIconKey("route_bottom"), poiIconKey("route_top"));
	}

	private String poiIconKey(String climbing) throws Exception {
		return MapLibreMapWidget.poiIconKey(new DisplayableGeoNode(nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", climbing))));
	}

	private GeoNode nodeWithTags(JSONObject tags) throws Exception {
		return new GeoNode(new JSONObject()
				.put("id", 1L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", tags));
	}
}
