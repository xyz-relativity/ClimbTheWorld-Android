package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONObject;

/**
 * A change made in the app, waiting to go to OSM: the element as changed, the downloaded version
 * it started from, and once prepared, what is sent and to which version on the server.
 */
public final class PendingChange {
	public enum Action {
		create, modify, delete
	}

	/** node, way or relation. */
	public final String type;
	/** Negative for a create, until OSM gives the element its id. */
	public final long id;
	public final Action action;
	/** The element as changed in the app. */
	public final JSONObject local;
	/** The downloaded version the change started from; null for a create. */
	public final JSONObject base;

	/** What is uploaded: the local change merged with the server's version. */
	public JSONObject upload;
	/** The server's version the change applies to; 0 for a create. */
	public long version;

	public PendingChange(String type, long id, Action action, JSONObject local, JSONObject base) {
		this.type = type;
		this.id = id;
		this.action = action;
		this.local = local;
		this.base = base;
		this.upload = local;
	}

	public String key() {
		return RelationMembers.key(type, id);
	}
}
