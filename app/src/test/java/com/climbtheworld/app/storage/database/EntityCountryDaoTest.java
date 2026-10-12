package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;

import androidx.room.Room;

import com.climbtheworld.app.storage.offline.importer.CountryOsmImporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class EntityCountryDaoTest {
	private AppDatabase database;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
				.allowMainThreadQueries()
				.build();
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void countsRouteNodesAndWaysPerCountry() throws Exception {
		CountryOsmImporter importer = new CountryOsmImporter(database);
		importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":1,\"lat\":45.0,\"lon\":-75.0,\"tags\":"
				+ "{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"Shared\"}},"
				+ "{\"type\":\"node\",\"id\":2,\"lat\":45.0,\"lon\":-75.0,\"tags\":"
				+ "{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"Second\"}},"
				+ "{\"type\":\"node\",\"id\":3,\"lat\":45.0,\"lon\":-75.0,\"tags\":"
				+ "{\"sport\":\"climbing\",\"climbing\":\"crag\",\"name\":\"Crag\"}},"
				+ "{\"type\":\"node\",\"id\":4,\"lat\":45.0,\"lon\":-75.0},"
				+ "{\"type\":\"way\",\"id\":10,\"nodes\":[4],\"tags\":"
				+ "{\"sport\":\"climbing\",\"climbing\":\"route\",\"name\":\"Way\"}}]}"), "CA");
		importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":1,\"lat\":45.0,\"lon\":-75.0,\"tags\":"
				+ "{\"sport\":\"climbing\",\"climbing\":\"route_bottom\",\"name\":\"Shared\"}}]}"), "US");

		Map<String, Integer> routes = new HashMap<>();
		for (EntityCountryDao.CountryEntityCount count : database.entityCountryDao()
				.countPerCountry(OsmEntity.EntityClimbingType.route)) {
			routes.put(count.countryIso, count.entityCount);
		}

		Map<String, Integer> expected = new HashMap<>();
		expected.put("CA", 3);
		expected.put("US", 1);
		assertEquals(expected, routes);
	}
}
