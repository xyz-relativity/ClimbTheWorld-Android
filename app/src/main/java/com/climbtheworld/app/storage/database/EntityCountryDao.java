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

	/** How many downloaded nodes, ways and relations of the climbing type each country has. */
	@Query("SELECT countryIso, COUNT(*) AS entityCount FROM ("
			+ "SELECT EntityCountry.countryIso FROM EntityCountry JOIN OsmNode "
			+ "ON OsmNode.osmType = EntityCountry.osmType AND OsmNode.osmID = EntityCountry.osmID "
			+ "WHERE OsmNode.entityClimbingType = :climbingType "
			+ "UNION ALL "
			+ "SELECT EntityCountry.countryIso FROM EntityCountry JOIN OsmCollectionEntity "
			+ "ON OsmCollectionEntity.osmType = EntityCountry.osmType "
			+ "AND OsmCollectionEntity.osmID = EntityCountry.osmID "
			+ "WHERE OsmCollectionEntity.entityClimbingType = :climbingType"
			+ ") GROUP BY countryIso")
	List<CountryEntityCount> countPerCountry(OsmEntity.EntityClimbingType climbingType);

	class CountryEntityCount {
		public String countryIso;
		public int entityCount;
	}
}
