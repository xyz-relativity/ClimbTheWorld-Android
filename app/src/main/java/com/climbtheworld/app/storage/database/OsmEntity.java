package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.room.TypeConverters;

import com.climbtheworld.app.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public abstract class OsmEntity {
	/** Negative for an entity created in the app that is not on OSM yet. */
	public long osmID;
	/** When the entity was last changed in the app, 0 if never. */
	public long localEditDate;
	public EntityState localUpdateState = EntityState.clean;
	@NonNull
	public EntityOsmType osmType;
	public EntityClimbingType entityClimbingType;
	//uses type converter
	@TypeConverters(DataConverter.class)
	public JSONObject jsonNodeInfo;
	/**
	 * The downloaded version the local changes started from, kept to revert them and to merge
	 * them with the server's version on upload. Null without local changes and for new entities.
	 */
	@Nullable
	@TypeConverters(DataConverter.class)
	public JSONObject baseJson;

	public OsmEntity(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
		this.osmType = EntityOsmType.valueOf(this.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE));

	}

	/** @return whether the entity was created in the app and is not on OSM yet. */
	public boolean isNew() {
		return osmID < 0;
	}

	protected void setJSONData(JSONObject pNodeInfo) {
		this.jsonNodeInfo = pNodeInfo;
		refreshClimbingType();
	}

	/** Brings the climbing type column back in line with the tags after they changed. */
	public void refreshClimbingType() {
		this.entityClimbingType = EntityClimbingType.getNodeTypeFromJson(getTags());
	}

	public JSONObject getTags() {
		if (!jsonNodeInfo.has(ClimbingTags.KEY_TAGS)) {
			return new JSONObject();
		}
		return jsonNodeInfo.optJSONObject(ClimbingTags.KEY_TAGS);
	}

	public enum EntityOsmType {
		node, way, relation
	}

	public enum EntityState {
		clean, toDelete, toUpdate
	}

	public enum EntityClimbingType {
		//individual route
		route(R.string.route, R.string.route_description, R.layout.icon_node_topo_display,
				"(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ")" + ClimbingTags.ROUTE_PATTERN + ".*"),
		//a crag will contain one or more routes
		crag(R.string.crag, R.string.crag_description, R.layout.icon_climbing_area_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*\"climbing\":\"crag\".*).*"),
		//a site will contain one or more crags
		area(R.string.area, R.string.area_description, R.layout.icon_climbing_area_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*\"climbing\":\"area\".*).*"),

		artificial(R.string.artificial, R.string.artificial_description,
				R.layout.icon_climbing_artificial_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*)(?=.*" + ClimbingTags.ARTIFICIAL_PATTERN + ".*).*"),
		others(R.string.unknown, R.string.unknown_description, R.layout.icon_node_topo_display,
				".*(?=.*" + ClimbingTags.SPORT_CLIMBING_PATTERN + ".*).*"),

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

		EntityClimbingType(int pStringId, int pStringDescriptionId, int iconID,
		                   String regexFilter) {
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
}
