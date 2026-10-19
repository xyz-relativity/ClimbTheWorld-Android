package com.climbtheworld.app.storage.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityState;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.offline.CountryBoxes;
import com.climbtheworld.app.storage.offline.importer.CountryOsmImporter;
import com.climbtheworld.app.storage.upload.DiffResult;
import com.climbtheworld.app.storage.upload.PendingChange;
import com.climbtheworld.app.storage.upload.UploadPlan;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class OsmEditStoreTest {
	/** Routes 1 and 2 in crag 100, itself in area 200; route 3 alone; node 4 on the gym way 300. */
	private static final String DOWNLOAD = "{\"elements\":["
			+ route(1, 45.50, -73.60, "One") + ","
			+ route(2, 45.51, -73.61, "Two") + ","
			+ route(3, 45.52, -73.62, "Three") + ","
			+ "{\"type\":\"node\",\"id\":4,\"lat\":45.55,\"lon\":-73.65},"
			+ "{\"type\":\"way\",\"id\":300,\"nodes\":[4],"
			+ "\"tags\":{\"sport\":\"climbing\",\"building\":\"yes\",\"name\":\"Gym\"}},"
			+ "{\"type\":\"relation\",\"id\":100,\"members\":["
			+ "{\"type\":\"node\",\"ref\":1,\"role\":\"\"},{\"type\":\"node\",\"ref\":2,\"role\":\"\"}],"
			+ "\"tags\":{\"type\":\"site\",\"site\":\"climbing\",\"sport\":\"climbing\","
			+ "\"climbing\":\"crag\",\"name\":\"Crag\"}},"
			+ "{\"type\":\"relation\",\"id\":200,\"members\":[{\"type\":\"relation\",\"ref\":100,\"role\":\"\"}],"
			+ "\"tags\":{\"type\":\"site\",\"site\":\"climbing\",\"sport\":\"climbing\","
			+ "\"climbing\":\"area\",\"name\":\"Area\"}}]}";

	private AppDatabase database;
	private OsmEditStore store;
	private CountryOsmImporter importer;

	private static String route(long id, double latitude, double longitude, String name) {
		return "{\"type\":\"node\",\"id\":" + id + ",\"lat\":" + latitude + ",\"lon\":" + longitude
				+ ",\"tags\":{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\""
				+ name + "\"}}";
	}

	@Before
	public void setUp() throws Exception {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
				.allowMainThreadQueries()
				.build();
		// Montreal is in both boxes, the US one being the smaller.
		store = new OsmEditStore(database, CountryBoxes.read(new StringReader("header\n"
				+ "CA,Canada,-141,41,-52,84\n"
				+ "US,United States,-125,24,-66,50\n")));
		importer = new CountryOsmImporter(database);
		importer.importResponse(new StringReader(DOWNLOAD), "CA");
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void newNodeGetsAPlaceholderIdAndADownloadedCountry() throws Exception {
		OsmNode node = store.newNode(45.53, -73.63);
		node.getTags().put("name", "New");

		store.saveNode(node);

		assertTrue(node.osmID < 0);
		OsmNode stored = node(node.osmID);
		assertEquals(EntityState.toUpdate, stored.localUpdateState);
		assertNull(stored.baseJson);
		assertTrue(stored.localEditDate > 0);
		assertEquals("New", stored.getTags().getString("name"));
		assertEquals(Collections.singletonList("CA"),
				database.entityCountryDao().findCountries(EntityOsmType.node, node.osmID));
		assertTrue(store.hasChanges());
	}

	@Test
	public void placeholderIdsAreUniqueAcrossTypes() {
		OsmNode node = store.newNode(45.53, -73.63);
		store.saveNode(node);
		OsmCollectionEntity relation = store.newRelation();
		long next = store.newPlaceholderId();

		assertTrue(relation.osmID < 0);
		assertNotEquals(node.osmID, relation.osmID);
		assertTrue(next < Math.min(node.osmID, relation.osmID));
	}

	@Test
	public void editKeepsTheDownloadedVersionUntilEditedBack() throws Exception {
		assertFalse(store.hasChanges());
		OsmNode node = node(1);
		node.getTags().put("name", "Renamed");
		store.saveNode(node);

		OsmNode stored = node(1);
		assertEquals(EntityState.toUpdate, stored.localUpdateState);
		assertEquals("One", stored.baseJson.getJSONObject("tags").getString("name"));

		stored.getTags().put("name", "Again");
		store.saveNode(stored);
		assertEquals("One", node(1).baseJson.getJSONObject("tags").getString("name"));

		OsmNode again = node(1);
		again.getTags().put("name", "One");
		store.saveNode(again);
		assertEquals(EntityState.clean, node(1).localUpdateState);
		assertNull(node(1).baseJson);
		assertFalse(store.hasChanges());
	}

	@Test
	public void movingARouteUpdatesTheBoxesOfItsCragAndArea() {
		OsmNode node = node(1);
		node.updatePOILocation(45.40, -73.60, 0);
		store.saveNode(node);

		assertEquals(45.40, relation(100).bBoxSouth, 1e-9);
		assertEquals(45.40, relation(200).bBoxSouth, 1e-9);
		// The cached box is not something to upload.
		assertEquals(EntityState.clean, relation(100).localUpdateState);
		assertEquals(EntityState.clean, relation(200).localUpdateState);
	}

	@Test
	public void routeAddedToACragGoesToTheEndAndComesOutOnRevert() {
		OsmNode node = store.newNode(45.60, -73.70);
		OsmCollectionEntity crag = relation(100);
		RelationMembers.append(crag.getMembers(), "node", node.osmID, "");

		store.save(Collections.singletonList(node), Collections.singletonList(crag));

		OsmCollectionEntity storedCrag = relation(100);
		assertEquals(Arrays.asList(1L, 2L, node.osmID), storedCrag.osmMembers);
		assertEquals(EntityState.toUpdate, storedCrag.localUpdateState);
		assertEquals(2, storedCrag.baseJson.getJSONArray("members").length());
		assertEquals(45.60, storedCrag.bBoxNorth, 1e-9);
		assertTrue(storedCrag.osmNodes.contains(node.osmID));
		assertEquals(45.60, relation(200).bBoxNorth, 1e-9);

		store.revert(EntityOsmType.node, node.osmID);

		assertNull(node(node.osmID));
		assertEquals(Arrays.asList(1L, 2L), relation(100).osmMembers);
		assertEquals(EntityState.clean, relation(100).localUpdateState);
		assertEquals(45.51, relation(100).bBoxNorth, 1e-9);
		assertTrue(database.entityCountryDao().findCountries(EntityOsmType.node, node.osmID).isEmpty());
	}

	@Test
	public void reorderingMembersIsAChangeUntilPutBack() {
		OsmCollectionEntity crag = relation(100);
		RelationMembers.move(crag.getMembers(), 0, 1);
		store.saveRelation(crag);

		assertEquals(Arrays.asList(2L, 1L), relation(100).osmMembers);
		assertEquals(EntityState.toUpdate, relation(100).localUpdateState);

		OsmCollectionEntity reordered = relation(100);
		RelationMembers.move(reordered.getMembers(), 1, 0);
		store.saveRelation(reordered);

		assertEquals(Arrays.asList(1L, 2L), relation(100).osmMembers);
		assertEquals(EntityState.clean, relation(100).localUpdateState);
	}

	@Test
	public void deletedRouteLeavesItsCragAndTheMap() {
		assertTrue(store.deleteNode(1));

		assertEquals(EntityState.toDelete, node(1).localUpdateState);
		assertNotNull(node(1).baseJson);
		assertEquals(Collections.singletonList(2L), relation(100).osmMembers);
		assertEquals(EntityState.toUpdate, relation(100).localUpdateState);
		List<Long> visible = database.osmNodeDao().loadBBox(46, -73, 45, -74,
				EntityClimbingType.route);
		assertFalse(visible.contains(1L));
		assertTrue(visible.contains(2L));
	}

	@Test
	public void deletingTheLastRouteDeletesTheCragAndTheEmptiedArea() {
		store.deleteNode(1);
		store.deleteNode(2);

		assertEquals(EntityState.toDelete, relation(100).localUpdateState);
		assertEquals(EntityState.toDelete, relation(200).localUpdateState);
		assertTrue(database.osmCollectionDao().loadBBox(46, -73, 45, -74,
				EntityClimbingType.crag, EntityClimbingType.area).isEmpty());
	}

	@Test
	public void newCragGoesAwayWithItsOnlyNewRoute() {
		OsmNode node = store.newNode(45.60, -73.70);
		OsmCollectionEntity crag = store.newRelation();
		RelationMembers.append(crag.getMembers(), "node", node.osmID, "");
		store.save(Collections.singletonList(node), Collections.singletonList(crag));
		assertNotNull(relation(crag.osmID));
		assertEquals(Collections.singletonList("CA"),
				database.entityCountryDao().findCountries(EntityOsmType.relation, crag.osmID));

		store.revert(EntityOsmType.node, node.osmID);

		assertNull(relation(crag.osmID));
		assertTrue(database.entityCountryDao().findCountries(EntityOsmType.relation, crag.osmID).isEmpty());
	}

	@Test
	public void revertBringsBackADeletedRoute() throws Exception {
		store.deleteNode(3);
		store.revert(EntityOsmType.node, 3);

		assertEquals(EntityState.clean, node(3).localUpdateState);
		assertEquals("Three", node(3).getTags().getString("name"));
		assertNull(node(3).baseJson);
	}

	@Test
	public void nodeOfAWayIsNotDeleted() {
		assertTrue(store.isUsedByWay(4));
		assertFalse(store.deleteNode(4));
		assertEquals(EntityState.clean, node(4).localUpdateState);
	}

	@Test
	public void pendingChangesTellCreateModifyAndDelete() throws Exception {
		OsmNode added = store.newNode(45.60, -73.70);
		OsmNode renamed = node(2);
		renamed.getTags().put("name", "Renamed");
		store.save(Arrays.asList(added, renamed), Collections.emptyList());
		store.deleteNode(3);

		Map<String, PendingChange> pending = new HashMap<>();
		for (PendingChange change : store.loadPendingChanges()) {
			pending.put(change.key(), change);
		}

		assertEquals(PendingChange.Action.create, pending.get("node|" + added.osmID).action);
		assertNull(pending.get("node|" + added.osmID).base);
		assertEquals(PendingChange.Action.modify, pending.get("node|2").action);
		assertEquals("Two", pending.get("node|2").base.getJSONObject("tags").getString("name"));
		assertEquals("Renamed", pending.get("node|2").local.getJSONObject("tags").getString("name"));
		assertEquals(PendingChange.Action.delete, pending.get("node|3").action);
		assertEquals(3, pending.size());
	}

	@Test
	public void uploadedNewRouteAndCragTakeTheirOsmIdsEverywhere() throws Exception {
		OsmNode route = store.newNode(45.60, -73.70);
		OsmCollectionEntity crag = store.newRelation();
		crag.getTags().put("type", "site").put("climbing", "crag").put("sport", "climbing");
		RelationMembers.append(crag.getMembers(), "node", route.osmID, "");
		OsmCollectionEntity area = relation(200);
		RelationMembers.append(area.getMembers(), "relation", crag.osmID, "");
		store.save(Collections.singletonList(route), Arrays.asList(crag, area));
		List<PendingChange> plan = UploadPlan.plan(store.loadPendingChanges(),
				Collections.singleton("relation|200"));

		store.applyUploaded(plan, DiffResult.parse("<diffResult>"
				+ "<node old_id=\"" + route.osmID + "\" new_id=\"5000\" new_version=\"1\"/>"
				+ "<relation old_id=\"" + crag.osmID + "\" new_id=\"6000\" new_version=\"1\"/>"
				+ "<relation old_id=\"200\" new_id=\"200\" new_version=\"7\"/></diffResult>"));

		assertNull(node(route.osmID));
		assertNull(relation(crag.osmID));
		assertEquals(EntityState.clean, node(5000).localUpdateState);
		assertEquals(Collections.singletonList(5000L), relation(6000).osmMembers);
		assertEquals(EntityState.clean, relation(6000).localUpdateState);
		assertEquals(6000, relation(6000).jsonNodeInfo.getLong("id"));
		assertEquals(Arrays.asList(100L, 6000L), relation(200).osmMembers);
		assertEquals(EntityState.clean, relation(200).localUpdateState);
		assertNull(relation(200).baseJson);
		assertTrue(relation(200).osmNodes.contains(5000L));
		assertEquals(Collections.singletonList("CA"),
				database.entityCountryDao().findCountries(EntityOsmType.node, 5000));
		assertFalse(store.hasChanges());
	}

	@Test
	public void uploadedModifyKeepsTheMergedVersionAndDeleteGoes() throws Exception {
		OsmNode renamed = node(2);
		renamed.getTags().put("name", "Renamed");
		store.saveNode(renamed);
		store.deleteNode(3);
		List<PendingChange> plan = UploadPlan.plan(store.loadPendingChanges(),
				new HashSet<>(Arrays.asList("node|2", "node|3")));
		for (PendingChange change : plan) {
			if (change.key().equals("node|2")) {
				// As merged with a grade someone added on OSM.
				change.upload.getJSONObject("tags").put("climbing:grade:uiaa", "6");
			}
		}

		store.applyUploaded(plan, DiffResult.parse("<diffResult>"
				+ "<node old_id=\"2\" new_id=\"2\" new_version=\"5\"/><node old_id=\"3\"/></diffResult>"));

		assertEquals("6", node(2).getTags().getString("climbing:grade:uiaa"));
		assertEquals("Renamed", node(2).getTags().getString("name"));
		assertEquals(EntityState.clean, node(2).localUpdateState);
		assertNull(node(3));
		assertFalse(store.hasChanges());
	}

	@Test
	public void serverVersionReplacesTheLocalChange() throws Exception {
		OsmNode moved = node(1);
		moved.updatePOILocation(45.40, -73.60, 0);
		store.saveNode(moved);

		store.useServerVersion(EntityOsmType.node, 1, new JSONObject(
				"{\"type\":\"node\",\"id\":1,\"version\":4,\"lat\":45.55,\"lon\":-73.60,"
						+ "\"tags\":{\"name\":\"Theirs\"}}"));

		assertEquals(EntityState.clean, node(1).localUpdateState);
		assertEquals("Theirs", node(1).getTags().getString("name"));
		assertFalse(node(1).jsonNodeInfo.has("version"));
		assertEquals(45.55, relation(100).bBoxNorth, 1e-9);

		store.useServerVersion(EntityOsmType.node, 2, new JSONObject(
				"{\"type\":\"node\",\"id\":2,\"visible\":false}"));
		assertNull(node(2));
	}

	@Test
	public void changesAreListedNewestFirst() throws Exception {
		OsmNode first = node(3);
		first.getTags().put("name", "First");
		store.saveNode(first);
		Thread.sleep(2);
		OsmCollectionEntity crag = relation(100);
		crag.getTags().put("name", "Second");
		store.saveRelation(crag);

		List<OsmEntity> changes = store.loadChanges();

		assertEquals(2, changes.size());
		assertEquals(100, changes.get(0).osmID);
		assertEquals(3, changes.get(1).osmID);
	}

	@Test
	public void downloadKeepsLocalChangesAndTheNewRoutesCountry() throws Exception {
		OsmNode renamed = node(1);
		renamed.getTags().put("name", "Renamed");
		OsmNode added = store.newNode(45.60, -73.70);
		OsmCollectionEntity crag = relation(100);
		RelationMembers.append(crag.getMembers(), "node", added.osmID, "");
		store.save(Arrays.asList(renamed, added), Collections.singletonList(crag));

		importer.importResponse(new StringReader(DOWNLOAD), "CA");

		assertEquals("Renamed", node(1).getTags().getString("name"));
		assertNotNull(node(added.osmID));
		assertEquals(Collections.singletonList("CA"),
				database.entityCountryDao().findCountries(EntityOsmType.node, added.osmID));
		assertEquals(Arrays.asList(1L, 2L, added.osmID), relation(100).osmMembers);
		assertEquals(45.60, relation(100).bBoxNorth, 1e-9);
	}

	@Test
	public void parentsLeaveOutDeletedRelations() {
		assertEquals(Collections.singletonList(100L), ids(store.loadParents(EntityOsmType.node, 1)));
		assertEquals(Collections.singletonList(200L),
				ids(store.loadParents(EntityOsmType.relation, 100)));
		assertTrue(store.loadParents(EntityOsmType.node, 3).isEmpty());

		store.deleteRelation(100);

		assertTrue(store.loadParents(EntityOsmType.node, 1).isEmpty());
	}

	@Test
	public void nearbyCragsListTheLastChangedFirstThenTheNearest() throws Exception {
		OsmCollectionEntity farCrag = store.newRelation();
		farCrag.getTags().put("type", "site").put("climbing", "crag").put("sport", "climbing");
		RelationMembers.append(farCrag.getMembers(), "node", 3, "");
		store.saveRelation(farCrag);
		long nearCrag = 100;

		assertEquals(Arrays.asList(farCrag.osmID, nearCrag), ids(store.loadNearbyRelations(
				45.50, -73.60, 5000, EntityClimbingType.crag)));

		Thread.sleep(2);
		OsmCollectionEntity renamed = relation(nearCrag);
		renamed.getTags().put("name", "Renamed");
		store.saveRelation(renamed);

		assertEquals(Arrays.asList(nearCrag, farCrag.osmID), ids(store.loadNearbyRelations(
				45.50, -73.60, 5000, EntityClimbingType.crag)));
		assertEquals(Collections.singletonList(nearCrag), ids(store.loadNearbyRelations(
				45.50, -73.60, 500, EntityClimbingType.crag)));
		assertEquals(Collections.singletonList(200L), ids(store.loadNearbyRelations(
				45.50, -73.60, 5000, EntityClimbingType.area)));
	}

	@Test
	public void farRelationsAreListedInWideBoundsOrFoundByName() throws Exception {
		OsmNode farRoute = store.newNode(46.80, -71.20);
		farRoute.getTags().put("sport", "climbing").put("climbing", "route_bottom");
		store.saveNode(farRoute);
		OsmCollectionEntity farCrag = store.newRelation();
		farCrag.getTags().put("type", "site").put("climbing", "crag").put("sport", "climbing")
				.put("name", "Far Crag");
		RelationMembers.append(farCrag.getMembers(), "node", farRoute.osmID, "");
		store.saveRelation(farCrag);
		long nearCrag = 100;

		assertEquals(Collections.singletonList(nearCrag), ids(store.loadNearbyRelations(
				45.50, -73.60, 5000, EntityClimbingType.crag)));
		// Each crag in a box of its own, and both in a wide one: listed once each.
		assertEquals(Arrays.asList(farCrag.osmID, nearCrag), ids(store.loadRelationsIn(
				Arrays.asList(new MapBounds(46.81, -71.19, 46.79, -71.21),
						new MapBounds(45.52, -73.59, 45.49, -73.62),
						new MapBounds(47, -71, 45, -74)),
				45.50, -73.60, EntityClimbingType.crag)));
		assertEquals(Collections.singletonList(farCrag.osmID), ids(store.findRelations(
				"far", 45.50, -73.60, EntityClimbingType.crag)));
		assertEquals(Arrays.asList(farCrag.osmID, nearCrag), ids(store.findRelations(
				"CRAG", 45.50, -73.60, EntityClimbingType.crag)));
		assertEquals(Collections.singletonList(200L), ids(store.findRelations(
				"a", 45.50, -73.60, EntityClimbingType.area)));

		store.deleteRelation(nearCrag);

		assertEquals(Collections.singletonList(farCrag.osmID), ids(store.findRelations(
				"crag", 45.50, -73.60, EntityClimbingType.crag)));
	}

	@Test
	public void cragSavedWithoutMembersIsDeleted() {
		OsmCollectionEntity crag = relation(100);
		RelationMembers.removeAll(crag.getMembers(), "node", 1);
		RelationMembers.removeAll(crag.getMembers(), "node", 2);

		store.saveRelation(crag);

		assertEquals(EntityState.toDelete, relation(100).localUpdateState);
		assertEquals(EntityState.toDelete, relation(200).localUpdateState);
		assertEquals(EntityState.clean, node(1).localUpdateState);
	}

	@Test
	public void newCragWithoutMembersIsNotSaved() {
		OsmCollectionEntity crag = store.newRelation();

		store.saveRelation(crag);

		assertNull(relation(crag.osmID));
		assertFalse(store.hasChanges());
	}

	@Test
	public void membersAreLoadedByTypeAndIdLeavingOutTheMissingOnes() {
		Map<String, OsmEntity> areaMembers = store.loadMembers(relation(200).getMembers());
		assertEquals(Collections.singleton("relation|100"), areaMembers.keySet());

		OsmCollectionEntity crag = relation(100);
		RelationMembers.append(crag.getMembers(), "node", 999, "");
		Map<String, OsmEntity> cragMembers = store.loadMembers(crag.getMembers());
		assertEquals(new HashSet<>(Arrays.asList("node|1", "node|2")), cragMembers.keySet());
		assertEquals(2, cragMembers.get("node|2").osmID);
	}

	private static List<Long> ids(List<OsmCollectionEntity> collections) {
		List<Long> result = new ArrayList<>();
		for (OsmCollectionEntity collection : collections) {
			result.add(collection.osmID);
		}
		return result;
	}

	private OsmNode node(long osmId) {
		return database.osmNodeDao().find(EntityOsmType.node, osmId);
	}

	private OsmCollectionEntity relation(long osmId) {
		return database.osmCollectionDao().find(EntityOsmType.relation, osmId);
	}
}
