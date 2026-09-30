package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
		GeoNode area = nodeWithTags(new JSONObject()
				.put("sport", "climbing")
				.put("climbing", "area"));

		assertTrue(MapLibreMapWidget.isPoiVisibleAtZoom(gym, MapZoomLevels.AREA_MIN));
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

	private GeoNode nodeWithTags(JSONObject tags) throws Exception {
		return new GeoNode(new JSONObject()
				.put("id", 1L)
				.put("type", "node")
				.put("lat", 45)
				.put("lon", 24)
				.put("tags", tags));
	}
}
