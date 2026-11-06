package com.climbtheworld.app.storage;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A data manager for one load of a view, which reads each node, collection and bounding box
 * from the database once: the pins, the route relations, the route numbers and the climbing
 * geometry of a view are read from the same elements, several times over. It keeps what it
 * read until it is dropped, so it is made for one load, on one thread.
 */
public class CachedDataManager extends DataManager {
	private final DataManager source;
	private final Map<Long, OsmNode> nodes = new HashMap<>();
	// The node IDs read, those with no row too, so that they are not read again.
	private final Set<Long> nodesRead = new HashSet<>();
	// The collections each ID resolved to: a way and a relation may share an ID.
	private final Map<Long, List<OsmCollectionEntity>> collections = new HashMap<>();
	private final Map<String, List<Long>> nodeBoxes = new HashMap<>();
	private final Map<String, List<Long>> collectionBoxes = new HashMap<>();

	public CachedDataManager() {
		this(new DataManager());
	}

	/**
	 * @param source reads what is not read yet
	 */
	public CachedDataManager(DataManager source) {
		this.source = source;
	}

	@Override
	public Map<Long, OsmNode> loadNodeData(Context context, List<Long> ids) {
		List<Long> missing = new ArrayList<>();
		for (Long id : ids) {
			if (nodesRead.add(id)) {
				missing.add(id);
			}
		}
		if (!missing.isEmpty()) {
			nodes.putAll(source.loadNodeData(context, missing));
		}
		Map<Long, OsmNode> result = new HashMap<>();
		for (Long id : ids) {
			OsmNode node = nodes.get(id);
			if (node != null) {
				result.put(id, node);
			}
		}
		return result;
	}

	@Override
	public Map<String, OsmCollectionEntity> loadCollectionData(Context context, List<Long> ids) {
		Set<Long> missing = new LinkedHashSet<>();
		for (Long id : ids) {
			if (!collections.containsKey(id)) {
				missing.add(id);
			}
		}
		if (!missing.isEmpty()) {
			for (Long id : missing) {
				collections.put(id, new ArrayList<>());
			}
			for (OsmCollectionEntity collection
					: source.loadCollectionData(context, new ArrayList<>(missing)).values()) {
				collections.computeIfAbsent(collection.osmID, id -> new ArrayList<>())
						.add(collection);
			}
		}
		Map<String, OsmCollectionEntity> result = new HashMap<>();
		for (Long id : ids) {
			for (OsmCollectionEntity collection : collections.get(id)) {
				result.put(collectionKey(collection.osmType, collection.osmID), collection);
			}
		}
		return result;
	}

	@Override
	public List<Long> loadNodeBBox(Context context, MapBounds bounds,
	                               OsmEntity.EntityClimbingType... types) {
		return cachedBox(nodeBoxes, bounds, types,
				() -> source.loadNodeBBox(context, bounds, types));
	}

	@Override
	public List<Long> loadCollectionBBox(Context context, MapBounds bounds,
	                                     OsmEntity.EntityClimbingType... types) {
		return cachedBox(collectionBoxes, bounds, types,
				() -> source.loadCollectionBBox(context, bounds, types));
	}

	/** A copy of the IDs in the box, read once for the bounds and types. */
	private static List<Long> cachedBox(Map<String, List<Long>> boxes, MapBounds bounds,
	                                    OsmEntity.EntityClimbingType[] types,
	                                    Supplier<List<Long>> read) {
		StringBuilder key = new StringBuilder().append(bounds.getNorth()).append('|')
				.append(bounds.getEast()).append('|').append(bounds.getSouth()).append('|')
				.append(bounds.getWest());
		// By name: the type's toString throws, as the app shows the types translated.
		for (OsmEntity.EntityClimbingType type : types) {
			key.append('|').append(type.name());
		}
		return cachedBox(boxes, key.toString(), read);
	}

	private static List<Long> cachedBox(Map<String, List<Long>> boxes, String key,
	                                    Supplier<List<Long>> read) {
		List<Long> ids = boxes.get(key);
		if (ids == null) {
			ids = new ArrayList<>(read.get());
			boxes.put(key, ids);
		}
		return new ArrayList<>(ids);
	}
}
