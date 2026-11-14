package com.climbtheworld.app.map.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.storage.upload.OsmElements;

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
public class MemberEditsTest {
	private static final String NODE = "node";
	private static final String RELATION = "relation";

	private final JSONArray members = members(1, 2);
	private final MemberEdits edits = new MemberEdits(members);

	@Test
	public void aTappedRouteIsSavedBeforeItIsLoaded() {
		assertTrue(edits.join(NODE, 3));

		// What a save right at the tap finds, the load still queued.
		assertEquals(Arrays.asList(1L, 2L, 3L), saved());
	}

	@Test
	public void aRouteTappedTwiceJoinsOnce() {
		assertTrue(edits.join(NODE, 3));
		assertFalse(edits.join(NODE, 3));

		assertEquals(Arrays.asList(1L, 2L, 3L), saved());
	}

	@Test
	public void aMemberTappedIsAlreadyAMember() {
		assertFalse(edits.join(NODE, 2));

		assertEquals(Arrays.asList(1L, 2L), saved());
	}

	@Test
	public void aCragAndARouteOfTheSameIdAreTwoMembers() {
		assertTrue(edits.join(RELATION, 2));

		assertTrue(edits.contains(NODE, 2));
		assertTrue(edits.contains(RELATION, 2));
	}

	@Test
	public void aRouteTappedThenTakenOutIsNotSaved() {
		edits.join(NODE, 3);
		edits.remove(2);

		assertEquals(Arrays.asList(1L, 2L), saved());
		// Its load coming in after, of a stored element or not, changes nothing.
		assertFalse(edits.notStored(NODE, 3));
		assertEquals(Arrays.asList(1L, 2L), saved());
	}

	@Test
	public void aRouteTappedTakenOutAndTappedAgainIsSavedOnce() {
		edits.join(NODE, 3);
		edits.remove(2);

		assertTrue(edits.join(NODE, 3));

		assertEquals(Arrays.asList(1L, 2L, 3L), saved());
	}

	@Test
	public void aTappedElementNotStoredIsTakenOutWhenLoaded() {
		edits.join(NODE, 3);

		assertTrue(edits.notStored(NODE, 3));

		assertEquals(Arrays.asList(1L, 2L), saved());
	}

	@Test
	public void aSaveKeepsTheMembersOfThePress() throws JSONException {
		edits.join(NODE, 3);
		JSONObject atThePress = OsmElements.copy(
				new JSONObject().put("members", members));

		// What the list does after the press does not reach the write.
		edits.join(NODE, 4);
		edits.notStored(NODE, 3);

		assertEquals(Arrays.asList(1L, 2L, 3L),
				RelationMembers.refs(atThePress.getJSONArray("members")));
	}

	@Test
	public void theTappedRouteKeepsTheEmptyCrag() {
		JSONArray empty = new JSONArray();
		MemberEdits newCrag = new MemberEdits(empty);

		newCrag.join(NODE, 3);

		// Saved at once, the crag has its route, and is not deleted as empty.
		assertEquals(Collections.singletonList(3L), RelationMembers.refs(empty));
	}

	@Test
	public void aTappedMemberIsAddedWithoutRole() {
		edits.join(NODE, 3);

		assertEquals("", members.optJSONObject(2).optString("role", null));
	}

	private List<Long> saved() {
		return RelationMembers.refs(members);
	}

	private static JSONArray members(long... nodeIds) {
		JSONArray result = new JSONArray();
		for (long id : nodeIds) {
			RelationMembers.append(result, NODE, id, "");
		}
		return result;
	}
}
