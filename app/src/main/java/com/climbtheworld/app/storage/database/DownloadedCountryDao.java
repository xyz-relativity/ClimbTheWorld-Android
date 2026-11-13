package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DownloadedCountryDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void upsert(DownloadedCountry country);

	// The codes are stored in upper case, and looked up through their index as such, see
	// EntityCountryDao.OF_COUNTRY.
	@Query("DELETE FROM DownloadedCountry WHERE " + EntityCountryDao.OF_COUNTRY)
	void delete(String countryIso);

	@Query("SELECT * FROM DownloadedCountry ORDER BY countryIso")
	List<DownloadedCountry> loadAll();

	@Query("SELECT countryIso FROM DownloadedCountry ORDER BY countryIso")
	List<String> loadCountryIsos();

	@Query("SELECT COUNT(*) FROM DownloadedCountry")
	int countAll();

	@Query("SELECT COUNT(*) FROM DownloadedCountry WHERE " + EntityCountryDao.OF_COUNTRY)
	int count(String countryIso);
}
