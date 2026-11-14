package com.climbtheworld.app.storage.edit;

import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;

/**
 * An edited element downloaded from OSM is no longer in the local data, so its changes have no
 * downloaded version to start from and can't be kept. A country download running while the
 * element's editor is open drops it when it was deleted on OSM or left the country's data.
 * <p>
 * Checked, so that every editor saving shows it to the user: thrown on the database thread, it
 * would otherwise end the app.
 */
public class ElementNotStoredException extends Exception {
	public final EntityOsmType osmType;
	public final long osmId;

	public ElementNotStoredException(EntityOsmType osmType, long osmId) {
		super(osmType + " " + osmId + " is not in the local data");
		this.osmType = osmType;
		this.osmId = osmId;
	}
}
