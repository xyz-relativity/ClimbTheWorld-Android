package com.climbtheworld.app.storage.logbook;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;

/**
 * The user's personal log of one OSM element (area, crag, route, gym...).
 *
 * <p>Entries are keyed by OSM type and ID only and are never linked to the OSM cache, so they
 * survive OSM data updates and removals. The element's name, type and location are kept as a
 * snapshot, refreshed whenever the element is viewed, so an entry still makes sense when its OSM
 * data is no longer stored locally.</p>
 */
@Entity(primaryKeys = {"osmType", "osmID"})
public class LogBookEntry {
	@NonNull
	public OsmEntity.EntityOsmType osmType;
	public long osmID;
	@NonNull
	public String note = "";
	@NonNull
	public Attempt attempt = Attempt.none;
	public long createdAt;
	public long updatedAt;

	// Snapshot of the element. The type is kept as text so that changes to GeoNode.NodeTypes
	// can never make a stored entry unreadable.
	public String name;
	public String nodeType;
	public double decimalLatitude;
	public double decimalLongitude;

	public LogBookEntry(@NonNull OsmEntity.EntityOsmType osmType, long osmID) {
		this.osmType = osmType;
		this.osmID = osmID;
	}

	public boolean isEmpty() {
		return note.trim().isEmpty() && attempt == Attempt.none;
	}

	/**
	 * @return true when the snapshot changed and the entry needs to be stored again.
	 */
	public boolean updateSnapshot(GeoNode element) {
		String elementName = element.getName();
		String elementType = element.getNodeType().name();
		if (elementName.equals(name) && elementType.equals(nodeType)
				&& element.decimalLatitude == decimalLatitude
				&& element.decimalLongitude == decimalLongitude) {
			return false;
		}

		name = elementName;
		nodeType = elementType;
		decimalLatitude = element.decimalLatitude;
		decimalLongitude = element.decimalLongitude;
		return true;
	}

	/**
	 * The outcome of the user's attempts on a route. Stored by name, so constants must not be
	 * renamed.
	 */
	public enum Attempt {
		//not attempted, or nothing logged about it
		none(R.string.log_book_not_attempted, R.string.log_book_not_attempted),
		//completed first try, without falls or takes
		flashed(R.string.log_book_flashed, R.string.log_book_flashed_description),
		//completed without falls or takes, after a few tries
		sent(R.string.log_book_sent, R.string.log_book_sent_description),
		//completed with takes or falls
		completed(R.string.log_book_completed, R.string.log_book_completed_description),
		//attempted without reaching the top
		notCompleted(R.string.log_book_not_completed,
				R.string.log_book_not_completed_description);

		private final int nameId;
		private final int descriptionId;

		Attempt(int nameId, int descriptionId) {
			this.nameId = nameId;
			this.descriptionId = descriptionId;
		}

		public int getNameId() {
			return nameId;
		}

		public int getDescriptionId() {
			return descriptionId;
		}
	}
}
