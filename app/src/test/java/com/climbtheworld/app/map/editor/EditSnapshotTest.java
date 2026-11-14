package com.climbtheworld.app.map.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class EditSnapshotTest {
	private static final String ROUTE = "{\"type\":\"node\",\"id\":12,\"lat\":45.5,\"lon\":6.25,"
			+ "\"tags\":{\"name\":\"Dalle\",\"climbing:grade:uiaa\":\"6+\"}}";

	@Test
	public void anUntouchedEditIsNotChanged() throws JSONException {
		EditSnapshot loaded = EditSnapshot.of(new JSONObject(ROUTE));

		assertFalse(EditSnapshot.changed(loaded, new JSONObject(ROUTE)));
	}

	@Test
	public void theSnapshotKeepsTheContentAsItWasTaken() throws JSONException {
		JSONObject route = new JSONObject(ROUTE);
		EditSnapshot loaded = EditSnapshot.of(route);

		// The editors change the element in place.
		route.getJSONObject("tags").put("name", "Dalle grise");

		assertTrue(EditSnapshot.changed(loaded, route));
	}

	@Test
	public void aChangedTagIsChanged() throws JSONException {
		EditSnapshot loaded = EditSnapshot.of(new JSONObject(ROUTE));
		JSONObject edited = new JSONObject(ROUTE);
		edited.getJSONObject("tags").put("climbing:grade:uiaa", "7-");

		assertTrue(EditSnapshot.changed(loaded, edited));
	}

	@Test
	public void anAddedOrRemovedTagIsChanged() throws JSONException {
		EditSnapshot loaded = EditSnapshot.of(new JSONObject(ROUTE));
		JSONObject added = new JSONObject(ROUTE);
		added.getJSONObject("tags").put("climbing:bolts", "8");
		JSONObject removed = new JSONObject(ROUTE);
		removed.getJSONObject("tags").remove("name");

		assertTrue(EditSnapshot.changed(loaded, added));
		assertTrue(EditSnapshot.changed(loaded, removed));
	}

	@Test
	public void aMovedNodeIsChanged() throws JSONException {
		EditSnapshot loaded = EditSnapshot.of(new JSONObject(ROUTE));
		JSONObject moved = new JSONObject(ROUTE).put("lat", 45.5000001);

		assertTrue(EditSnapshot.changed(loaded, moved));
	}

	@Test
	public void theOrderOfKeysDoesNotMatter() throws JSONException {
		JSONObject reordered = new JSONObject("{\"tags\":{\"climbing:grade:uiaa\":\"6+\","
				+ "\"name\":\"Dalle\"},\"lon\":6.25,\"lat\":45.5,\"id\":12,\"type\":\"node\"}");

		assertEquals(EditSnapshot.of(new JSONObject(ROUTE)), EditSnapshot.of(reordered));
	}

	@Test
	public void numbersReadAsAnotherTypeAreTheSame() throws JSONException {
		JSONObject asRead = new JSONObject("{\"id\":12,\"lat\":45,\"lon\":6.250}");
		JSONObject asWritten = new JSONObject().put("id", 12L).put("lat", 45.0).put("lon", 6.25);

		assertEquals(EditSnapshot.of(asRead), EditSnapshot.of(asWritten));
	}

	@Test
	public void aNumberIsNotTheTextOfIt() throws JSONException {
		assertNotEquals(EditSnapshot.of(new JSONObject().put("id", 12)),
				EditSnapshot.of(new JSONObject().put("id", "12")));
	}

	@Test
	public void anEmptyValueIsTheSameAsNone() throws JSONException {
		JSONObject withEmpty = new JSONObject(ROUTE);
		withEmpty.getJSONObject("tags").put("description", "");
		withEmpty.put("role", JSONObject.NULL);

		assertFalse(EditSnapshot.changed(EditSnapshot.of(new JSONObject(ROUTE)), withEmpty));
	}

	@Test
	public void theOrderOfMembersMatters() throws JSONException {
		JSONObject crag = new JSONObject().put("members", new JSONArray()
				.put(member(1)).put(member(2)));
		JSONObject reordered = new JSONObject().put("members", new JSONArray()
				.put(member(2)).put(member(1)));

		assertTrue(EditSnapshot.changed(EditSnapshot.of(crag), reordered));
	}

	@Test
	public void aListIsComparedByItsItems() throws JSONException {
		EditSnapshot loaded = EditSnapshot.of(Arrays.asList(member(1), member(2)));

		assertFalse(EditSnapshot.changed(loaded, Arrays.asList(member(1), member(2))));
		assertTrue(EditSnapshot.changed(loaded, Collections.singletonList(member(1))));
		assertTrue(EditSnapshot.changed(EditSnapshot.of(Collections.emptyList()),
				Collections.singletonList(member(1))));
	}

	@Test
	public void contentThatCantBeReadIsChanged() throws JSONException {
		assertTrue(EditSnapshot.changed(EditSnapshot.of(new JSONObject(ROUTE)), null));
	}

	@Test
	public void withoutASnapshotItIsChanged() throws JSONException {
		assertTrue(EditSnapshot.changed(null, new JSONObject(ROUTE)));
	}

	private static JSONObject member(long ref) throws JSONException {
		return new JSONObject().put("type", "node").put("ref", ref).put("role", "");
	}
}
