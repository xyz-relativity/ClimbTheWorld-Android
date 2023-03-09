package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.Index;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

@Entity(indices = {@Index(value = "decimalLatitude"), @Index(value = "decimalLongitude")})
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

		try {
			jsonNodeInfo.put(ClimbingTags.KEY_LAT, this.decimalLatitude);
			jsonNodeInfo.put(ClimbingTags.KEY_LON, this.decimalLongitude);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public GeoPoint toGeoPoint() {
		return new GeoPoint(decimalLatitude, decimalLongitude, elevationMeters);
	}
}
