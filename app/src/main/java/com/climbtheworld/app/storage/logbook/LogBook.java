package com.climbtheworld.app.storage.logbook;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;

/**
 * Reads and writes log book entries. Must be called off the main thread.
 */
public final class LogBook {
	public static final int MAX_NOTE_LENGTH = 1000;

	private LogBook() {
		//hide constructor
	}

	/**
	 * Elements that are not uploaded to OSM yet have a temporary negative ID that can later be
	 * given to another new element, so they cannot be logged.
	 */
	public static boolean canLog(long osmID) {
		return osmID > 0;
	}

	/**
	 * @return the element's entry with its snapshot refreshed, or null when it has none.
	 */
	public static LogBookEntry load(LogBookDatabase database, OsmEntity.EntityOsmType osmType,
	                                GeoNode element) {
		LogBookEntry entry = database.logBookDao().find(osmType, element.osmID);
		if (entry != null) {
			refreshSnapshot(database, entry, element);
		}
		return entry;
	}

	/**
	 * Stores the element's current name, type and location with the entry, if they changed.
	 * Not an edit by the user, so the entry's update time is kept.
	 */
	public static void refreshSnapshot(LogBookDatabase database, LogBookEntry entry,
	                                   GeoNode element) {
		if (entry.updateSnapshot(element)) {
			database.logBookDao().upsert(entry);
		}
	}

	/**
	 * Stores the entry, or deletes it when it holds neither a note nor an attempt.
	 */
	public static void save(LogBookDatabase database, LogBookEntry entry) {
		if (entry.isEmpty()) {
			database.logBookDao().delete(entry.osmType, entry.osmID);
			entry.createdAt = 0;
			entry.updatedAt = 0;
			return;
		}

		long now = System.currentTimeMillis();
		if (entry.createdAt == 0) {
			entry.createdAt = now;
		}
		entry.updatedAt = now;
		database.logBookDao().upsert(entry);
	}
}
