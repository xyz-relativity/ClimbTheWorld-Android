package com.climbtheworld.app.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The decoded terrain tiles held in memory, and what is known of the others: being fetched,
 * missing at their zoom, or failed. Apart from {@link TerrainElevation}, which fetches them, so
 * that it runs without Android. Only to be used from one thread: another reads the tiles from a
 * {@link TerrainSnapshot}.
 */
final class TerrainTiles {
	static final long RETRY_DELAY_MS = 60_000;
	// Gives the time in milliseconds, to tell when a failed tile is due again.
	private final LongSupplier clock;
	private final Map<Long, float[]> tiles;
	private final Set<Long> loadingTiles = new HashSet<>();
	// Tiles that do not exist at their zoom, as the detailed data does not cover them.
	private final Set<Long> missingTiles = new HashSet<>();
	// Tiles that could not be fetched, by when, to retry them later.
	private final Map<Long, Long> failedTiles = new HashMap<>();
	// Changes whenever a tile loads or turns out to be missing, and when the tiles are let go
	// of, which can change lookups.
	private int version;
	// Counts the releases, see release.
	private int generation;

	TerrainTiles(int maxTiles, LongSupplier clock) {
		this.clock = clock;
		tiles = new LinkedHashMap<Long, float[]>(maxTiles, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
				return size() > maxTiles;
			}
		};
	}

	/**
	 * @return the elevations of the tile, null while they are not held
	 */
	float[] get(long key) {
		return tiles.get(key);
	}

	/**
	 * @return how many tiles are held
	 */
	int size() {
		return tiles.size();
	}

	/**
	 * Copies the tiles held into the arrays, which have room for {@link #size} of them. Unlike
	 * {@link #get}, it does not count as reading them: they make way as if it had not been
	 * called.
	 */
	void copyTo(long[] keys, float[][] elevations) {
		int index = 0;
		for (Map.Entry<Long, float[]> tile : tiles.entrySet()) {
			keys[index] = tile.getKey();
			elevations[index++] = tile.getValue();
		}
	}

	/**
	 * To be asked for a tile that is not held. When it is to be fetched, it counts as being
	 * fetched until one of {@link #loaded}, {@link #missing} and {@link #failed} is called for
	 * it, with the {@link #getGeneration generation} as of now.
	 *
	 * @return whether the tile is to be fetched: not when it already is, when it is known to be
	 * missing, or when it failed a short while ago
	 */
	boolean startLoading(long key) {
		if (loadingTiles.contains(key) || missingTiles.contains(key)) {
			return false;
		}
		Long failedAt = failedTiles.get(key);
		if (failedAt != null && clock.getAsLong() - failedAt < RETRY_DELAY_MS) {
			return false;
		}
		loadingTiles.add(key);
		return true;
	}

	/**
	 * @return what a fetch is to give back along with its result, see {@link #startLoading}
	 */
	int getGeneration() {
		return generation;
	}

	/** The fetch gave the elevations of the tile. */
	void loaded(long key, int generation, float[] elevations) {
		if (endLoading(key, generation)) {
			failedTiles.remove(key);
			tiles.put(key, elevations);
			version++;
		}
	}

	/** The fetch found that the tile does not exist at its zoom. */
	void missing(long key, int generation) {
		if (endLoading(key, generation)) {
			missingTiles.add(key);
			version++;
		}
	}

	/** The fetch failed. */
	void failed(long key, int generation) {
		if (endLoading(key, generation)) {
			failedTiles.put(key, clock.getAsLong());
		}
	}

	/**
	 * @return whether the result of the fetch is to be kept. Not when the tiles were let go of
	 * since it started: it would bring back what was given up, maybe with nobody left to use it.
	 * The tile is then free to be fetched again, which the version tells whoever waits for it.
	 */
	private boolean endLoading(long key, int generation) {
		loadingTiles.remove(key);
		if (generation == this.generation) {
			return true;
		}
		version++;
		return false;
	}

	/**
	 * Lets go of the tiles, and of what was found out about the others, as if none had been
	 * asked for yet. The version moves on, so that whoever laid things out on them looks them up
	 * again, which fetches them again. The fetches under way stay so, not to run twice for a
	 * tile, but their result is not kept, see endLoading.
	 */
	void release() {
		tiles.clear();
		missingTiles.clear();
		failedTiles.clear();
		generation++;
		version++;
	}

	/**
	 * @return a number that changes whenever lookups may return something else than before
	 */
	int getVersion() {
		return version;
	}
}
