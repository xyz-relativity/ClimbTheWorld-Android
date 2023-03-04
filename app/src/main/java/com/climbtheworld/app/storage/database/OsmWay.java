package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.TypeConverters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Entity(indices = {@Index(value = "bBoxNorth"), @Index(value = "bBoxEast"), @Index(value = "bBoxSouth"), @Index(value = "bBoxWest")})
public class OsmWay extends OsmComposedEntity {

	@TypeConverters(DataConverter.class)
	public List<Long> osmNodes = new LinkedList<>();

	public OsmWay(String stringNodeInfo) throws JSONException {
		this(new JSONObject(stringNodeInfo));
	}

	public OsmWay(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
		this.updateDate = System.currentTimeMillis();

		createNodesList(jsonNodeInfo.optJSONArray(ClimbingTags.KEY_NODES));
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

	public void computeCache(Map<Long, OsmNode> nodeCache) {
		LinkedList<OsmNode> nodesList = new LinkedList<>();
		for (Long node: osmNodes) {
			nodesList.add(nodeCache.get(node));
		}

		this.computeCache(nodesList, nodeCache);
	}
}
