package com.climbtheworld.app.storage.logbook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.widget.climbing.RouteWay;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmCollectionEntityDao;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.database.OsmNodeDao;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The bookmarks, kept in memory for the "bookmarked only" display filter, which tests them on
 * the main thread too, see NodeDisplayFilters. While that filter is on, they are loaded from the
 * log book and the OSM data on first use, and again after a bookmark is set or cleared, and
 * after an edit, an upload or a download, which may change what a crag holds. While it is off,
 * none are kept.
 */
public final class Bookmarks {
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static final Handler mainThread = new Handler(Looper.getMainLooper());
	// Null until loaded.
	private static volatile BookmarkIndex index;
	private static volatile Context appContext;

	private Bookmarks() {
		//hide constructor
	}

	/**
	 * @return the bookmarks as last loaded. Before the first load, it is loaded off the main
	 * thread, and started on it, empty until done; the listeners are told once it is.
	 */
	public static BookmarkIndex current() {
		BookmarkIndex result = index;
		if (result != null) {
			return result;
		}
		Context context = appContext;
		if (context == null) {
			return BookmarkIndex.EMPTY;
		}
		if (Looper.myLooper() == Looper.getMainLooper()) {
			refresh(context);
			return BookmarkIndex.EMPTY;
		}
		return reload(context);
	}

	/**
	 * Loads the bookmarks unless they are, so the display filters read from now on test them:
	 * at once off the main thread, in the background on it, see current.
	 */
	public static void ensureLoaded(Context context) {
		appContext = context.getApplicationContext();
		if (index == null) {
			current();
		}
	}

	/**
	 * Loads the bookmarks again after the log book or the OSM data changed, unless they were
	 * never loaded, as nothing filters by them yet: at once off the main thread, in the
	 * background on it. While the filter is off, they are dropped instead.
	 */
	public static void changed(Context context) {
		if (index == null) {
			return;
		}
		if (!isFiltering(context)) {
			index = null;
			return;
		}
		if (Looper.myLooper() == Looper.getMainLooper()) {
			refresh(context);
		} else {
			reload(context);
		}
	}

	/**
	 * Loads the bookmarks again, telling the listeners when they changed. To be called off the
	 * main thread.
	 */
	public static synchronized BookmarkIndex reload(Context context) {
		appContext = context.getApplicationContext();
		BookmarkIndex loaded = load(appContext);
		BookmarkIndex previous = index != null ? index : BookmarkIndex.EMPTY;
		if (loaded.sameAs(previous)) {
			// Keeps the same object, see BookmarkIndex.sameAs.
			index = previous;
			return previous;
		}
		index = loaded;
		for (Runnable listener : listeners) {
			mainThread.post(listener);
		}
		return loaded;
	}

	/**
	 * Loads the bookmarks again in the background, see reload.
	 */
	public static void refresh(Context context) {
		Context application = context.getApplicationContext();
		appContext = application;
		Constants.DB_EXECUTOR.execute(() -> reload(application));
	}

	/**
	 * Tells the listener, on the main thread, whenever the bookmarks change, until removed. While
	 * the filter is on, also loads them again, as they may have changed meanwhile.
	 */
	public static void addListener(Context context, Runnable listener) {
		listeners.add(listener);
		appContext = context.getApplicationContext();
		if (isFiltering(context)) {
			refresh(context);
		}
	}

	public static void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	private static boolean isFiltering(Context context) {
		return Configs.instance(context).getBoolean(Configs.ConfigKey.filterBookmarked);
	}

	private static BookmarkIndex load(Context context) {
		List<String> keys = new ArrayList<>();
		for (LogBookEntry entry :
				LogBookDatabase.getInstance(context).logBookDao().loadBookmarked()) {
			keys.add(BookmarkIndex.key(entry.osmType.name(), entry.osmID));
		}
		if (keys.isEmpty()) {
			return BookmarkIndex.EMPTY;
		}

		AppDatabase osmData = AppDatabase.getInstance(context);
		OsmCollectionEntityDao collections = osmData.osmCollectionDao();
		OsmNodeDao nodes = osmData.osmNodeDao();
		Function<Long, OsmNode> findNode = nodeId -> nodes.find(OsmEntity.EntityOsmType.node,
				nodeId);
		return BookmarkIndex.build(keys, new BookmarkIndex.Parts() {
			@Override
			public Collection<String> of(String key) {
				int separator = key.indexOf('|');
				OsmEntity.EntityOsmType osmType;
				long osmId;
				try {
					osmType = OsmEntity.EntityOsmType.valueOf(key.substring(0, separator));
					osmId = Long.parseLong(key.substring(separator + 1));
				} catch (IllegalArgumentException | IndexOutOfBoundsException notAnElement) {
					return Collections.emptyList();
				}
				if (osmType == OsmEntity.EntityOsmType.node) {
					return Collections.emptyList();
				}
				OsmCollectionEntity collection = collections.find(osmType, osmId);
				if (collection == null) {
					return Collections.emptyList();
				}
				List<String> result = new ArrayList<>();
				if (osmType == OsmEntity.EntityOsmType.relation) {
					// Not its osmNodes, which also hold the nodes of its ways, route tops
					// included.
					for (JSONObject member : RelationMembers.of(collection.jsonNodeInfo)) {
						result.add(RelationMembers.key(member));
					}
				} else if (collection.entityClimbingType == OsmEntity.EntityClimbingType.route) {
					Long bottom = RouteWay.bottomNodeId(collection.osmNodes, findNode);
					if (bottom != null) {
						result.add(nodeKey(bottom));
					}
				} else {
					for (Long nodeId : collection.osmNodes) {
						result.add(nodeKey(nodeId));
					}
				}
				return result;
			}

			@Override
			public Collection<String> routeWaysFrom(long nodeId) {
				List<String> result = new ArrayList<>();
				for (OsmCollectionEntity way : collections.findContainingNode(nodeId)) {
					if (way.osmType == OsmEntity.EntityOsmType.way
							&& way.entityClimbingType == OsmEntity.EntityClimbingType.route
							&& Long.valueOf(nodeId).equals(
							RouteWay.bottomNodeId(way.osmNodes, findNode))) {
						result.add(BookmarkIndex.key(way.osmType.name(), way.osmID));
					}
				}
				return result;
			}
		});
	}

	private static String nodeKey(long nodeId) {
		return BookmarkIndex.key(OsmEntity.EntityOsmType.node.name(), nodeId);
	}
}
