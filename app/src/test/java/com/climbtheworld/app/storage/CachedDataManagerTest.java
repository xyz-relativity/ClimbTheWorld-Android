package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class CachedDataManagerTest {
	private static final MapBounds BOUNDS = MapBounds.fromUnwrapped(46, 25, 45, 24);
	// The IDs each read asked the database for, in order.
	private final List<List<Long>> nodeReads = new ArrayList<>();
	private final List<List<Long>> collectionReads = new ArrayList<>();
	private final List<String> boxReads = new ArrayList<>();
	private final Map<Long, OsmNode> databaseNodes = new HashMap<>();
	private final List<OsmCollectionEntity> databaseCollections = new ArrayList<>();
	private final CachedDataManager cache = new CachedDataManager(new DataManager() {
		@Override
		public Map<Long, OsmNode> loadNodeData(Context context, List<Long> ids) {
			nodeReads.add(new ArrayList<>(ids));
			Map<Long, OsmNode> result = new HashMap<>();
			for (Long id : ids) {
				if (databaseNodes.containsKey(id)) {
					result.put(id, databaseNodes.get(id));
				}
			}
			return result;
		}

		@Override
		public Map<String, OsmCollectionEntity> loadCollectionData(Context context,
		                                                           List<Long> ids) {
			collectionReads.add(new ArrayList<>(ids));
			Map<String, OsmCollectionEntity> result = new HashMap<>();
			for (OsmCollectionEntity collection : databaseCollections) {
				if (ids.contains(collection.osmID)) {
					result.put(collectionKey(collection.osmType, collection.osmID), collection);
				}
			}
			return result;
		}

		@Override
		public List<Long> loadCollectionBBox(Context context, MapBounds bounds,
		                                     OsmEntity.EntityClimbingType... types) {
			// The type's toString throws, see OsmEntity.EntityClimbingType.asString.
			boxReads.add(types[0].name());
			return Arrays.asList(10L, 11L);
		}
	});

	@Test
	public void eachNodeIsReadOnce() throws Exception {
		OsmNode first = node(1);
		node(2);
		node(3);

		Map<Long, OsmNode> read = cache.loadNodeData(null, Arrays.asList(1L, 2L));
		assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), read.keySet());
		assertSame(first, read.get(1L));
		assertEquals(Collections.singletonList(Arrays.asList(1L, 2L)), nodeReads);

		read = cache.loadNodeData(null, Arrays.asList(2L, 3L, 2L));
		assertEquals(new HashSet<>(Arrays.asList(2L, 3L)), read.keySet());
		assertEquals(Arrays.asList(Arrays.asList(1L, 2L), Collections.singletonList(3L)),
				nodeReads);

		read = cache.loadNodeData(null, Arrays.asList(3L, 1L));
		assertEquals(new HashSet<>(Arrays.asList(1L, 3L)), read.keySet());
		assertEquals(2, nodeReads.size());
	}

	@Test
	public void aNodeTheDatabaseLacksIsAskedForOnce() throws Exception {
		node(1);

		assertEquals(Collections.singleton(1L),
				cache.loadNodeData(null, Arrays.asList(1L, 4L)).keySet());
		assertTrue(cache.loadNodeData(null, Collections.singletonList(4L)).isEmpty());
		assertEquals(Collections.singletonList(Arrays.asList(1L, 4L)), nodeReads);
	}

	@Test
	public void aWayAndARelationSharingAnIdAreBothKept() throws Exception {
		OsmCollectionEntity way = collection(10, "way");
		OsmCollectionEntity relation = collection(10, "relation");
		collection(11, "way");

		Map<String, OsmCollectionEntity> read =
				cache.loadCollectionData(null, Arrays.asList(10L, 11L));
		assertEquals(3, read.size());
		assertSame(way, read.get(DataManager.collectionKey(OsmEntity.EntityOsmType.way, 10)));
		assertSame(relation,
				read.get(DataManager.collectionKey(OsmEntity.EntityOsmType.relation, 10)));

		read = cache.loadCollectionData(null, Arrays.asList(11L, 12L, 10L));
		assertEquals(3, read.size());
		assertEquals(Arrays.asList(Arrays.asList(10L, 11L), Collections.singletonList(12L)),
				collectionReads);
	}

	@Test
	public void aBoxIsReadOnceForItsBoundsAndTypes() {
		List<Long> read = cache.loadCollectionBBox(null, BOUNDS,
				OsmEntity.EntityClimbingType.crag);
		assertEquals(Arrays.asList(10L, 11L), read);
		// A caller may change what it got without changing what the next one gets.
		read.clear();
		assertEquals(Arrays.asList(10L, 11L),
				cache.loadCollectionBBox(null, BOUNDS, OsmEntity.EntityClimbingType.crag));
		cache.loadCollectionBBox(null, BOUNDS, OsmEntity.EntityClimbingType.area);
		cache.loadCollectionBBox(null, MapBounds.fromUnwrapped(47, 25, 45, 24),
				OsmEntity.EntityClimbingType.crag);
		assertEquals(Arrays.asList("crag", "area", "crag"), boxReads);
	}

	private OsmNode node(long id) throws Exception {
		OsmNode node = new OsmNode(new JSONObject()
				.put("type", "node").put("id", id).put("lat", 45.5).put("lon", 24.5)
				.put("tags", new JSONObject().put("sport", "climbing")));
		databaseNodes.put(id, node);
		return node;
	}

	private OsmCollectionEntity collection(long id, String type) throws Exception {
		OsmCollectionEntity collection = new OsmCollectionEntity(new JSONObject()
				.put("type", type).put("id", id)
				.put("tags", new JSONObject().put("sport", "climbing").put("climbing", "crag")));
		databaseCollections.add(collection);
		return collection;
	}
}
