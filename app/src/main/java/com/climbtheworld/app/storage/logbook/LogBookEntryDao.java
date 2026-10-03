package com.climbtheworld.app.storage.logbook;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.climbtheworld.app.storage.database.OsmEntity;

@Dao
public interface LogBookEntryDao {
	@Query("SELECT * FROM LogBookEntry WHERE osmType = :osmType AND osmID = :osmID")
	LogBookEntry find(OsmEntity.EntityOsmType osmType, long osmID);

	@Upsert
	void upsert(LogBookEntry entry);

	@Query("DELETE FROM LogBookEntry WHERE osmType = :osmType AND osmID = :osmID")
	void delete(OsmEntity.EntityOsmType osmType, long osmID);
}
