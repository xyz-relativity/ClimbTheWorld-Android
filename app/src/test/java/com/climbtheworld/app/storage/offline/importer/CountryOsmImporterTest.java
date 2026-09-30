package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.offline.OfflineCountryStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringReader;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class CountryOsmImporterTest {
	private AppDatabase database;
	private CountryOsmImporter importer;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
				.allowMainThreadQueries()
				.build();
		importer = new CountryOsmImporter(database);
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void importsTypedEntitiesAndRetainsCrossCountryOwnership() throws Exception {
		long canadaElements = importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,\"tags\":{}},"
				+ "{\"type\":\"way\",\"id\":42,\"nodes\":[42],\"tags\":{\"sport\":\"climbing\"}},"
				+ "{\"type\":\"relation\",\"id\":99,\"members\":[{\"type\":\"way\",\"ref\":42,\"role\":\"\"}],\"tags\":{\"sport\":\"climbing\"}}]}"), "CA");
		long usElements = importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,\"tags\":{}}]}"), "US");

		assertEquals(3L, canadaElements);
		assertEquals(1L, usElements);
		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 42L));
		assertNotNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 42L));
		assertEquals(1, database.osmCollectionDao()
				.find(OsmEntity.EntityOsmType.relation, 99L).osmNodes.size());
		assertEquals(1, database.downloadedCountryDao().count("CA"));
		assertEquals(1, database.downloadedCountryDao().count("US"));
		assertEquals(2, database.downloadedCountryDao().countAll());
		assertEquals(Arrays.asList("CA", "US"), database.downloadedCountryDao().loadCountryIsos());

		new OfflineCountryStore(database).deleteCountry("CA");

		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 42L));
		assertNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 42L));
		assertNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.relation, 99L));
		assertEquals(1, database.downloadedCountryDao().countAll());
	}
}
