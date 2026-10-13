package com.climbtheworld.app.storage.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class RelationMembersTest {
	@Test
	public void movesMemberForwardAndBackKeepingTheOthersInOrder() throws Exception {
		JSONArray members = members(1, 2, 3, 4);

		RelationMembers.move(members, 0, 2);
		assertEquals(Arrays.asList(2L, 3L, 1L, 4L), RelationMembers.refs(members));

		RelationMembers.move(members, 3, 0);
		assertEquals(Arrays.asList(4L, 2L, 3L, 1L), RelationMembers.refs(members));

		RelationMembers.move(members, 1, 1);
		assertEquals(Arrays.asList(4L, 2L, 3L, 1L), RelationMembers.refs(members));
	}

	@Test
	public void moveKeepsTheMemberObjectWithItsRole() throws Exception {
		JSONArray members = new JSONArray()
				.put(RelationMembers.member("node", 1, ""))
				.put(new JSONObject("{\"type\":\"way\",\"ref\":2,\"role\":\"outer\",\"extra\":true}"));

		RelationMembers.move(members, 1, 0);

		JSONObject moved = members.getJSONObject(0);
		assertEquals("outer", moved.getString("role"));
		assertTrue(moved.getBoolean("extra"));
	}

	@Test
	public void moveOutOfRangeFails() {
		JSONArray members = members(1, 2);

		assertThrows(IndexOutOfBoundsException.class, () -> RelationMembers.move(members, 0, 2));
		assertThrows(IndexOutOfBoundsException.class, () -> RelationMembers.move(members, -1, 0));
	}

	@Test
	public void appendAddsAtTheEnd() {
		JSONArray members = members(1, 2);

		RelationMembers.append(members, "node", 3, null);

		assertEquals(Arrays.asList(1L, 2L, 3L), RelationMembers.refs(members));
		assertEquals("", members.optJSONObject(2).optString("role", "missing"));
	}

	@Test
	public void removeAllMatchesTypeAndRef() {
		JSONArray members = members(1, 2, 1);
		RelationMembers.append(members, "way", 1, "");

		assertEquals(2, RelationMembers.removeAll(members, "node", 1));

		assertEquals(Arrays.asList(2L, 1L), RelationMembers.refs(members));
		assertFalse(RelationMembers.contains(members, "node", 1));
		assertTrue(RelationMembers.contains(members, "way", 1));
		assertEquals(1, RelationMembers.indexOf(members, "way", 1));
	}

	@Test
	public void replaceRefOnlyTouchesTheMatchingType() {
		JSONArray members = members(-1, 2);
		RelationMembers.append(members, "relation", -1, "");

		assertEquals(1, RelationMembers.replaceRef(members, "node", -1, 100));

		assertEquals(Arrays.asList(100L, 2L, -1L), RelationMembers.refs(members));
	}

	@Test
	public void keyNamesTypeAndId() throws Exception {
		assertEquals("node|42", RelationMembers.key("node", 42));
		assertEquals("relation|-3", RelationMembers.key(new JSONObject("{\"type\":\"relation\",\"ref\":\"-3\"}")));
	}

	@Test
	public void refsOfMissingMembersIsEmpty() {
		assertTrue(RelationMembers.refs(null).isEmpty());
	}

	private static JSONArray members(long... refs) {
		JSONArray result = new JSONArray();
		for (long ref : refs) {
			RelationMembers.append(result, "node", ref, "");
		}
		return result;
	}
}
