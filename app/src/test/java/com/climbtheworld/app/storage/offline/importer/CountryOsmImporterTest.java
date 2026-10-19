package com.climbtheworld.app.storage.offline.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import androidx.room.Room;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.offline.OfflineCountryStore;

import org.json.JSONObject;
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
	private static final String ROUTE = "{\"sport\":\"climbing\",\"climbing\":\"route_bottom\","
			+ "\"name\":\"Route\"}";

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
				+ "{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Shared Wall\"}},"
				+ "{\"type\":\"way\",\"id\":42,\"nodes\":[42],\"tags\":{\"climbing\":\"route\"}},"
				+ "{\"type\":\"relation\",\"id\":99,\"members\":[{\"type\":\"way\",\"ref\":42,\"role\":\"\"}],"
				+ "\"tags\":{\"type\":\"site\",\"climbing\":\"crag\"}}]}"), "CA");
		long usElements = importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Shared Wall\"}}]}"), "US");

		assertEquals(3L, canadaElements);
		assertEquals(1L, usElements);
		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 42L));
		assertEquals(1, database.osmNodeDao().find("shared", 50,
				OsmEntity.EntityClimbingType.route).size());
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

	@Test
	public void runtimeErrorRemarkKeepsExistingCountryData() throws Exception {
		importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Wall\"}},"
				+ "{\"type\":\"node\",\"id\":43,\"lat\":45.1,\"lon\":-75.1,\"tags\":{\"climbing\":\"crag\",\"name\":\"Crag\"}}]}"), "CA");

		assertThrows(OverpassServerException.class, () -> importer.importResponse(new StringReader(
				"{\"elements\":[{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,"
						+ "\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Wall\"}}],"
						+ "\"remark\":\"runtime error: Query timed out in \\\"query\\\" at line 1 after 801 seconds.\"}"),
				"CA"));

		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 43L));
		assertEquals(2, database.entityCountryDao().countForCountry("CA"));
	}

	@Test
	public void beforeWriteRunsOnlyForValidResponses() throws Exception {
		int[] calls = {0};
		importer.importResponse(new StringReader("{\"elements\":[]}"), "CA", () -> calls[0]++);
		assertEquals(1, calls[0]);

		assertThrows(OverpassServerException.class, () -> importer.importResponse(new StringReader(
				"{\"elements\":[],\"remark\":\"runtime error: out of memory\"}"), "CA",
				() -> calls[0]++));
		assertEquals(1, calls[0]);
	}

	@Test
	public void cancelledImportWritesNothing() throws Exception {
		CancellationSignal cancellation = new CancellationSignal();
		// Cancelled after parsing, as when the country is deleted while its response is read.
		assertThrows(OperationCanceledException.class, () -> importer.importResponse(new StringReader(
						"{\"elements\":[{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,"
								+ "\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Wall\"}}]}"), "CA", cancellation::cancel,
				cancellation));

		assertNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 42L));
		assertEquals(0, database.entityCountryDao().countForCountry("CA"));
		assertEquals(0, database.downloadedCountryDao().countAll());
	}

	@Test
	public void nonErrorRemarkStillImports() throws Exception {
		long elements = importer.importResponse(new StringReader("{\"remark\":\"informational\","
				+ "\"elements\":[{\"type\":\"node\",\"id\":42,\"lat\":45.0,\"lon\":-75.0,"
				+ "\"tags\":{\"climbing\":\"route_bottom\",\"name\":\"Wall\"}}]}"), "CA");

		assertEquals(1L, elements);
	}

	@Test
	public void keepsOnlyClimbingFeaturesAndWhatTheyAreDrawnWith() throws Exception {
		long elements = importer.importResponse(new StringReader("{\"elements\":["
				+ node(1, ROUTE) + "," + node(2, "{}") + ","
				+ "{\"type\":\"way\",\"id\":10,\"nodes\":[1,2],\"tags\":{\"climbing\":\"route\"}},"
				+ node(3, "{}") + ","
				+ "{\"type\":\"way\",\"id\":11,\"nodes\":[3],"
				+ "\"tags\":{\"sport\":\"climbing\",\"natural\":\"cliff\"}},"
				+ node(4, "{\"sport\":\"climbing\",\"climbing\":\"yes\",\"name\":\"Somewhere\"}")
				+ "]}"), "CA");

		assertEquals(3L, elements);
		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 1L));
		assertNotNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 2L));
		assertNotNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 10L));
		assertNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 3L));
		assertNull(database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 11L));
		assertNull(database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 4L));
		assertEquals(3, database.entityCountryDao().countForCountry("CA"));
	}

	@Test
	public void aFacilityWayWithoutItsNodesIsPlacedAtItsCenter() throws Exception {
		// The query asks for a gym's center and not for the nodes of its outline.
		long elements = importer.importResponse(new StringReader("{\"elements\":["
				+ "{\"type\":\"way\",\"id\":20,\"center\":{\"lat\":45.5,\"lon\":-73.6},"
				+ "\"nodes\":[1,2,3,1],\"tags\":{\"sport\":\"climbing\",\"leisure\":\"sports_centre\","
				+ "\"name\":\"Gym\"}}]}"), "CA");

		assertEquals(1L, elements);
		OsmCollectionEntity gym = database.osmCollectionDao().find(OsmEntity.EntityOsmType.way, 20L);
		assertEquals(OsmEntity.EntityClimbingType.artificial, gym.entityClimbingType);
		assertEquals(45.5, gym.bBoxNorth, 0);
		assertEquals(45.5, gym.bBoxSouth, 0);
		assertEquals(-73.6, gym.bBoxEast, 0);
		assertEquals(-73.6, gym.bBoxWest, 0);
	}

	@Test
	public void localChangesAreKeptWhateverTheirTags() throws Exception {
		// One changed in the app into no climbing feature, the other into one.
		OsmNode untagged = new OsmNode(new JSONObject(node(1, "{\"name\":\"Was a route\"}")));
		untagged.localUpdateState = OsmEntity.EntityState.toUpdate;
		OsmNode retagged = new OsmNode(new JSONObject(node(2, ROUTE)));
		retagged.localUpdateState = OsmEntity.EntityState.toUpdate;
		database.osmNodeDao().insertNodesWithReplace(Arrays.asList(untagged, retagged));

		importer.importResponse(new StringReader("{\"elements\":[" + node(1, ROUTE) + ","
				+ node(2, "{\"sport\":\"climbing\",\"natural\":\"peak\",\"name\":\"Peak\"}")
				+ "]}"), "CA");

		OsmNode first = database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 1L);
		assertEquals("Was a route", first.getTags().optString("name"));
		assertEquals(OsmEntity.EntityState.toUpdate, first.localUpdateState);
		OsmNode second = database.osmNodeDao().find(OsmEntity.EntityOsmType.node, 2L);
		assertEquals(OsmEntity.EntityClimbingType.route, second.entityClimbingType);
		assertEquals(OsmEntity.EntityState.toUpdate, second.localUpdateState);
	}

	private static String node(long id, String tags) {
		return "{\"type\":\"node\",\"id\":" + id + ",\"lat\":45.0,\"lon\":-75.0,\"tags\":" + tags + "}";
	}
}
