package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.room.TypeConverters;

import com.climbtheworld.app.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
		this.entityClimbingType = EntityClimbingType.fromElement(jsonNodeInfo);
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
		route(R.string.route, R.string.route_description, R.layout.icon_node_topo_display),
		//a crag will contain one or more routes
		crag(R.string.crag, R.string.crag_description, R.layout.icon_climbing_crag_display),
		//a site will contain one or more crags
		area(R.string.area, R.string.area_description, R.layout.icon_climbing_area_display),

		artificial(R.string.artificial, R.string.artificial_description,
				R.layout.icon_climbing_artificial_display),
		/**
		 * An element with tags that is no {@link ClimbingFeature}. Only one changed in the app,
		 * or a log book entry without a type, can be a pin of this type, so it is not offered as
		 * a choice.
		 */
		others(R.string.unknown, R.string.unknown_description, R.layout.icon_node_topo_display),
		/** An element without tags, like the nodes of a way, which is never a pin. */
		NAN();

		private final int stringTypeNameId;
		private final int stringTypeDescriptionId;
		private final int iconId;

		EntityClimbingType() {
			this.stringTypeNameId = ResourcesCompat.ID_NULL;
			this.stringTypeDescriptionId = ResourcesCompat.ID_NULL;
			this.iconId = ResourcesCompat.ID_NULL;
		}

		EntityClimbingType(int pStringId, int pStringDescriptionId, int iconID) {
			this.stringTypeNameId = pStringId;
			this.stringTypeDescriptionId = pStringDescriptionId;
			this.iconId = iconID;
		}

		/**
		 * The types of a {@link ClimbingFeature}: all but {@link #others} and {@link #NAN}, which
		 * a user can pick between.
		 */
		public static EntityClimbingType[] features() {
			return new EntityClimbingType[]{route, crag, area, artificial};
		}

		/**
		 * @return the {@link ClimbingFeature} of the element, {@link #others} for tags that make
		 * none, and {@link #NAN} without tags
		 */
		public static EntityClimbingType fromElement(JSONObject element) {
			JSONObject tags = element.optJSONObject(ClimbingTags.KEY_TAGS);
			if (tags == null || tags.length() == 0) {
				return NAN;
			}
			switch (ClimbingFeature.of(element)) {
				case route:
					return route;
				case crag:
					return crag;
				case area:
					return area;
				case artificial:
					return artificial;
				default:
					return others;
			}
		}

		/**
		 * @return the type of a pin standing for the element, which may have no tags yet:
		 * {@link #others} rather than {@link #NAN} for an element that is no feature
		 */
		public static EntityClimbingType ofPin(JSONObject element) {
			EntityClimbingType type = fromElement(element);
			return type == NAN ? others : type;
		}

		/** The types a pin can have: the features, and {@link #others}. */
		public static EntityClimbingType[] pinTypes() {
			return new EntityClimbingType[]{route, crag, area, artificial, others};
		}

		/**
		 * The types the node editor offers a node: a route or artificial climbing. Crags and areas
		 * are site relations, as the OSM wiki's Climbing page has them, so a node keeps a crag or
		 * {@link #others} type only when it already has it. No node is an area.
		 */
		public static EntityClimbingType[] nodeEditorValues(EntityClimbingType current) {
			List<EntityClimbingType> result = new ArrayList<>();
			for (EntityClimbingType type : pinTypes()) {
				if (type == route || type == artificial || type == current) {
					result.add(type);
				}
			}
			return result.toArray(new EntityClimbingType[0]);
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
