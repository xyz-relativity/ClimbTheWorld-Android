package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerrainTilesTest {
	private static final int MAX_TILES = 3;
	private static final long KEY = 42;

	private long nowMillis = 1_000_000;
	private final TerrainTiles tiles = new TerrainTiles(MAX_TILES, () -> nowMillis);

	/** Fetches the tile as TerrainElevation does, the fetch giving elevations. */
	private float[] load(long key) {
		float[] elevations = new float[1];
		assertTrue(tiles.startLoading(key));
		tiles.loaded(key, tiles.getGeneration(), elevations);
		return elevations;
	}

	@Test
	public void aTileIsFetchedOnceAndHeld() {
		assertNull(tiles.get(KEY));
		assertTrue(tiles.startLoading(KEY));
		// Under way: not started again.
		assertFalse(tiles.startLoading(KEY));
		assertNull(tiles.get(KEY));

		int version = tiles.getVersion();
		float[] elevations = new float[1];
		tiles.loaded(KEY, tiles.getGeneration(), elevations);
		assertSame(elevations, tiles.get(KEY));
		assertNotEquals(version, tiles.getVersion());
	}

	@Test
	public void aMissingTileIsNotFetchedAgain() {
		assertTrue(tiles.startLoading(KEY));
		int version = tiles.getVersion();
		tiles.missing(KEY, tiles.getGeneration());

		assertNull(tiles.get(KEY));
		assertFalse(tiles.startLoading(KEY));
		// Lookups now fall back to the base zoom.
		assertNotEquals(version, tiles.getVersion());
	}

	@Test
	public void aFailedTileIsFetchedAgainLater() {
		assertTrue(tiles.startLoading(KEY));
		int version = tiles.getVersion();
		tiles.failed(KEY, tiles.getGeneration());
		// Lookups give what they gave.
		assertEquals(version, tiles.getVersion());

		nowMillis += TerrainTiles.RETRY_DELAY_MS - 1;
		assertFalse(tiles.startLoading(KEY));
		nowMillis += 1;
		assertTrue(tiles.startLoading(KEY));
	}

	@Test
	public void theTilesLeastRecentlyReadMakeWay() {
		for (long key = 0; key < MAX_TILES; key++) {
			load(key);
		}
		// Read, so the next one makes way instead.
		tiles.get(0);
		load(MAX_TILES);

		assertNull(tiles.get(1));
		for (long key : new long[]{0, 2, MAX_TILES}) {
			assertNotNull(tiles.get(key));
		}
	}

	@Test
	public void aReleaseLetsGoOfEverythingAndTellsSo() {
		load(KEY);
		assertTrue(tiles.startLoading(KEY + 1));
		tiles.missing(KEY + 1, tiles.getGeneration());
		assertTrue(tiles.startLoading(KEY + 2));
		tiles.failed(KEY + 2, tiles.getGeneration());
		int version = tiles.getVersion();

		tiles.release();

		// Whoever laid things out on the tiles has to look them up again.
		assertNotEquals(version, tiles.getVersion());
		assertNull(tiles.get(KEY));
		// As when nothing had been asked for yet.
		assertTrue(tiles.startLoading(KEY));
		assertTrue(tiles.startLoading(KEY + 1));
		assertTrue(tiles.startLoading(KEY + 2));
	}

	@Test
	public void tilesFetchedAfterAReleaseAreHeld() {
		load(KEY);
		tiles.release();

		float[] elevations = load(KEY);
		assertSame(elevations, tiles.get(KEY));
	}

	@Test
	public void aFetchBackAfterAReleaseIsNotKept() {
		assertTrue(tiles.startLoading(KEY));
		int generation = tiles.getGeneration();
		tiles.release();

		// Still under way: not started a second time.
		assertFalse(tiles.startLoading(KEY));

		int version = tiles.getVersion();
		tiles.loaded(KEY, generation, new float[1]);
		assertNull(tiles.get(KEY));
		// Whoever waits for the tile is told to ask again, which fetches it again.
		assertNotEquals(version, tiles.getVersion());
		assertTrue(tiles.startLoading(KEY));
	}

	@Test
	public void aTileFoundMissingOrFailedBeforeAReleaseIsAskedForAgain() {
		assertTrue(tiles.startLoading(KEY));
		assertTrue(tiles.startLoading(KEY + 1));
		int generation = tiles.getGeneration();
		tiles.release();

		int version = tiles.getVersion();
		tiles.missing(KEY, generation);
		assertNotEquals(version, tiles.getVersion());
		version = tiles.getVersion();
		tiles.failed(KEY + 1, generation);
		assertNotEquals(version, tiles.getVersion());

		// Neither is remembered as missing or failed.
		assertTrue(tiles.startLoading(KEY));
		assertTrue(tiles.startLoading(KEY + 1));
	}
}
