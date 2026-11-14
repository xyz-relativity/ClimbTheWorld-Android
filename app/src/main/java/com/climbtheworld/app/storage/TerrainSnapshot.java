package com.climbtheworld.app.storage;

import java.util.Arrays;

/**
 * The elevations as the terrain tiles held at one time give them, for a thread other than the
 * main one, which {@link TerrainElevation} cannot be read from: its lookups move the tile read
 * among the recently used, and have the tiles not held fetched. A decoded tile never changes, so
 * the snapshot holds on to the ones held when it was taken, and notes the tiles it is asked for,
 * for the main thread to ask the terrain for them afterwards, see {@link #request}.
 * <p>
 * Taken on the main thread, then read from one thread at a time, each handing it to the next. It
 * keeps the tiles it holds from being let go of, so it is to be dropped once read.
 */
public final class TerrainSnapshot {
	// Each tile looked up is noted as its zoom, x and y.
	private static final int LOOKED_UP_FIELDS = 3;
	private final TerrainTiles held;
	private final TerrainElevation.TileSource terrain;
	// Counts the releases of the tiles, as of when the snapshot was taken, see request.
	private final int generation;
	private final long[] keys;
	private final float[][] tiles;
	private final TerrainElevation.TileSource snapshotTiles = this::getTile;
	// The tiles looked up, held or not, see request.
	private int[] lookedUp = new int[16 * LOOKED_UP_FIELDS];
	private int lookedUpLength;
	// The tile of the last lookup, which the next one mostly reads too. No tile has this key.
	private long lastKey = -1;
	private float[] lastTile;

	/**
	 * @param held    the tiles to take as they are now
	 * @param terrain where the tiles looked up are asked for afterwards, see {@link #request}
	 */
	TerrainSnapshot(TerrainTiles held, TerrainElevation.TileSource terrain) {
		this.held = held;
		this.terrain = terrain;
		generation = held.getGeneration();
		keys = new long[held.size()];
		tiles = new float[keys.length][];
		held.copyTo(keys, tiles);
	}

	/**
	 * @param zoom tile zoom to read from, falling back to {@link TerrainElevation#BASE_ZOOM}
	 *             where it has no data
	 * @return what {@link TerrainElevation#getElevation} gave when the snapshot was taken: the
	 * ground elevation above sea level in meters, or NaN while it was not known
	 */
	public double getElevation(double latitude, double longitude, int zoom) {
		return TerrainElevation.getElevation(snapshotTiles, latitude, longitude, zoom);
	}

	private float[] getTile(int zoom, int x, int y) {
		long key = TerrainElevation.tileKey(zoom, x, y);
		if (key != lastKey) {
			lastKey = key;
			lastTile = null;
			for (int index = 0; index < keys.length; index++) {
				if (keys[index] == key) {
					lastTile = tiles[index];
					break;
				}
			}
			noteLookedUp(zoom, x, y);
		}
		return lastTile;
	}

	private void noteLookedUp(int zoom, int x, int y) {
		for (int index = 0; index < lookedUpLength; index += LOOKED_UP_FIELDS) {
			if (lookedUp[index] == zoom && lookedUp[index + 1] == x && lookedUp[index + 2] == y) {
				return;
			}
		}
		if (lookedUpLength == lookedUp.length) {
			lookedUp = Arrays.copyOf(lookedUp, 2 * lookedUp.length);
		}
		lookedUp[lookedUpLength++] = zoom;
		lookedUp[lookedUpLength++] = x;
		lookedUp[lookedUpLength++] = y;
	}

	/**
	 * Asks the terrain for every tile the snapshot was asked for, once each, as looking the same
	 * elevations up on the main thread would have: the tiles held stay among the recently used,
	 * and the others are fetched, which the terrain's version tells once they are there. To be
	 * called on the main thread, once the snapshot is read, by whoever still uses the terrain.
	 * <p>
	 * Not when the tiles were let go of since the snapshot was taken: it would bring back what
	 * was given up. The version told so, and whoever still draws the terrain reads it again.
	 */
	public void request() {
		if (held.getGeneration() != generation) {
			return;
		}
		for (int index = 0; index < lookedUpLength; index += LOOKED_UP_FIELDS) {
			terrain.getTile(lookedUp[index], lookedUp[index + 1], lookedUp[index + 2]);
		}
	}
}
