package com.climbtheworld.app.storage.database;

import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import org.json.JSONObject;

public abstract class OsmEntity {
	@PrimaryKey
	public long osmID;

	public long updateDate;
	public int localUpdateState = ClimbingTags.CLEAN_STATE;

	//uses type converter
	@TypeConverters(DataConverter.class)
	public JSONObject jsonNodeInfo;

	protected void setJSONData(JSONObject pNodeInfo) {
		this.jsonNodeInfo = pNodeInfo;
	}

	public JSONObject getTags() {
		if (!jsonNodeInfo.has(ClimbingTags.KEY_TAGS)) {
			return new JSONObject();
		}
		return jsonNodeInfo.optJSONObject(ClimbingTags.KEY_TAGS);
	}
}
