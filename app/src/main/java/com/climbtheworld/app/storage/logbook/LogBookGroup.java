package com.climbtheworld.app.storage.logbook;

/**
 * A heading the log book list groups its entries under: a country, an area or a crag.
 *
 * @param key  tells groups of the same name apart, such as two crags both called "Main Wall"
 * @param name what the heading shows
 */
public record LogBookGroup(Kind kind, String key, String name) {

	/**
	 * Groups of the same level are listed in this order, then by name: the areas of a country
	 * before its crags that are in no area, and the entries without a known country last.
	 */
	public enum Kind {
		country,
		area,
		crag,
		unknownCountry,
		// Entries whose element is not in the downloaded data, so their place is not known.
		notDownloaded
	}
}
