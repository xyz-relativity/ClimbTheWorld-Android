package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.TypeConverters;

import com.climbtheworld.app.utils.GeoUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

@Entity(primaryKeys = {"osmType", "osmID"}, indices = {
		@Index(value = "bBoxNorth"),
		@Index(value = "bBoxEast"),
		@Index(value = "bBoxSouth"),
		@Index(value = "bBoxWest")
})
public class OsmCollectionEntity extends OsmEntity {
	//https://developer.android.com/reference/androidx/room/Embedded
	public double bBoxNorth = 0;
	public double bBoxSouth = 0;
	public double bBoxWest = 0;
	public double bBoxEast = 0;

	@TypeConverters(DataConverter.class)
	public List<Long> osmMembers = new LinkedList<>();

	@TypeConverters(DataConverter.class)
	public List<Long> osmNodes = new LinkedList<>();

	public OsmCollectionEntity(String stringNodeInfo) throws JSONException {
		this(new JSONObject(stringNodeInfo));
	}

	public OsmCollectionEntity(JSONObject jsonNodeInfo) {
		super(jsonNodeInfo);

		if (osmType == EntityOsmType.relation) {
			createMembersList(jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS));
		}

		if (osmType == EntityOsmType.way) {
			createNodesList(jsonNodeInfo.optJSONArray(ClimbingTags.KEY_NODES));
		}
	}

	public void computeCache(List<OsmNode> osmNodes) {
		if (osmNodes.size() == 0) {
			return;
		}

		bBoxNorth = osmNodes.get(0).decimalLatitude;
		bBoxSouth = osmNodes.get(0).decimalLatitude;
		bBoxEast = osmNodes.get(0).decimalLongitude;
		bBoxWest = osmNodes.get(0).decimalLongitude;

		for (OsmNode crNode: osmNodes) {
			bBoxSouth = Math.min(bBoxSouth, crNode.decimalLatitude);
			bBoxWest = Math.min(bBoxWest, crNode.decimalLongitude);
			bBoxNorth = Math.max(bBoxNorth, crNode.decimalLatitude);
			bBoxEast = Math.max(bBoxEast, crNode.decimalLongitude);
		}

		// take care of ante-meridian
		if ((bBoxEast < 0 != bBoxWest < 0) && GeoUtils.diffAngle(bBoxEast, bBoxWest) > 0) {
			double tmp = bBoxEast;
			bBoxEast = bBoxWest;
			bBoxWest =tmp;
		}
	}

	public void createNodesList(JSONArray nodes) {
		if (nodes == null) {
			return;
		}

		for (int i = 0; i < nodes.length(); ++i) {
			Long nodeId = nodes.optLong(i);
			osmNodes.add(nodeId);
		}
	}

	public void createMembersList(JSONArray nodes) {
		if (nodes == null) {
			return;
		}

		for (int i = 0; i < nodes.length(); ++i) {
			Long nodeId = nodes.optJSONObject(i).optLong(ClimbingTags.KEY_REF);
			osmMembers.add(nodeId);
		}
	}
}
