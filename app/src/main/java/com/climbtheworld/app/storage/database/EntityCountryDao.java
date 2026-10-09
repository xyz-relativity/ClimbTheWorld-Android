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

	/** The countries whose download supplied the entity, more than one along a border. */
	@Query("SELECT countryIso FROM EntityCountry WHERE osmType = :osmType AND osmID = :osmID "
			+ "ORDER BY countryIso")
	List<String> findCountries(OsmEntity.EntityOsmType osmType, long osmID);
}
