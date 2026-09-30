package com.climbtheworld.app.storage.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Records a country only after its Overpass response has been imported successfully. */
@Entity
public class DownloadedCountry {
	@PrimaryKey
	public String countryIso;
	public long downloadedAt;
	public long elementCount;

	public DownloadedCountry(String countryIso, long downloadedAt, long elementCount) {
		this.countryIso = countryIso.toUpperCase();
		this.downloadedAt = downloadedAt;
		this.elementCount = elementCount;
	}
}
