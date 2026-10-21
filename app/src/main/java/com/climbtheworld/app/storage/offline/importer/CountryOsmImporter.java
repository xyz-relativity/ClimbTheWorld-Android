package com.climbtheworld.app.storage.offline.importer;

import android.os.CancellationSignal;
import android.util.JsonReader;
import android.util.JsonToken;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.DownloadedCountry;
import com.climbtheworld.app.storage.database.EntityCountry;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Imports one streamed Overpass country response into the offline database. */
public class CountryOsmImporter {
	private static final String KEY_REMARK = "remark";
	private static final String RUNTIME_ERROR_PREFIX = "runtime error";

	private final AppDatabase database;

	public CountryOsmImporter(AppDatabase database) {
		this.database = database;
	}

	/**
	 * Replaces one country's source membership as one transaction. Only the climbing features of
	 * the response and the elements they are drawn with are kept, see {@link ClimbingElementFilter}.
	 * Entities retained by another country or changed locally remain in the database.
	 */
	public long importResponse(Reader response, String countryIso) throws IOException, JSONException {
		return importResponse(response, countryIso, () -> {
		});
	}

	/**
	 * @param beforeWrite runs once the response has been read and validated, before the database
	 *                    transaction starts.
	 */
	public long importResponse(Reader response, String countryIso, Runnable beforeWrite)
			throws IOException, JSONException {
		return importResponse(response, countryIso, beforeWrite, new CancellationSignal());
	}

	/**
	 * @param cancellation checked inside the write transaction, so a country deleted while this
	 *                     import runs is either written before the delete or not written at all.
	 * @throws android.os.OperationCanceledException if cancelled before the data is written.
	 */
	public long importResponse(Reader response, String countryIso, Runnable beforeWrite,
	                           CancellationSignal cancellation) throws IOException, JSONException {
		String normalizedCountryIso = normalizeCountryIso(countryIso);
		Map<Long, OsmNode> nodes = new HashMap<>();
		Map<String, OsmCollectionEntity> collections = new HashMap<>();

		readElements(response, nodes, collections);
		// Before the local changes stand in for the downloaded elements, so those changes are
		// kept whatever their tags.
		ClimbingElementFilter.retain(nodes.values(), collections.values());
		List<EntityCountry> membershipsToWrite = new ArrayList<>();
		for (OsmNode node : nodes.values()) {
			membershipsToWrite.add(new EntityCountry(node.osmType, node.osmID, normalizedCountryIso));
		}
		for (OsmCollectionEntity collection : collections.values()) {
			membershipsToWrite.add(new EntityCountry(collection.osmType, collection.osmID,
					normalizedCountryIso));
		}
		List<OsmNode> dirtyNodes = database.osmNodeDao().loadDirty();
		List<OsmCollectionEntity> dirtyCollections = database.osmCollectionDao().loadDirty();
		replaceWithDirtyLocalEntities(nodes, collections, dirtyNodes, dirtyCollections);
		computeCollectionCaches(nodes, collections, dirtyNodes, dirtyCollections);

		List<OsmNode> nodesToWrite = new ArrayList<>(nodes.values());
		List<OsmCollectionEntity> collectionsToWrite = new ArrayList<>(collections.values());
		beforeWrite.run();
		database.runInTransaction(() -> {
			cancellation.throwIfCanceled();
			database.entityCountryDao().deleteCleanForCountry(normalizedCountryIso);
			database.osmNodeDao().insertNodesWithReplace(nodesToWrite);
			database.osmCollectionDao().insertCollectionWithReplace(collectionsToWrite);
			database.entityCountryDao().insertAll(membershipsToWrite);
			database.osmNodeDao().deleteUnownedCleanNodes();
			database.osmCollectionDao().deleteUnownedCleanCollections();
			database.downloadedCountryDao().upsert(new DownloadedCountry(normalizedCountryIso,
					System.currentTimeMillis(), membershipsToWrite.size()));
		});
		return membershipsToWrite.size();
	}

	private void readElements(Reader response, Map<Long, OsmNode> nodes,
	                          Map<String, OsmCollectionEntity> collections)
			throws IOException, JSONException {
		String remark = null;
		try (JsonReader reader = new JsonReader(response)) {
			reader.beginObject();
			while (reader.hasNext()) {
				String name = reader.nextName();
				if (KEY_REMARK.equals(name) && reader.peek() == JsonToken.STRING) {
					remark = reader.nextString();
					continue;
				}
				if (!"elements".equals(name)) {
					reader.skipValue();
					continue;
				}
				reader.beginArray();
				while (reader.hasNext()) {
					JSONObject element = readElement(reader);
					importElement(element, nodes, collections);
				}
				reader.endArray();
			}
			reader.endObject();
		}
		// Overpass reports timeouts and memory exhaustion as a remark on an HTTP 200 response
		// whose elements are partial, so importing it would delete the country's missing data.
		if (remark != null && remark.contains(RUNTIME_ERROR_PREFIX)) {
			throw new OverpassServerException("Overpass returned an incomplete response: " + remark);
		}
	}

	private void importElement(JSONObject element, Map<Long, OsmNode> nodes,
	                           Map<String, OsmCollectionEntity> collections) {
		OsmEntity.EntityOsmType type;
		try {
			type = OsmEntity.EntityOsmType.valueOf(element.getString(ClimbingTags.KEY_TYPE));
		} catch (JSONException | IllegalArgumentException exception) {
			return;
		}
		long osmId = element.optLong(ClimbingTags.KEY_ID, 0);
		if (osmId == 0) {
			return;
		}

		if (type == OsmEntity.EntityOsmType.node) {
			nodes.put(osmId, new OsmNode(element));
		} else {
			OsmCollectionEntity collection = new OsmCollectionEntity(element);
			collections.put(entityKey(type, osmId), collection);
		}
	}

	private void replaceWithDirtyLocalEntities(Map<Long, OsmNode> nodes,
	                                           Map<String, OsmCollectionEntity> collections,
	                                           List<OsmNode> dirtyNodes,
	                                           List<OsmCollectionEntity> dirtyCollections) {
		for (OsmNode dirtyNode : dirtyNodes) {
			if (nodes.containsKey(dirtyNode.osmID)) {
				nodes.put(dirtyNode.osmID, dirtyNode);
			}
		}
		for (OsmCollectionEntity dirtyCollection : dirtyCollections) {
			String key = entityKey(dirtyCollection.osmType, dirtyCollection.osmID);
			if (collections.containsKey(key)) {
				collections.put(key, dirtyCollection);
			}
		}
	}

	/**
	 * The local changes are looked up too, as a changed relation may list entities created in
	 * the app, which no download has.
	 */
	private void computeCollectionCaches(Map<Long, OsmNode> nodes,
	                                     Map<String, OsmCollectionEntity> collections,
	                                     List<OsmNode> dirtyNodes,
	                                     List<OsmCollectionEntity> dirtyCollections) {
		Map<Long, OsmNode> knownNodes = new HashMap<>(nodes);
		for (OsmNode dirtyNode : dirtyNodes) {
			knownNodes.putIfAbsent(dirtyNode.osmID, dirtyNode);
		}
		Map<String, OsmCollectionEntity> knownCollections = new HashMap<>(collections);
		for (OsmCollectionEntity dirtyCollection : dirtyCollections) {
			knownCollections.putIfAbsent(entityKey(dirtyCollection.osmType, dirtyCollection.osmID),
					dirtyCollection);
		}

		for (OsmCollectionEntity collection : collections.values()) {
			List<Long> nodeIds = resolveNodeIds(collection, knownNodes, knownCollections,
					new HashSet<>());
			collection.osmNodes = nodeIds;
			List<OsmNode> resolvedNodes = new ArrayList<>();
			for (Long nodeId : nodeIds) {
				OsmNode node = knownNodes.get(nodeId);
				if (node != null) {
					resolvedNodes.add(node);
				}
			}
			collection.computeCache(resolvedNodes);
		}
	}

	private List<Long> resolveNodeIds(OsmCollectionEntity collection, Map<Long, OsmNode> nodes,
	                                  Map<String, OsmCollectionEntity> collections,
	                                  Set<String> visited) {
		String collectionKey = entityKey(collection.osmType, collection.osmID);
		if (!visited.add(collectionKey)) {
			return new ArrayList<>();
		}
		if (collection.osmType == OsmEntity.EntityOsmType.way) {
			return new ArrayList<>(collection.osmNodes);
		}

		List<Long> nodeIds = new ArrayList<>();
		for (JSONObject member : RelationMembers.of(collection.jsonNodeInfo)) {
			long memberId = RelationMembers.ref(member);
			OsmEntity.EntityOsmType memberType = RelationMembers.osmType(member);
			if (memberId == 0) {
				continue;
			}
			if (memberType == OsmEntity.EntityOsmType.node) {
				if (nodes.containsKey(memberId)) {
					nodeIds.add(memberId);
				}
				continue;
			}
			if (memberType == null) {
				// An Overpass member type the app has no use for.
				continue;
			}
			OsmCollectionEntity memberCollection = collections.get(entityKey(memberType, memberId));
			if (memberCollection != null) {
				nodeIds.addAll(resolveNodeIds(memberCollection, nodes, collections, visited));
			}
		}
		return nodeIds;
	}

	private JSONObject readElement(JsonReader reader) throws IOException, JSONException {
		JSONObject element = new JSONObject();
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			switch (name) {
				case ClimbingTags.KEY_ID:
					element.put(name, reader.nextLong());
					break;
				case ClimbingTags.KEY_TYPE:
					element.put(name, reader.nextString());
					break;
				case ClimbingTags.KEY_LAT:
				case ClimbingTags.KEY_LON:
					element.put(name, reader.nextDouble());
					break;
				case ClimbingTags.KEY_TAGS:
					element.put(name, readObject(reader));
					break;
				case ClimbingTags.KEY_NODES:
					element.put(name, readLongArray(reader));
					break;
				case ClimbingTags.KEY_MEMBERS:
					element.put(name, readMembers(reader));
					break;
				case ClimbingTags.KEY_CENTER:
					element.put(name, readCenter(reader));
					break;
				default:
					reader.skipValue();
			}
		}
		reader.endObject();
		return element;
	}

	private JSONObject readObject(JsonReader reader) throws IOException, JSONException {
		JSONObject result = new JSONObject();
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (reader.peek() == JsonToken.NULL) {
				reader.nextNull();
				continue;
			}
			result.put(name, reader.nextString());
		}
		reader.endObject();
		return result;
	}

	private JSONArray readLongArray(JsonReader reader) throws IOException, JSONException {
		JSONArray result = new JSONArray();
		reader.beginArray();
		while (reader.hasNext()) {
			result.put(reader.nextLong());
		}
		reader.endArray();
		return result;
	}

	/** The middle of a way or relation, which places a way whose nodes are not downloaded. */
	private JSONObject readCenter(JsonReader reader) throws IOException, JSONException {
		JSONObject result = new JSONObject();
		reader.beginObject();
		while (reader.hasNext()) {
			String name = reader.nextName();
			if (ClimbingTags.KEY_LAT.equals(name) || ClimbingTags.KEY_LON.equals(name)) {
				result.put(name, reader.nextDouble());
			} else {
				reader.skipValue();
			}
		}
		reader.endObject();
		return result;
	}

	private JSONArray readMembers(JsonReader reader) throws IOException, JSONException {
		JSONArray result = new JSONArray();
		reader.beginArray();
		while (reader.hasNext()) {
			result.put(readObject(reader));
		}
		reader.endArray();
		return result;
	}

	private String normalizeCountryIso(String countryIso) {
		if (countryIso == null || countryIso.trim().isEmpty()) {
			throw new IllegalArgumentException("countryIso is required");
		}
		return countryIso.trim().toUpperCase(Locale.ROOT);
	}

	private static String entityKey(OsmEntity.EntityOsmType type, long osmId) {
		return type.name() + "|" + osmId;
	}
}
