package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.TypeConverters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Entity(indices = {@Index(value = "bBoxNorth"), @Index(value = "bBoxEast"), @Index(value = "bBoxSouth"), @Index(value = "bBoxWest")})
public class OsmRelation extends OsmComposedEntity {

	@TypeConverters(DataConverter.class)
	public List<Long> osmMembers = new LinkedList<>();

	@TypeConverters(DataConverter.class)
	public List<Long> convexHall = new LinkedList<>();

	@TypeConverters(DataConverter.class)
	public List<Long> osmNodes = new LinkedList<>();

	public OsmRelation(String stringNodeInfo) throws JSONException {
		this(new JSONObject(stringNodeInfo));
	}

	public OsmRelation(JSONObject jsonNodeInfo) {
		this.setJSONData(jsonNodeInfo); //this should always be firs.

		this.osmID = this.jsonNodeInfo.optLong(ClimbingTags.KEY_ID, 0);
		this.updateDate = System.currentTimeMillis();
		createMembersList(jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS));
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

	public void computeCache(Map<Long, OsmNode> nodeCache, Map<Long, OsmWay> wayCache, Map<Long, OsmRelation> relationCache) {
		osmNodes = createNodesList(this, nodeCache, wayCache, relationCache);

		LinkedList<OsmNode> nodesList = new LinkedList<>();
		for (Long node: osmNodes) {
			nodesList.add(nodeCache.get(node));
		}

		this.computeCache(nodesList, nodeCache);
		computeConvexHall(nodesList);
	}

	private List<Long> createNodesList(OsmRelation relation, Map<Long, OsmNode> nodeCache, Map<Long, OsmWay> wayCache, Map<Long, OsmRelation> relationCache) {
		List<Long> result = new LinkedList<>();
		for (Long memberId: relation.osmMembers) {
			if (nodeCache.containsKey(memberId)) {
				result.add(memberId);
				continue;
			}

			if (wayCache.containsKey(memberId)) {
				result.addAll(wayCache.get(memberId).osmNodes);
				continue;
			}

			if (relationCache.containsKey(memberId)) {
				result.addAll(createNodesList(relationCache.get(memberId), nodeCache, wayCache, relationCache));
			}
		}

		return result;
	}

	private void computeConvexHall(LinkedList<OsmNode> nodesList) {
		if (nodesList.size() == 0) {
			return;
		}

		Collections.sort(nodesList, new Comparator<OsmNode>() {
			@Override
			public int compare(OsmNode o1, OsmNode o2) {
				return o1.decimalLatitude.compareTo(o2.decimalLatitude);
			}
		});

		int n = nodesList.size();

		OsmNode[] lUpper = new OsmNode[n];

		lUpper[0] = nodesList.get(0);
		lUpper[1] = nodesList.get(1);

		int lUpperSize = 2;

		for (int i = 2; i < n; i++) {
			lUpper[lUpperSize] = nodesList.get(i);
			lUpperSize++;

			while (lUpperSize > 2 && !rightTurn(lUpper[lUpperSize - 3], lUpper[lUpperSize - 2], lUpper[lUpperSize - 1])) {
				// Remove the middle point of the three last
				lUpper[lUpperSize - 2] = lUpper[lUpperSize - 1];
				lUpperSize--;
			}
		}

		OsmNode[] lLower = new OsmNode[n];

		lLower[0] = nodesList.get(n - 1);
		lLower[1] = nodesList.get(n - 2);

		int lLowerSize = 2;

		for (int i = n - 3; i >= 0; i--) {
			lLower[lLowerSize] = nodesList.get(i);
			lLowerSize++;

			while (lLowerSize > 2 && !rightTurn(lLower[lLowerSize - 3], lLower[lLowerSize - 2], lLower[lLowerSize - 1])) {
				// Remove the middle point of the three last
				lLower[lLowerSize - 2] = lLower[lLowerSize - 1];
				lLowerSize--;
			}
		}

		for (int i = 0; i < lUpperSize; i++) {
			convexHall.add(lUpper[i].osmID);
		}

		for (int i = 1; i < lLowerSize - 1; i++) {
			convexHall.add(lLower[i].osmID);
		}
	}

	private boolean rightTurn(OsmNode a, OsmNode b, OsmNode c) {
		return (b.decimalLatitude - a.decimalLatitude) * (c.decimalLongitude - a.decimalLongitude)
				- (b.decimalLongitude - a.decimalLongitude) * (c.decimalLatitude - a.decimalLatitude) > 0;
	}
}
