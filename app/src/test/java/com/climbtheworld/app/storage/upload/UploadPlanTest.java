package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class UploadPlanTest {
	@Test
	public void newRelationBringsItsNewMembersAndComesAfterThem() throws Exception {
		PendingChange route = create("node", -1, "{\"type\":\"node\",\"id\":-1}");
		PendingChange crag = create("relation", -2, relation(-2, "node|-1", "node|5"));
		PendingChange area = create("relation", -3, relation(-3, "relation|-2"));
		PendingChange unrelated = create("node", -4, "{\"type\":\"node\",\"id\":-4}");

		List<PendingChange> plan = UploadPlan.plan(Arrays.asList(area, crag, route, unrelated),
				Collections.singleton("relation|-3"));

		assertEquals(Arrays.asList("node|-1", "relation|-2", "relation|-3"), keys(plan));
	}

	@Test
	public void deletedRouteBringsTheCragItWasTakenOutOf() throws Exception {
		PendingChange route = delete("node", 1, "{\"type\":\"node\",\"id\":1}");
		PendingChange crag = modify("relation", 10, relation(10, "node|2"), relation(10, "node|1", "node|2"));
		PendingChange other = modify("node", 2, "{\"type\":\"node\",\"id\":2}", "{\"type\":\"node\",\"id\":2}");

		List<PendingChange> plan = UploadPlan.plan(Arrays.asList(route, crag, other),
				Collections.singleton("node|1"));

		assertEquals(Arrays.asList("relation|10", "node|1"), keys(plan));
	}

	@Test
	public void deletedRelationsGoBeforeWhatTheyListed() throws Exception {
		PendingChange route = delete("node", 1, "{\"type\":\"node\",\"id\":1}");
		PendingChange crag = delete("relation", 10, relation(10, "node|1"));
		PendingChange area = delete("relation", 20, relation(20, "relation|10"));

		List<PendingChange> plan = UploadPlan.plan(Arrays.asList(route, crag, area),
				Collections.singleton("node|1"));

		assertEquals(Arrays.asList("relation|20", "relation|10", "node|1"), keys(plan));
	}

	@Test
	public void blocksGoCreateModifyDelete() throws Exception {
		PendingChange deleted = delete("node", 3, "{\"type\":\"node\",\"id\":3}");
		PendingChange modified = modify("node", 2, "{\"type\":\"node\",\"id\":2}", "{\"type\":\"node\",\"id\":2}");
		PendingChange created = create("node", -1, "{\"type\":\"node\",\"id\":-1}");

		List<PendingChange> plan = UploadPlan.plan(Arrays.asList(deleted, modified, created),
				new HashSet<>(Arrays.asList("node|3", "node|2", "node|-1")));

		assertEquals(Arrays.asList("node|-1", "node|2", "node|3"), keys(plan));
	}

	@Test
	public void unselectedChangesStay() throws Exception {
		PendingChange first = create("node", -1, "{\"type\":\"node\",\"id\":-1}");
		PendingChange second = create("node", -2, "{\"type\":\"node\",\"id\":-2}");

		assertEquals(Collections.singletonList("node|-2"), keys(UploadPlan.plan(
				Arrays.asList(first, second), Collections.singleton("node|-2"))));
	}

	private static String relation(long id, String... members) {
		StringBuilder json = new StringBuilder("{\"type\":\"relation\",\"id\":" + id + ",\"members\":[");
		for (int index = 0; index < members.length; index++) {
			String[] member = members[index].split("\\|");
			json.append(index > 0 ? "," : "").append("{\"type\":\"").append(member[0])
					.append("\",\"ref\":").append(member[1]).append(",\"role\":\"\"}");
		}
		return json.append("]}").toString();
	}

	private static PendingChange create(String type, long id, String local) throws Exception {
		return new PendingChange(type, id, PendingChange.Action.create, new JSONObject(local), null);
	}

	private static PendingChange modify(String type, long id, String local, String base) throws Exception {
		return new PendingChange(type, id, PendingChange.Action.modify, new JSONObject(local),
				new JSONObject(base));
	}

	private static PendingChange delete(String type, long id, String base) throws Exception {
		return new PendingChange(type, id, PendingChange.Action.delete, new JSONObject(base),
				new JSONObject(base));
	}

	private static List<String> keys(List<PendingChange> plan) {
		List<String> result = new ArrayList<>();
		for (PendingChange change : plan) {
			result.add(change.key());
		}
		return result;
	}
}
