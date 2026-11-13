package com.climbtheworld.app.storage.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The queries by country find its rows whatever the case its code is given in, and do it through
 * the index on the codes rather than by reading every row, see EntityCountryDao.OF_COUNTRY.
 */
@RunWith(RobolectricTestRunner.class)
public class CountryIndexTest {
	private AppDatabase database;

	@Before
	public void setUp() {
		database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),
				AppDatabase.class).allowMainThreadQueries().build();
		database.entityCountryDao().insertAll(Arrays.asList(
				new EntityCountry(OsmEntity.EntityOsmType.node, 1, "CA"),
				new EntityCountry(OsmEntity.EntityOsmType.node, 2, "CA"),
				new EntityCountry(OsmEntity.EntityOsmType.node, 2, "US")));
		database.downloadedCountryDao().upsert(new DownloadedCountry("CA", 0, 2));
		database.downloadedCountryDao().upsert(new DownloadedCountry("US", 0, 1));
	}

	@After
	public void tearDown() {
		database.close();
	}

	@Test
	public void aCountrysEntitiesAreCountedWhateverTheCaseOfItsCode() {
		assertEquals(2, database.entityCountryDao().countForCountry("CA"));
		assertEquals(2, database.entityCountryDao().countForCountry("ca"));
		assertEquals(1, database.entityCountryDao().countForCountry("Us"));
		assertEquals(0, database.entityCountryDao().countForCountry("FR"));
	}

	@Test
	public void aCountrysEntitiesAreDeletedWhateverTheCaseOfItsCode() {
		database.entityCountryDao().deleteForCountry("ca");

		assertEquals(0, database.entityCountryDao().countForCountry("CA"));
		assertEquals(1, database.entityCountryDao().countForCountry("US"));
	}

	@Test
	public void aDownloadedCountryIsFoundAndDeletedWhateverTheCaseOfItsCode() {
		assertEquals(1, database.downloadedCountryDao().count("ca"));

		database.downloadedCountryDao().delete("ca");

		assertEquals(0, database.downloadedCountryDao().count("CA"));
		assertEquals(1, database.downloadedCountryDao().count("US"));
	}

	@Test
	public void aCountrysEntitiesAreFoundThroughTheIndexOnTheCodes() {
		String plan = queryPlan("SELECT COUNT(*) FROM EntityCountry WHERE "
				+ EntityCountryDao.OF_COUNTRY);
		assertTrue(plan, plan.contains("index_EntityCountry_countryIso (countryIso=?)"));
	}

	@Test
	public void aDownloadedCountryIsFoundThroughItsPrimaryKey() {
		String plan = queryPlan("SELECT COUNT(*) FROM DownloadedCountry WHERE "
				+ EntityCountryDao.OF_COUNTRY);
		assertTrue(plan, plan.contains("sqlite_autoindex_DownloadedCountry_1 (countryIso=?)"));
	}

	/**
	 * What SQLite plans for the query, as its EXPLAIN QUERY PLAN details.
	 *
	 * @param sql a query by country, with its code as Room binds it
	 */
	private String queryPlan(String sql) {
		List<String> details = new ArrayList<>();
		try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
				.query("EXPLAIN QUERY PLAN " + sql.replace(":countryIso", "?"))) {
			while (cursor.moveToNext()) {
				details.add(cursor.getString(cursor.getColumnIndexOrThrow("detail")));
			}
		}
		return String.join(" / ", details);
	}
}
