package com.climbtheworld.app.storage.offline.importer;

import android.util.JsonReader;
import android.util.JsonToken;

import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.DownloadedCountry;
import com.climbtheworld.app.storage.database.EntityCountry;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
	 * Replaces one country's source membership as one transaction. Entities retained by another
	 * country or changed locally remain in the database.
	 */
	public long importResponse(Reader response, String countryIso) throws IOException, JSONException {
		String normalizedCountryIso = normalizeCountryIso(countryIso);
		Map<Long, OsmNode> nodes = new HashMap<>();
		Map<String, OsmCollectionEntity> collections = new HashMap<>();
		Map<String, EntityCountry> countryMembership = new LinkedHashMap<>();

		readElements(response, nodes, collections, countryMembership, normalizedCountryIso);
		replaceWithDirtyLocalEntities(nodes, collections);
		computeCollectionCaches(nodes, collections);

		List<OsmNode> nodesToWrite = new ArrayList<>(nodes.values());
		List<OsmCollectionEntity> collectionsToWrite = new ArrayList<>(collections.values());
		List<EntityCountry> membershipsToWrite = new ArrayList<>(countryMembership.values());
		database.runInTransaction(() -> {
			database.entityCountryDao().deleteForCountry(normalizedCountryIso);
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
	                          Map<String, OsmCollectionEntity> collections,
	                          Map<String, EntityCountry> countryMembership, String countryIso)
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
					importElement(element, nodes, collections, countryMembership, countryIso);
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
	                           Map<String, OsmCollectionEntity> collections,
	                           Map<String, EntityCountry> countryMembership, String countryIso) {
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
		EntityCountry membership = new EntityCountry(type, osmId, countryIso);
		countryMembership.put(entityKey(type, osmId), membership);
	}

	private void replaceWithDirtyLocalEntities(Map<Long, OsmNode> nodes,
	                                           Map<String, OsmCollectionEntity> collections) {
		for (OsmNode dirtyNode : database.osmNodeDao().loadDirty()) {
			if (nodes.containsKey(dirtyNode.osmID)) {
				nodes.put(dirtyNode.osmID, dirtyNode);
			}
		}
		for (OsmCollectionEntity dirtyCollection : database.osmCollectionDao().loadDirty()) {
			String key = entityKey(dirtyCollection.osmType, dirtyCollection.osmID);
			if (collections.containsKey(key)) {
				collections.put(key, dirtyCollection);
			}
		}
	}

	private void computeCollectionCaches(Map<Long, OsmNode> nodes,
	                                     Map<String, OsmCollectionEntity> collections) {
		for (OsmCollectionEntity collection : collections.values()) {
			List<Long> nodeIds = resolveNodeIds(collection, nodes, collections, new HashSet<>());
			collection.osmNodes = nodeIds;
			List<OsmNode> resolvedNodes = new ArrayList<>();
			for (Long nodeId : nodeIds) {
				OsmNode node = nodes.get(nodeId);
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
		JSONArray members = collection.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (members == null) {
			return nodeIds;
		}
		for (int index = 0; index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			long memberId = member.optLong(ClimbingTags.KEY_REF, 0);
			String memberTypeName = member.optString(ClimbingTags.KEY_TYPE);
			if (memberId == 0) {
				continue;
			}
			if (OsmEntity.EntityOsmType.node.name().equals(memberTypeName)) {
				if (nodes.containsKey(memberId)) {
					nodeIds.add(memberId);
				}
				continue;
			}
			try {
				OsmEntity.EntityOsmType memberType = OsmEntity.EntityOsmType.valueOf(memberTypeName);
				OsmCollectionEntity memberCollection = collections.get(entityKey(memberType, memberId));
				if (memberCollection != null) {
					nodeIds.addAll(resolveNodeIds(memberCollection, nodes, collections, visited));
				}
			} catch (IllegalArgumentException ignored) {
				// Ignore unsupported Overpass member types.
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
