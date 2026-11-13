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

	/**
	 * The rows of a country, whatever the case its code is given in. The codes are stored in
	 * upper case, see EntityCountry, so the one given is put in upper case too: compared without
	 * regard to case instead, the index on the codes went unused and every row was read.
	 */
	String OF_COUNTRY = "countryIso = UPPER(:countryIso)";

	@Query("DELETE FROM EntityCountry WHERE " + OF_COUNTRY)
	void deleteForCountry(String countryIso);

	/**
	 * Like deleteForCountry, but keeps the links of entities changed in the app: a new download
	 * leaves those entities as they are, and the ones created in the app are not in it.
	 */
	@Query("DELETE FROM EntityCountry WHERE " + OF_COUNTRY + " "
			+ "AND NOT EXISTS (SELECT 1 FROM OsmNode WHERE OsmNode.osmType = EntityCountry.osmType "
			+ "AND OsmNode.osmID = EntityCountry.osmID AND OsmNode.localUpdateState != 'clean') "
			+ "AND NOT EXISTS (SELECT 1 FROM OsmCollectionEntity "
			+ "WHERE OsmCollectionEntity.osmType = EntityCountry.osmType "
			+ "AND OsmCollectionEntity.osmID = EntityCountry.osmID "
			+ "AND OsmCollectionEntity.localUpdateState != 'clean')")
	void deleteCleanForCountry(String countryIso);

	@Query("DELETE FROM EntityCountry WHERE osmType = :osmType AND osmID = :osmID")
	void deleteForEntity(OsmEntity.EntityOsmType osmType, long osmID);

	@Query("SELECT COUNT(*) FROM EntityCountry WHERE " + OF_COUNTRY)
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
