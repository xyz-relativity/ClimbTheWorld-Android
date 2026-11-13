package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.Index;

import org.json.JSONException;
import org.json.JSONObject;

// The index serves the bounding box query (OsmNodeDao.loadBBox) on its own: a seek on the type
// and the latitude, the rest read from the index without the table. One index on the latitude
// and one on the longitude, as before, had SQLite walk a whole band of a wide view.
// The name index serves the search by name (OsmNodeDao.find): walked in the order of the names,
// it tells which rows match without reading them, and stops at the limit.
@Entity(primaryKeys = {"osmType", "osmID"}, indices = {
		@Index(name = "index_OsmNode_box", value = {"entityClimbingType", "decimalLatitude",
				"decimalLongitude", "localUpdateState", "osmID"}),
		@Index(name = "index_OsmNode_name", value = {"name"})
})
public class OsmNode extends OsmEntity {
	public Double decimalLatitude = 0.0;
	public Double decimalLongitude = 0.0;
	public Double elevationMeters = 0.0;
	public String countryIso;

	public OsmNode(String stringNodeInfo) throws JSONException {
		this(new JSONObject(stringNodeInfo));
	}

	public OsmNode(JSONObject jsonNodeInfo) {
		super(jsonNodeInfo);

		this.updatePOILocation(Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LAT, "0")),
				Double.parseDouble(this.jsonNodeInfo.optString(ClimbingTags.KEY_LON, "0")),
				Double.parseDouble(getTags().optString(ClimbingTags.KEY_ELEVATION, "0").replaceAll("[^\\d.]", "")));
	}

	public void updatePOILocation(double pDecimalLatitude, double pDecimalLongitude, double pMetersAltitude) {
		this.decimalLongitude = pDecimalLongitude;
		this.decimalLatitude = pDecimalLatitude;
		this.elevationMeters = pMetersAltitude;
		putLocation(jsonNodeInfo, decimalLatitude, decimalLongitude);
	}

	/** Writes the position into the element, as its lat and lon. */
	static void putLocation(JSONObject jsonNodeInfo, double latitude, double longitude) {
		try {
			jsonNodeInfo.put(ClimbingTags.KEY_LAT, latitude);
			jsonNodeInfo.put(ClimbingTags.KEY_LON, longitude);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
}
