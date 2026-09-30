package com.climbtheworld.app.storage.offline;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.DownloadedCountry;

/** Coordinates the atomic state changes associated with removing a country download. */
public class OfflineCountryStore {
	private final AppDatabase database;

	public OfflineCountryStore(AppDatabase database) {
		this.database = database;
	}

	/**
	 * Removes one country's ownership links and only removes clean entities no other country owns.
	 */
	public void deleteCountry(String countryIso) {
		database.runInTransaction(() -> {
			database.entityCountryDao().deleteForCountry(countryIso);
			database.osmNodeDao().deleteUnownedCleanNodes();
			database.osmCollectionDao().deleteUnownedCleanCollections();
			database.downloadedCountryDao().delete(countryIso);
		});
	}

	/** Marks a country as available after its complete import transaction finishes. */
	public void markDownloaded(String countryIso, long downloadedAt, long elementCount) {
		database.downloadedCountryDao().upsert(
				new DownloadedCountry(countryIso, downloadedAt, elementCount));
	}
}
