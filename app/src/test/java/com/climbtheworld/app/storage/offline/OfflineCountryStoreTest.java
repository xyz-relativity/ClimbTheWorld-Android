package com.climbtheworld.app.storage.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.EntityCountry;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class OfflineCountryStoreTest {
	private AppDatabase database;
	private OfflineCountryStore store;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
				.allowMainThreadQueries()
				.build();
		store = new OfflineCountryStore(database);
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void deletingOneCountryKeepsEntitiesSharedWithAnotherCountry() throws Exception {
		OsmNode sharedNode = node(42L);
		OsmNode canadaOnlyNode = node(43L);
		OsmCollectionEntity wayWithSameNumericId = way(42L);
		database.osmNodeDao().insertNodesWithReplace(Arrays.asList(sharedNode, canadaOnlyNode));
		database.osmCollectionDao().insertCollectionWithReplace(
				Arrays.asList(wayWithSameNumericId));
		database.entityCountryDao().insertAll(Arrays.asList(
				new EntityCountry(OsmEntity.EntityOsmType.node, 42L, "CA"),
				new EntityCountry(OsmEntity.EntityOsmType.node, 42L, "US"),
				new EntityCountry(OsmEntity.EntityOsmType.node, 43L, "CA"),
				new EntityCountry(OsmEntity.EntityOsmType.way, 42L, "CA")));
		store.markDownloaded("CA", 1L, 3L);
		store.markDownloaded("US", 2L, 1L);

		store.deleteCountry("CA");

		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 42L));
		assertNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 43L));
		assertNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 42L));
		assertEquals(0, database.downloadedCountryDao().count("CA"));
		assertEquals(1, database.downloadedCountryDao().count("US"));

		store.deleteCountry("US");

		assertEquals(0, database.osmNodeDao().count());
	}

	private OsmNode node(long id) throws Exception {
		return new OsmNode(new JSONObject()
				.put("id", id)
				.put("type", "node")
				.put("lat", 45.0)
				.put("lon", -75.0)
				.put("tags", new JSONObject()));
	}

	private OsmCollectionEntity way(long id) throws Exception {
		return new OsmCollectionEntity(new JSONObject()
				.put("id", id)
				.put("type", "way")
				.put("nodes", new JSONArray().put(42L))
				.put("tags", new JSONObject()));
	}
}
