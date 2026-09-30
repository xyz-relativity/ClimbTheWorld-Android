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

	@Query("DELETE FROM DownloadedCountry WHERE countryIso = :countryIso COLLATE NOCASE")
	void delete(String countryIso);

	@Query("SELECT * FROM DownloadedCountry ORDER BY countryIso")
	List<DownloadedCountry> loadAll();

	@Query("SELECT COUNT(*) FROM DownloadedCountry WHERE countryIso = :countryIso COLLATE NOCASE")
	int count(String countryIso);
}
