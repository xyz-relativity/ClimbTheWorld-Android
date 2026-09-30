package com.climbtheworld.app.storage.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Records every country download that supplied an OSM entity.
 *
 * <p>An entity can occur in more than one country response, so country ownership must not be
 * stored on the entity itself.</p>
 */
@Entity(primaryKeys = {"osmType", "osmID", "countryIso"}, indices = {
		@Index(value = "countryIso"),
		@Index(value = {"osmType", "osmID"})
})
public class EntityCountry {
	@NonNull
	public OsmEntity.EntityOsmType osmType;
	public long osmID;
	@NonNull
	public String countryIso;

	public EntityCountry(OsmEntity.EntityOsmType osmType, long osmID, String countryIso) {
		this.osmType = osmType;
		this.osmID = osmID;
		this.countryIso = countryIso.toUpperCase();
	}
}
