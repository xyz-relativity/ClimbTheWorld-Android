package com.climbtheworld.app.storage.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EntityCountryDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insertAll(List<EntityCountry> entities);

	@Query("DELETE FROM EntityCountry WHERE countryIso = :countryIso COLLATE NOCASE")
	void deleteForCountry(String countryIso);

	@Query("SELECT COUNT(*) FROM EntityCountry WHERE countryIso = :countryIso COLLATE NOCASE")
	int countForCountry(String countryIso);
}
