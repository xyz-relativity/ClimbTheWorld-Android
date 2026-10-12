package com.climbtheworld.app.storage.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityState;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.offline.CountryBoxes;
import com.climbtheworld.app.storage.offline.importer.CountryOsmImporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
	public void uploadedNewRouteTakesItsOsmIdEverywhere() {
		OsmNode node = store.newNode(45.60, -73.70);
		long localId = node.osmID;
		OsmCollectionEntity crag = relation(100);
		RelationMembers.append(crag.getMembers(), "node", localId, "");
		store.save(Collections.singletonList(node), Collections.singletonList(crag));

		store.markUploaded(EntityOsmType.node, localId, 5000);

		assertNull(node(localId));
		OsmNode uploaded = node(5000);
		assertEquals(EntityState.clean, uploaded.localUpdateState);
		assertEquals(5000, uploaded.jsonNodeInfo.optLong("id"));
		assertEquals(Arrays.asList(1L, 2L, 5000L), relation(100).osmMembers);
		assertTrue(relation(100).osmNodes.contains(5000L));
		assertTrue(relation(200).osmNodes.contains(5000L));
		// The crag's change, adding the route, is still to upload.
		assertEquals(EntityState.toUpdate, relation(100).localUpdateState);
		assertEquals(Collections.singletonList("CA"),
				database.entityCountryDao().findCountries(EntityOsmType.node, 5000));
	}

	@Test
	public void uploadedDeleteIsForgotten() {
		store.deleteNode(3);

		store.markUploaded(EntityOsmType.node, 3, 3);

		assertNull(node(3));
		assertFalse(store.hasChanges());
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

	private OsmNode node(long osmId) {
		return database.osmNodeDao().find(EntityOsmType.node, osmId);
	}

	private OsmCollectionEntity relation(long osmId) {
		return database.osmCollectionDao().find(EntityOsmType.relation, osmId);
	}
}
