package com.climbtheworld.app.storage.database;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.climbtheworld.app.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public abstract class OsmEntity {
	public enum EntityOsmType {
		node, way, relation
	}

	public enum EntityState {
		clean, toDelete, toUpdate
	}

	public enum EntityClimbingType {
		//individual route
		route(R.string.route, R.string.route_description, R.layout.icon_node_topo_display, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"climbing\":\"route_.*\".*).*"),
		//a crag will contain one or more routes
		crag(R.string.crag, R.string.crag_description, R.layout.icon_climbing_crag_display, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"climbing\":\"crag\".*).*"),
		//a site will contain one or more crags
		area(R.string.area, R.string.area_description, R.layout.icon_climbing_crag_display, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"climbing\":\"area\".*).*"),

		artificial(R.string.artificial, R.string.artificial_description, R.layout.icon_climbing_artificial_display, ".*(?=.*\"sport\":\"climbing\".*)(?=.*\"leisure\":\"sports_centre\".*).*"),
		others(R.string.unknown, R.string.unknown_description, R.layout.icon_node_topo_display, ".*(?=.*\"sport\":\"climbing\".*).*"),

		NAN();

		private final int stringTypeNameId;
		private final int stringTypeDescriptionId;
		private final int iconId;
		private final String regexFilter;

		EntityClimbingType() {
			this.regexFilter = "";
			this.stringTypeNameId = ResourcesCompat.ID_NULL;
			this.stringTypeDescriptionId = ResourcesCompat.ID_NULL;
			this.iconId = ResourcesCompat.ID_NULL;
		}

		EntityClimbingType(int pStringId, int pStringDescriptionId, int iconID, String regexFilter) {
			this.regexFilter = regexFilter;
			this.stringTypeNameId = pStringId;
			this.stringTypeDescriptionId = pStringDescriptionId;
			this.iconId = iconID;
		}

		public static EntityClimbingType getNodeTypeFromJson(JSONObject tags) {
			if (tags == null || tags.length() == 0) {
				return NAN;
			}
			String tagsString = tags.toString().trim();
			for (EntityClimbingType type : EntityClimbingType.values()) {
				if (tagsString.matches(type.regexFilter)) {
					return type;
				}
			}

			return EntityClimbingType.others;
		}

		@NotNull
		@Override
		public String toString() {
			throw new UnsupportedOperationException("Do not use toString. Use asString");
		}

		public String asString(AppCompatActivity parent) {
			return parent.getString(stringTypeNameId);
		}

		public int getNameId() {
			return stringTypeNameId;
		}

		public int getDescriptionId() {
			return stringTypeDescriptionId;
		}

		public int getIconId() {
			return iconId;
		}
	}

	@PrimaryKey
	public long osmID;

	public long updateDate;
	public EntityState localUpdateState = EntityState.clean;
	public EntityOsmType osmType;

	public EntityClimbingType entityClimbingType;

	//uses type converter
	@TypeConverters(DataConverter.class)
	public JSONObject jsonNodeInfo;

	public OsmEntity(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
		this.updateDate = System.currentTimeMillis();
		this.osmType = EntityOsmType.valueOf(this.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE));

	}

	protected void setJSONData(JSONObject pNodeInfo) {
		this.jsonNodeInfo = pNodeInfo;
		this.entityClimbingType = EntityClimbingType.getNodeTypeFromJson(getTags());
	}

	public JSONObject getTags() {
		if (!jsonNodeInfo.has(ClimbingTags.KEY_TAGS)) {
			return new JSONObject();
		}
		return jsonNodeInfo.optJSONObject(ClimbingTags.KEY_TAGS);
	}
}
