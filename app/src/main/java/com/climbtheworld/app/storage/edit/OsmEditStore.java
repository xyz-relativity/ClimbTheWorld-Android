package com.climbtheworld.app.storage.edit;

import android.content.Context;
import android.util.Log;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.EntityCountry;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityState;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.offline.CountryBoxes;
import com.climbtheworld.app.storage.upload.DiffResult;
import com.climbtheworld.app.storage.upload.OsmElements;
import com.climbtheworld.app.storage.upload.PendingChange;
import com.climbtheworld.app.storage.upload.ThreeWayMerge;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every change made in the app to the downloaded OSM data goes through here. Changed entities stay
 * in the downloaded tables, so the map, AR and search show them right away, marked for upload and
 * holding the downloaded version they started from (baseJson) for reverting.
 *
 * <p>Entities created in the app get negative placeholder ids until they are uploaded. Relations
 * keep their cached nodes and bounding box current as members change or move, and a deleted
 * element is taken out of the relations listing it, as OSM refuses to delete a member.
 *
 * <p>Calls block on the database, so they run off the UI thread.
 */
public class OsmEditStore {
	private static final String TAG = OsmEditStore.class.getSimpleName();
	/** SQLite takes at most 999 bound values per statement on the oldest supported Android. */
	private static final int QUERY_CHUNK_SIZE = 500;
	/** The most relations listed to choose from, as from a map zoomed out over a whole country. */
	private static final int LISTED_RELATIONS_LIMIT = 50;

	private static final Object PLACEHOLDER_LOCK = new Object();
	private static long lastPlaceholderId = 0;
	private static CountryBoxes appCountryBoxes;

	private final AppDatabase database;
	private final Context context;
	private CountryBoxes countryBoxes;

	public OsmEditStore(Context context) {
		this.database = AppDatabase.getInstance(context);
		this.context = context.getApplicationContext();
	}

	public OsmEditStore(AppDatabase database, CountryBoxes countryBoxes) {
		this.database = database;
		this.context = null;
		this.countryBoxes = countryBoxes;
	}

	/** An id for an entity created in the app, unique among the local entities of any type. */
	public long newPlaceholderId() {
		synchronized (PLACEHOLDER_LOCK) {
			long smallest = Math.min(lastPlaceholderId, Math.min(
					database.osmNodeDao().minId(), database.osmCollectionDao().minId()));
			lastPlaceholderId = Math.min(smallest, 0) - 1;
			return lastPlaceholderId;
		}
	}

	/** A node to fill in and save, not stored yet. */
	public OsmNode newNode(double latitude, double longitude) {
		JSONObject json = newElementJson(EntityOsmType.node);
		try {
			json.put(ClimbingTags.KEY_LAT, latitude);
			json.put(ClimbingTags.KEY_LON, longitude);
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return new OsmNode(json);
	}

	/** A relation without members to fill in and save, not stored yet. */
	public OsmCollectionEntity newRelation() {
		JSONObject json = newElementJson(EntityOsmType.relation);
		try {
			json.put(ClimbingTags.KEY_MEMBERS, new JSONArray());
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return new OsmCollectionEntity(json);
	}

	public void saveNode(OsmNode node) {
		save(Collections.singletonList(node), Collections.emptyList());
	}

	public void saveRelation(OsmCollectionEntity relation) {
		save(Collections.emptyList(), Collections.singletonList(relation));
	}

	/**
	 * Saves edited or new entities together, like a new route and the crag it was added to. Nodes
	 * go first, so the relations' bounding boxes include them. An entity edited back to its
	 * downloaded version is clean again, and a relation left without members is deleted.
	 */
	public void save(List<OsmNode> nodes, List<OsmCollectionEntity> relations) {
		long now = System.currentTimeMillis();
		database.runInTransaction(() -> {
			for (OsmNode node : nodes) {
				writeNode(node, now);
			}
			for (OsmCollectionEntity relation : relations) {
				writeRelation(relation, now);
			}
		});
	}

	/**
	 * Deletes the node and takes it out of the relations listing it; a relation left without
	 * members is deleted too.
	 *
	 * @return false, changing nothing, if the node is part of a way, which the app can't edit.
	 */
	public boolean deleteNode(long osmId) {
		long now = System.currentTimeMillis();
		return database.runInTransaction(() -> {
			OsmNode stored = database.osmNodeDao().find(EntityOsmType.node, osmId);
			if (stored == null) {
				return true;
			}
			if (isUsedByWay(osmId)) {
				return false;
			}
			removeFromParents(EntityOsmType.node, osmId, now);
			if (stored.isNew()) {
				forget(stored);
			} else {
				markDeleted(stored, now);
				write(stored);
			}
			return true;
		});
	}

	/** Deletes the relation, keeping its members, and takes it out of its parent relations. */
	public void deleteRelation(long osmId) {
		long now = System.currentTimeMillis();
		database.runInTransaction(() -> {
			OsmCollectionEntity stored = database.osmCollectionDao().find(EntityOsmType.relation, osmId);
			if (stored != null) {
				deleteRelation(stored, now);
			}
		});
	}

	/** @return whether a way lists the node, which then can't be deleted in the app. */
	public boolean isUsedByWay(long nodeId) {
		for (OsmCollectionEntity collection : database.osmCollectionDao().findContainingNode(nodeId)) {
			if (collection.osmType == EntityOsmType.way && collection.osmNodes.contains(nodeId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Drops the local changes of one entity. A new entity is removed, and with it its place in
	 * the relations listing it; any other goes back to its downloaded version.
	 */
	public void revert(EntityOsmType osmType, long osmId) {
		long now = System.currentTimeMillis();
		database.runInTransaction(() -> {
			OsmEntity stored = find(osmType, osmId);
			if (stored == null || stored.localUpdateState == EntityState.clean) {
				return;
			}
			if (stored.isNew()) {
				removeFromParents(osmType, osmId, now);
				forget(stored);
				return;
			}
			if (stored.baseJson == null) {
				Log.w(TAG, "No downloaded version to revert " + osmType + " " + osmId + " to");
				stored.localUpdateState = EntityState.clean;
				write(stored);
				return;
			}
			replaceWith(stored, stored.baseJson);
		});
	}

	/**
	 * Stores the element as given, clean, in place of the stored one, keeping the cached boxes
	 * of the relations around it right.
	 */
	private void replaceWith(OsmEntity stored, JSONObject json) {
		OsmEntity updated = fromJson(json);
		updated.localEditDate = stored.localEditDate;
		if (updated instanceof OsmNode) {
			OsmNode updatedNode = (OsmNode) updated;
			updatedNode.countryIso = ((OsmNode) stored).countryIso;
			write(updatedNode);
			if (moved((OsmNode) stored, updatedNode)) {
				refreshCollectionsContaining(updatedNode.osmID);
			}
		} else {
			OsmCollectionEntity updatedCollection = (OsmCollectionEntity) updated;
			refreshCache(updatedCollection);
			write(updatedCollection);
			refreshAncestors(updatedCollection);
		}
	}

	/** The changes waiting for upload, most recently changed first. */
	public List<PendingChange> loadPendingChanges() {
		List<PendingChange> result = new ArrayList<>();
		for (OsmEntity entity : loadChanges()) {
			PendingChange.Action action = entity.localUpdateState == EntityState.toDelete
					? PendingChange.Action.delete
					: entity.isNew() ? PendingChange.Action.create : PendingChange.Action.modify;
			result.add(new PendingChange(entity.osmType.name(), entity.osmID, action,
					OsmElements.storable(entity.jsonNodeInfo),
					entity.baseJson == null ? null : OsmElements.storable(entity.baseJson)));
		}
		return result;
	}

	/**
	 * Records what OSM took from an upload: created elements take the ids OSM gave them, also
	 * where relations list them, each element becomes the version sent, and deleted ones go.
	 */
	public void applyUploaded(List<PendingChange> uploaded, DiffResult result) {
		database.runInTransaction(() -> {
			// The ids first, so that the versions sent can point at them.
			for (PendingChange change : uploaded) {
				long newId = result.newId(change.type, change.id);
				if (change.action == PendingChange.Action.create && newId != change.id) {
					OsmEntity stored = find(EntityOsmType.valueOf(change.type), change.id);
					if (stored != null) {
						changeId(stored, newId);
						write(stored);
					}
				}
			}
			for (PendingChange change : uploaded) {
				if (!result.contains(change.type, change.id)) {
					continue;
				}
				OsmEntity stored = find(EntityOsmType.valueOf(change.type),
						result.newId(change.type, change.id));
				if (stored == null) {
					continue;
				}
				if (change.action == PendingChange.Action.delete) {
					forget(stored);
					continue;
				}
				JSONObject sent = OsmElements.storable(change.upload);
				result.remap(sent);
				replaceWith(stored, sent);
			}
		});
	}

	/**
	 * Drops the local change of an element for its current version on OSM; one deleted there
	 * goes.
	 */
	public void useServerVersion(EntityOsmType osmType, long osmId, JSONObject server) {
		database.runInTransaction(() -> {
			OsmEntity stored = find(osmType, osmId);
			if (stored == null) {
				return;
			}
			if (ThreeWayMerge.isDeleted(server)) {
				forget(stored);
				return;
			}
			replaceWith(stored, OsmElements.storable(server));
		});
	}

	/** The stored entity, or null. */
	public OsmEntity load(EntityOsmType osmType, long osmId) {
		return find(osmType, osmId);
	}

	/**
	 * The stored entities a members array lists, by RelationMembers.key; members that are not
	 * stored, like ones outside the downloaded countries, are left out.
	 */
	public Map<String, OsmEntity> loadMembers(JSONArray members) {
		Map<String, OsmEntity> result = new HashMap<>();
		List<Long> nodeIds = new ArrayList<>();
		for (JSONObject member : RelationMembers.list(members)) {
			long ref = RelationMembers.ref(member);
			EntityOsmType memberType = RelationMembers.osmType(member);
			if (memberType == null) {
				continue;
			}
			if (memberType == EntityOsmType.node) {
				nodeIds.add(ref);
				continue;
			}
			OsmEntity collection = find(memberType, ref);
			if (collection != null) {
				result.put(RelationMembers.key(memberType.name(), ref), collection);
			}
		}
		for (OsmNode node : loadNodes(nodeIds)) {
			result.put(RelationMembers.key(EntityOsmType.node.name(), node.osmID), node);
		}
		return result;
	}

	/** The relations listing the entity, leaving out deleted ones. */
	public List<OsmCollectionEntity> loadParents(EntityOsmType osmType, long osmId) {
		List<OsmCollectionEntity> result = new ArrayList<>();
		for (OsmCollectionEntity parent : parentsOf(osmType, osmId)) {
			if (parent.localUpdateState != EntityState.toDelete) {
				result.add(parent);
			}
		}
		return result;
	}

	/** Relations of a climbing type within reach of the point, as loadRelationsIn lists them. */
	public List<OsmCollectionEntity> loadNearbyRelations(double latitude, double longitude,
	                                                     double radiusMeters,
	                                                     EntityClimbingType climbingType) {
		return loadRelationsIn(Collections.singletonList(DataManager.computeBoundingBox(
						new Vector4d(latitude, longitude, 0, 0), radiusMeters)),
				latitude, longitude, climbingType);
	}

	/**
	 * Relations of a climbing type overlapping any of the bounds, like the part of a map on screen
	 * and the surroundings of the point: first the ones changed in the app, most recently first, as
	 * those are likely the ones being worked on, then the nearest to the point. The first
	 * LISTED_RELATIONS_LIMIT of them.
	 */
	public List<OsmCollectionEntity> loadRelationsIn(List<MapBounds> boundsList, double latitude,
	                                                 double longitude,
	                                                 EntityClimbingType climbingType) {
		Map<Long, OsmCollectionEntity> found = new HashMap<>();
		for (MapBounds bounds : boundsList) {
			// The query compares longitudes as numbers, so it looks on either side of the
			// antimeridian.
			for (MapBounds part : bounds.splitAtAntimeridian()) {
				for (OsmCollectionEntity relation : database.osmCollectionDao().loadRelationsInBBox(
						part.getNorth(), part.getEast(), part.getSouth(), part.getWest(),
						climbingType, latitude, longitude, LISTED_RELATIONS_LIMIT)) {
					found.put(relation.osmID, relation);
				}
			}
		}
		List<OsmCollectionEntity> result = new ArrayList<>(found.values());
		sortChangedThenNearest(result, latitude, longitude);
		return result.size() > LISTED_RELATIONS_LIMIT
				? new ArrayList<>(result.subList(0, LISTED_RELATIONS_LIMIT)) : result;
	}

	/**
	 * Relations of a climbing type whose name holds the text, at any distance from the point, in
	 * the order of loadRelationsIn. The first LISTED_RELATIONS_LIMIT of them.
	 */
	public List<OsmCollectionEntity> findRelations(String name, double latitude, double longitude,
	                                               EntityClimbingType climbingType) {
		List<OsmCollectionEntity> result = database.osmCollectionDao().findRelations(name,
				climbingType, latitude, longitude, LISTED_RELATIONS_LIMIT);
		sortChangedThenNearest(result, latitude, longitude);
		return result;
	}

	/**
	 * The ones changed in the app first, most recently first, then the nearest: the database's
	 * order, with the distances worked out exactly.
	 */
	private static void sortChangedThenNearest(List<OsmCollectionEntity> collections,
	                                           double latitude, double longitude) {
		Map<OsmCollectionEntity, Double> distances = new HashMap<>();
		for (OsmCollectionEntity collection : collections) {
			distances.put(collection, distanceMeters(latitude, longitude, collection));
		}
		collections.sort(Comparator.<OsmCollectionEntity>comparingLong(
						collection -> -collection.localEditDate)
				.thenComparing(distances::get));
	}

	/** From the point to the middle of the collection's bounding box. */
	public static double distanceMeters(double latitude, double longitude,
	                                    OsmCollectionEntity collection) {
		MapCoordinate center = DataManager.collectionCenter(collection);
		// The short way round, across the antimeridian when that is shorter.
		double longitudeDelta = center.getLongitude() - longitude;
		longitudeDelta -= 360 * Math.round(longitudeDelta / 360);
		double x = Math.toRadians(longitudeDelta)
				* Math.cos(Math.toRadians((center.getLatitude() + latitude) / 2));
		double y = Math.toRadians(center.getLatitude() - latitude);
		return Math.sqrt(x * x + y * y) * GeoUtils.EARTH_RADIUS_M;
	}

	/** Changed entities, most recently changed first. */
	public List<OsmEntity> loadChanges() {
		List<OsmEntity> result = new ArrayList<>();
		result.addAll(database.osmNodeDao().loadDirty());
		result.addAll(database.osmCollectionDao().loadDirty());
		result.sort((first, second) -> Long.compare(second.localEditDate, first.localEditDate));
		return result;
	}

	public boolean hasChanges() {
		return database.osmNodeDao().countDirty() + database.osmCollectionDao().countDirty() > 0;
	}

	private void writeNode(OsmNode node, long now) {
		OsmNode stored = database.osmNodeDao().find(EntityOsmType.node, node.osmID);
		requireStoredUnlessNew(node, stored);
		markChanged(node, stored, now);
		write(node);
		if (stored != null && moved(stored, node)) {
			refreshCollectionsContaining(node.osmID);
		}
		if (node.isNew()) {
			assignCountries(node, node.decimalLatitude, node.decimalLongitude);
		}
	}

	private void writeRelation(OsmCollectionEntity relation, long now) {
		if (relation.osmType != EntityOsmType.relation) {
			throw new IllegalArgumentException("Ways can't be edited, " + relation.osmID);
		}
		OsmCollectionEntity stored = database.osmCollectionDao().find(EntityOsmType.relation,
				relation.osmID);
		if (relation.getMembers().length() == 0) {
			// OSM counts an empty relation as broken.
			if (stored != null) {
				deleteRelation(stored, now);
			}
			return;
		}
		requireStoredUnlessNew(relation, stored);
		relation.refreshMembersList();
		markChanged(relation, stored, now);
		refreshCache(relation);
		write(relation);
		refreshAncestors(relation);
		if (relation.isNew() && !isWithoutLocation(relation)) {
			MapCoordinate center = DataManager.collectionCenter(relation);
			assignCountries(relation, center.getLatitude(), center.getLongitude());
		}
	}

	private void deleteRelation(OsmCollectionEntity relation, long now) {
		removeFromParents(EntityOsmType.relation, relation.osmID, now);
		if (relation.isNew()) {
			forget(relation);
		} else {
			markDeleted(relation, now);
			write(relation);
		}
	}

	/** Takes the entity out of every relation listing it, deleting relations left empty. */
	private void removeFromParents(EntityOsmType osmType, long osmId, long now) {
		for (OsmCollectionEntity parent : parentsOf(osmType, osmId)) {
			if (parent.localUpdateState == EntityState.toDelete) {
				continue;
			}
			RelationMembers.removeAll(parent.getMembers(), osmType.name(), osmId);
			if (parent.getMembers().length() == 0) {
				// The stored version, as the members of a deleted relation don't matter.
				deleteRelation(database.osmCollectionDao().find(EntityOsmType.relation,
						parent.osmID), now);
			} else {
				writeRelation(parent, now);
			}
		}
	}

	private List<OsmCollectionEntity> parentsOf(EntityOsmType osmType, long osmId) {
		List<OsmCollectionEntity> result = new ArrayList<>();
		for (OsmCollectionEntity relation : database.osmCollectionDao().findRelationsWithMemberId(osmId)) {
			if (RelationMembers.contains(relation.getMembers(), osmType.name(), osmId)) {
				result.add(relation);
			}
		}
		return result;
	}

	private static void requireStoredUnlessNew(OsmEntity entity, OsmEntity stored) {
		if (stored == null && !entity.isNew()) {
			throw new IllegalArgumentException(entity.osmType + " " + entity.osmID
					+ " is not in the local data");
		}
	}

	private static void markChanged(OsmEntity entity, OsmEntity stored, long now) {
		entity.refreshClimbingType();
		entity.localEditDate = now;
		entity.baseJson = baseOf(stored);
		entity.localUpdateState = EntityState.toUpdate;
		if (entity.baseJson != null && OsmContent.sameContent(entity.jsonNodeInfo, entity.baseJson)) {
			entity.localUpdateState = EntityState.clean;
			entity.baseJson = null;
		}
	}

	private static void markDeleted(OsmEntity stored, long now) {
		stored.baseJson = baseOf(stored);
		stored.localEditDate = now;
		stored.localUpdateState = EntityState.toDelete;
	}

	/** The downloaded version the stored entity's changes started from, null for a new one. */
	private static JSONObject baseOf(OsmEntity stored) {
		if (stored == null) {
			return null;
		}
		if (stored.localUpdateState == EntityState.clean) {
			return OsmElements.copy(stored.jsonNodeInfo);
		}
		return stored.baseJson;
	}

	private static boolean moved(OsmNode before, OsmNode after) {
		return Double.compare(before.decimalLatitude, after.decimalLatitude) != 0
				|| Double.compare(before.decimalLongitude, after.decimalLongitude) != 0;
	}

	/** Recomputes the cached nodes and boxes of the ways and relations around a moved node. */
	private void refreshCollectionsContaining(long nodeId) {
		for (OsmCollectionEntity collection : database.osmCollectionDao().findContainingNode(nodeId)) {
			refreshCache(collection);
			write(collection);
		}
	}

	/** Recomputes the cached nodes and boxes of the relations holding this one, up to the top. */
	private void refreshAncestors(OsmEntity entity) {
		Set<String> visited = new HashSet<>();
		visited.add(DataManager.collectionKey(entity.osmType, entity.osmID));
		LinkedList<OsmEntity> pending = new LinkedList<>();
		pending.add(entity);
		while (!pending.isEmpty()) {
			OsmEntity child = pending.removeFirst();
			for (OsmCollectionEntity parent : parentsOf(child.osmType, child.osmID)) {
				if (!visited.add(DataManager.collectionKey(parent.osmType, parent.osmID))) {
					continue;
				}
				refreshCache(parent);
				write(parent);
				pending.add(parent);
			}
		}
	}

	/** Same as the importer's cache: the nodes of all nested members, and their bounding box. */
	private void refreshCache(OsmCollectionEntity collection) {
		List<Long> candidates = resolveNodeIds(collection, new HashSet<>());
		List<OsmNode> nodes = loadNodes(candidates);
		Set<Long> found = new HashSet<>();
		for (OsmNode node : nodes) {
			found.add(node.osmID);
		}
		List<Long> nodeIds = new LinkedList<>();
		for (Long candidate : candidates) {
			if (collection.osmType == EntityOsmType.way || found.contains(candidate)) {
				nodeIds.add(candidate);
			}
		}
		collection.osmNodes = nodeIds;
		collection.computeCache(nodes);
	}

	private List<Long> resolveNodeIds(OsmCollectionEntity collection, Set<String> visited) {
		if (!visited.add(DataManager.collectionKey(collection.osmType, collection.osmID))) {
			return new ArrayList<>();
		}
		if (collection.osmType == EntityOsmType.way) {
			JSONArray wayNodes = collection.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_NODES);
			List<Long> result = new ArrayList<>();
			for (int index = 0; wayNodes != null && index < wayNodes.length(); index++) {
				result.add(wayNodes.optLong(index));
			}
			return result;
		}

		List<Long> result = new ArrayList<>();
		for (JSONObject member : RelationMembers.of(collection.jsonNodeInfo)) {
			long ref = RelationMembers.ref(member);
			EntityOsmType memberType = RelationMembers.osmType(member);
			if (memberType == null) {
				continue;
			}
			if (memberType == EntityOsmType.node) {
				result.add(ref);
				continue;
			}
			OsmCollectionEntity memberCollection = database.osmCollectionDao().find(memberType, ref);
			if (memberCollection != null) {
				result.addAll(resolveNodeIds(memberCollection, visited));
			}
		}
		return result;
	}

	/** The stored nodes of these ids; missing ones are left out. */
	public List<OsmNode> loadNodes(List<Long> ids) {
		List<Long> unique = new ArrayList<>(new HashSet<>(ids));
		List<OsmNode> result = new ArrayList<>();
		for (int start = 0; start < unique.size(); start += QUERY_CHUNK_SIZE) {
			result.addAll(database.osmNodeDao().resolveNodeData(
					unique.subList(start, Math.min(unique.size(), start + QUERY_CHUNK_SIZE))));
		}
		return result;
	}

	private static boolean isWithoutLocation(OsmCollectionEntity collection) {
		return collection.bBoxNorth == 0 && collection.bBoxSouth == 0
				&& collection.bBoxEast == 0 && collection.bBoxWest == 0;
	}

	/**
	 * Links a new entity to the countries around it, so that it is kept, like the downloaded data,
	 * while one of them is downloaded. Downloaded countries are preferred.
	 */
	private void assignCountries(OsmEntity entity, double latitude, double longitude) {
		if (!database.entityCountryDao().findCountries(entity.osmType, entity.osmID).isEmpty()) {
			return;
		}
		List<String> containing = countryBoxes().containing(latitude, longitude);
		if (containing.isEmpty()) {
			return;
		}
		List<String> downloaded = database.downloadedCountryDao().loadCountryIsos();
		List<EntityCountry> links = new ArrayList<>();
		for (String countryIso : containing) {
			if (downloaded.contains(countryIso)) {
				links.add(new EntityCountry(entity.osmType, entity.osmID, countryIso));
			}
		}
		if (links.isEmpty()) {
			links.add(new EntityCountry(entity.osmType, entity.osmID, containing.get(0)));
		}
		database.entityCountryDao().insertAll(links);
	}

	private void changeId(OsmEntity stored, long serverId) {
		long localId = stored.osmID;
		List<String> countries = database.entityCountryDao().findCountries(stored.osmType, localId);
		forget(stored);
		stored.osmID = serverId;
		try {
			stored.jsonNodeInfo.put(ClimbingTags.KEY_ID, serverId);
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		List<EntityCountry> links = new ArrayList<>();
		for (String countryIso : countries) {
			links.add(new EntityCountry(stored.osmType, serverId, countryIso));
		}
		database.entityCountryDao().insertAll(links);

		// A relation listing a new entity is changed itself, so its base has no placeholder ids.
		for (OsmCollectionEntity parent : parentsOf(stored.osmType, localId)) {
			RelationMembers.replaceRef(parent.getMembers(), stored.osmType.name(), localId, serverId);
			parent.refreshMembersList();
			write(parent);
		}
		if (stored.osmType == EntityOsmType.node) {
			for (OsmCollectionEntity collection : database.osmCollectionDao().findContainingNode(localId)) {
				List<Long> nodeIds = new LinkedList<>();
				for (Long nodeId : collection.osmNodes) {
					nodeIds.add(nodeId == localId ? serverId : nodeId);
				}
				collection.osmNodes = nodeIds;
				write(collection);
			}
		}
	}

	private OsmEntity find(EntityOsmType osmType, long osmId) {
		if (osmType == EntityOsmType.node) {
			return database.osmNodeDao().find(osmType, osmId);
		}
		return database.osmCollectionDao().find(osmType, osmId);
	}

	private void write(OsmEntity entity) {
		if (entity instanceof OsmNode) {
			database.osmNodeDao().insertNodesWithReplace(Collections.singletonList((OsmNode) entity));
		} else {
			database.osmCollectionDao().insertCollectionWithReplace(
					Collections.singletonList((OsmCollectionEntity) entity));
		}
	}

	/** Removes the entity and its country links. */
	private void forget(OsmEntity entity) {
		if (entity instanceof OsmNode) {
			database.osmNodeDao().delete(entity.osmType, entity.osmID);
		} else {
			database.osmCollectionDao().delete(entity.osmType, entity.osmID);
		}
		database.entityCountryDao().deleteForEntity(entity.osmType, entity.osmID);
	}

	private static OsmEntity fromJson(JSONObject json) {
		JSONObject restored = OsmElements.copy(json);
		if (EntityOsmType.node.name().equals(restored.optString(ClimbingTags.KEY_TYPE))) {
			return new OsmNode(restored);
		}
		return new OsmCollectionEntity(restored);
	}

	private JSONObject newElementJson(EntityOsmType osmType) {
		JSONObject json = new JSONObject();
		try {
			json.put(ClimbingTags.KEY_TYPE, osmType.name());
			json.put(ClimbingTags.KEY_ID, newPlaceholderId());
			json.put(ClimbingTags.KEY_TAGS, new JSONObject());
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return json;
	}

	private CountryBoxes countryBoxes() {
		if (countryBoxes == null) {
			countryBoxes = loadAppCountryBoxes(context);
		}
		return countryBoxes;
	}

	private static synchronized CountryBoxes loadAppCountryBoxes(Context context) {
		if (appCountryBoxes == null) {
			try (Reader csv = new InputStreamReader(
					context.getResources().openRawResource(R.raw.country_bbox),
					StandardCharsets.UTF_8)) {
				appCountryBoxes = CountryBoxes.read(csv);
			} catch (IOException e) {
				Log.e(TAG, "Can't read the country boxes", e);
				try {
					return CountryBoxes.read(new StringReader(""));
				} catch (IOException impossible) {
					throw new IllegalStateException(impossible);
				}
			}
		}
		return appCountryBoxes;
	}
}
