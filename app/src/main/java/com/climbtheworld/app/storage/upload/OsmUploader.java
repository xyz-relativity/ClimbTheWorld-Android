package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.edit.OsmEditStore;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Sends changes made in the app to OSM, in one changeset that OSM applies all or nothing. Each
 * change is first merged with the element's current version on OSM; where both sides changed the
 * same thing, the user picks one. Calls block, so they run off the UI thread.
 */
public class OsmUploader {
	/** A deleted element was changed on OSM since the download. */
	public static final String CHANGED_ON_SERVER = "changed";

	private final OsmEditStore store;
	private final OsmApi api;

	/** An upload ready to send. */
	public static final class Prepared {
		/** The changes to send, in upload order. */
		public final List<PendingChange> plan;
		/** The changes that conflict with OSM, by key, with what conflicts; see ThreeWayMerge. */
		public final Map<String, List<String>> conflicts;
		/** OSM's current versions of the changed elements, by key. */
		final Map<String, JSONObject> current;

		Prepared(List<PendingChange> plan, Map<String, List<String>> conflicts,
		         Map<String, JSONObject> current) {
			this.plan = plan;
			this.conflicts = conflicts;
			this.current = current;
		}
	}

	public OsmUploader(OsmEditStore store, OsmApi api) {
		this.store = store;
		this.api = api;
	}

	public boolean canWrite() throws IOException {
		return api.canWrite();
	}

	/**
	 * Plans the upload of the selected changes, see UploadPlan, and merges each with OSM's
	 * current version.
	 *
	 * @param selected the keys of the changes to upload, see PendingChange.key
	 */
	public Prepared prepare(Set<String> selected) throws IOException {
		List<PendingChange> plan = UploadPlan.plan(store.loadPendingChanges(), selected);
		Map<String, JSONObject> current = fetchCurrent(plan);
		Map<String, List<String>> conflicts = new LinkedHashMap<>();
		for (PendingChange change : plan) {
			if (change.action == PendingChange.Action.create) {
				continue;
			}
			JSONObject server = current.get(change.key());
			if (server == null) {
				throw new IOException("OpenStreetMap did not return " + change.key());
			}
			change.version = server.optLong("version");
			JSONObject base = change.base != null ? change.base : change.local;
			if (change.action == PendingChange.Action.modify) {
				ThreeWayMerge.Result merged = ThreeWayMerge.merge(base, change.local, server);
				change.upload = merged.merged;
				if (!merged.conflicts.isEmpty()) {
					conflicts.put(change.key(), merged.conflicts);
				}
			} else if (!ThreeWayMerge.unchangedSince(base, server)) {
				conflicts.put(change.key(), Collections.singletonList(ThreeWayMerge.isDeleted(server)
						? ThreeWayMerge.DELETED : CHANGED_ON_SERVER));
			}
		}
		return new Prepared(plan, conflicts, current);
	}

	/**
	 * Settles the conflicts for the app: changes go up as merged, the app's values winning, and
	 * deletes go ahead. An element OSM deleted can't take a change, so that one stays local; one
	 * deleted on both sides is done.
	 */
	public void keepLocal(Prepared prepared) {
		Iterator<PendingChange> changes = prepared.plan.iterator();
		while (changes.hasNext()) {
			PendingChange change = changes.next();
			JSONObject server = prepared.current.get(change.key());
			if (!prepared.conflicts.containsKey(change.key()) || !ThreeWayMerge.isDeleted(server)) {
				continue;
			}
			if (change.action == PendingChange.Action.delete) {
				store.useServerVersion(EntityOsmType.valueOf(change.type), change.id, server);
			}
			changes.remove();
		}
	}

	/** Settles the conflicts for OSM: the app's changes of those elements are dropped. */
	public void useServer(Prepared prepared) {
		Iterator<PendingChange> changes = prepared.plan.iterator();
		while (changes.hasNext()) {
			PendingChange change = changes.next();
			if (prepared.conflicts.containsKey(change.key())) {
				store.useServerVersion(EntityOsmType.valueOf(change.type), change.id,
						prepared.current.get(change.key()));
				changes.remove();
			}
		}
	}

	/**
	 * Sends the prepared changes and records the result in the local data.
	 *
	 * @param status told the string of each step
	 * @return how many changes OSM took, 0 when nothing was left to send
	 */
	public int upload(Prepared prepared, String comment, String createdBy, IntConsumer status)
			throws IOException {
		if (prepared.plan.isEmpty()) {
			return 0;
		}
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put("comment", comment);
		tags.put("created_by", createdBy);
		status.accept(R.string.osm_start_change_set);
		long changesetId = api.createChangeset(tags);
		DiffResult result;
		try {
			status.accept(R.string.osm_pushing_data);
			result = api.upload(changesetId, OsmChangeXml.osmChange(prepared.plan, changesetId,
					createdBy));
		} finally {
			status.accept(R.string.osm_commit_change_set);
			try {
				api.closeChangeset(changesetId);
			} catch (IOException ignored) {
				// OSM closes an idle changeset by itself after an hour.
			}
		}
		status.accept(R.string.osm_updating_local_data);
		store.applyUploaded(prepared.plan, result);
		return prepared.plan.size();
	}

	private Map<String, JSONObject> fetchCurrent(List<PendingChange> plan) throws IOException {
		Map<String, List<Long>> idsByType = new HashMap<>();
		for (PendingChange change : plan) {
			if (change.action != PendingChange.Action.create) {
				idsByType.computeIfAbsent(change.type, type -> new ArrayList<>()).add(change.id);
			}
		}
		Map<String, JSONObject> result = new HashMap<>();
		for (Map.Entry<String, List<Long>> ids : idsByType.entrySet()) {
			result.putAll(api.fetch(ids.getKey(), ids.getValue()));
		}
		return result;
	}
}
