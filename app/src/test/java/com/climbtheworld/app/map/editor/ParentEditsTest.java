package com.climbtheworld.app.map.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.editor.ParentEdits.Membership;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class ParentEditsTest {
	private static final long ROUTE = 5;

	private final ParentEdits edits = new ParentEdits(EntityOsmType.node, ROUTE);

	@Test
	public void aParentPickedIsSavedBeforeItsPinIsLoaded() {
		OsmCollectionEntity crag = crag(10, 1, 2);

		Membership membership = edits.join(crag);

		// What a save right after the pick finds, the pin still loading.
		assertNull(membership.pin);
		assertSaved(crag);
		assertEquals(Arrays.asList(1L, 2L, ROUTE), RelationMembers.refs(crag.getMembers()));
	}

	@Test
	public void thePinLoadedChangesNothingOfWhatIsSaved() {
		OsmCollectionEntity crag = crag(10, 1, 2);
		Membership membership = edits.join(crag);

		membership.pin = new DisplayableGeoNode(new GeoNode(crag.jsonNodeInfo));

		assertEquals(Collections.singletonList(membership), edits.memberships());
		assertSaved(crag);
		assertEquals(Arrays.asList(1L, 2L, ROUTE), RelationMembers.refs(crag.getMembers()));
	}

	@Test
	public void aParentPickedThenLeftIsSavedAsItWas() {
		OsmCollectionEntity crag = crag(10, 1, 2);

		edits.leave(edits.join(crag));

		assertTrue(edits.memberships().isEmpty());
		// Saved all the same, with the members it is stored with, which changes nothing.
		assertSaved(crag);
		assertEquals(Arrays.asList(1L, 2L), RelationMembers.refs(crag.getMembers()));
	}

	@Test
	public void aNewParentLeftIsNotSaved() {
		OsmCollectionEntity crag = crag(-3, ROUTE);

		edits.leave(edits.join(crag));

		assertTrue(edits.memberships().isEmpty());
		assertSaved();
	}

	@Test
	public void aStoredParentIsOnlySavedOnceLeft() {
		OsmCollectionEntity crag = crag(10, 1, ROUTE, 2);
		Membership membership = new Membership(crag, null, false);
		edits.addLoaded(Collections.singletonList(membership), Collections.emptyList());
		assertSaved();

		edits.leave(membership);

		assertTrue(edits.memberships().isEmpty());
		assertSaved(crag);
		assertEquals(Arrays.asList(1L, 2L), RelationMembers.refs(crag.getMembers()));
	}

	@Test
	public void aParentLeftThenPickedAgainListsTheElementOnce() {
		Membership membership = new Membership(crag(10, 1, ROUTE), null, false);
		edits.addLoaded(Collections.singletonList(membership), Collections.emptyList());
		edits.leave(membership);
		// The picker lists the parent as it is stored, the element still in it.
		OsmCollectionEntity listed = crag(10, 1, ROUTE);

		edits.join(listed);

		assertSaved(listed);
		assertEquals(Arrays.asList(1L, ROUTE), RelationMembers.refs(listed.getMembers()));
	}

	@Test
	public void theParentsLeftAreSavedBeforeTheOnesJoined() {
		OsmCollectionEntity left = crag(10, ROUTE, 1);
		Membership membership = new Membership(left, null, false);
		OsmCollectionEntity kept = crag(11, ROUTE);
		edits.addLoaded(Arrays.asList(membership, new Membership(kept, null, false)),
				Collections.emptyList());
		OsmCollectionEntity joined = crag(12, 2);

		edits.join(joined);
		edits.leave(membership);

		assertSaved(left, joined);
		assertSame(joined, edits.membershipIn(12).relation);
		assertNull(edits.membershipIn(10));
	}

	@Test
	public void aCragJoinsAnAreaAsARelation() {
		ParentEdits areas = new ParentEdits(EntityOsmType.relation, ROUTE);
		// A node of the crag's id is another element.
		OsmCollectionEntity area = crag(20, ROUTE);
		assertFalse(areas.listsElement(area));

		areas.join(area);

		JSONObject added = area.getMembers().optJSONObject(1);
		assertEquals("relation", added.optString("type"));
		assertEquals(ROUTE, added.optLong("ref"));
		assertTrue(areas.listsElement(area));
	}

	/** What the editor saves with the element are these relations, in this order. */
	private void assertSaved(OsmCollectionEntity... relations) {
		List<OsmCollectionEntity> saved = edits.changed();
		assertEquals(relations.length, saved.size());
		for (int index = 0; index < relations.length; index++) {
			assertSame(relations[index], saved.get(index));
		}
	}

	/** A relation with these nodes as its members. */
	private static OsmCollectionEntity crag(long id, long... nodes) {
		try {
			JSONArray members = new JSONArray();
			for (long node : nodes) {
				members.put(RelationMembers.member("node", node, ""));
			}
			return new OsmCollectionEntity(new JSONObject()
					.put("type", "relation")
					.put("id", id)
					.put("members", members)
					.put("tags", new JSONObject()
							.put("type", "site")
							.put("climbing", "crag")));
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}
}
