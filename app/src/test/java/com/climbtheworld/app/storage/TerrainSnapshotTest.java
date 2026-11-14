package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerrainSnapshotTest {
	private static final int TILE_SIZE = TerrainElevation.TILE_SIZE;
	private static final int MAX_TILES = 3;
	private static final int BASE_ZOOM = TerrainElevation.BASE_ZOOM;
	private static final int DETAIL_ZOOM = TerrainElevation.DETAIL_ZOOM;
	// Val-David, Québec, and how far east the next detailed tile is.
	private static final double LATITUDE = 46.0305;
	private static final double LONGITUDE = -74.2056;
	private static final double DETAIL_TILE_DEGREES = 360.0 / (1 << DETAIL_ZOOM);

	private final TerrainTiles tiles = new TerrainTiles(MAX_TILES, () -> 0);
	// The tiles asked of the terrain, as zoom/x/y.
	private final List<String> asked = new ArrayList<>();
	// Reads the tiles held as TerrainElevation does, which counts as using them.
	private final TerrainElevation.TileSource terrain = (zoom, x, y) -> {
		asked.add(zoom + "/" + x + "/" + y);
		return tiles.get(TerrainElevation.tileKey(zoom, x, y));
	};

	private static int[] tileAt(double longitude, int zoom) {
		double[] coordinates = TerrainElevation.toTileCoordinates(LATITUDE, longitude, zoom);
		return new int[]{(int) Math.floor(coordinates[0]), (int) Math.floor(coordinates[1])};
	}

	private static String name(int zoom, int[] tile) {
		return zoom + "/" + tile[0] + "/" + tile[1];
	}

	/**
	 * Holds the tile at the longitude, its elevations growing by 1 per pixel to the east and by
	 * 1000 per pixel to the south from the one given.
	 */
	private void hold(double longitude, int zoom, float elevation) {
		int[] tile = tileAt(longitude, zoom);
		float[] elevations = new float[TILE_SIZE * TILE_SIZE];
		for (int y = 0; y < TILE_SIZE; y++) {
			for (int x = 0; x < TILE_SIZE; x++) {
				elevations[y * TILE_SIZE + x] = elevation + x + 1000 * y;
			}
		}
		long key = TerrainElevation.tileKey(zoom, tile[0], tile[1]);
		assertTrue(tiles.startLoading(key));
		tiles.loaded(key, tiles.getGeneration(), elevations);
	}

	private boolean isHeld(double longitude, int zoom) {
		int[] tile = tileAt(longitude, zoom);
		return tiles.get(TerrainElevation.tileKey(zoom, tile[0], tile[1])) != null;
	}

	@Test
	public void readsWhatTheTerrainReads() {
		// The detailed tile here, and the base one under it and under the next detailed ones.
		hold(LONGITUDE, DETAIL_ZOOM, 1_000_000);
		hold(LONGITUDE, BASE_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);

		// From west of the detailed tile to east of it.
		for (double east = -1; east <= 1; east += 0.01) {
			double longitude = LONGITUDE + east * DETAIL_TILE_DEGREES;
			for (int zoom : new int[]{DETAIL_ZOOM, BASE_ZOOM}) {
				double expected = TerrainElevation.getElevation(terrain, LATITUDE, longitude, zoom);
				assertFalse(Double.isNaN(expected));
				assertEquals(expected, snapshot.getElevation(LATITUDE, longitude, zoom), 0);
			}
		}
		// In the detailed tile, and next to it, where the base one stands in.
		assertTrue(snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM) > 1_000_000);
		assertTrue(snapshot.getElevation(LATITUDE, LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM)
				< 1_000_000);
		assertTrue(snapshot.getElevation(LATITUDE, LONGITUDE, BASE_ZOOM) < 1_000_000);
	}

	@Test
	public void theElevationsOfTilesNotHeldAreUnknown() {
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);

		assertFalse(Double.isNaN(snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM)));
		assertTrue(Double.isNaN(snapshot.getElevation(LATITUDE, LONGITUDE, BASE_ZOOM)));
		assertTrue(Double.isNaN(
				snapshot.getElevation(LATITUDE, LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM)));
	}

	@Test
	public void staysAsTakenWhateverBecomesOfTheTiles() {
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);
		double taken = snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM);

		// A tile loaded since is not in it.
		hold(LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM, 0);
		assertTrue(Double.isNaN(
				snapshot.getElevation(LATITUDE, LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM)));
		// And one let go of since still is.
		tiles.release();
		assertFalse(isHeld(LONGITUDE, DETAIL_ZOOM));
		assertEquals(taken, snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM), 0);
	}

	@Test
	public void readingItLeavesTheTerrainAlone() {
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		int version = tiles.getVersion();
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);

		for (double east = -1; east <= 1; east += 0.01) {
			snapshot.getElevation(LATITUDE, LONGITUDE + east * DETAIL_TILE_DEGREES, DETAIL_ZOOM);
		}
		assertEquals("No tile asked for", Arrays.asList(), asked);
		assertEquals(version, tiles.getVersion());
	}

	@Test
	public void theTilesReadOnlyCountAsUsedOnceRequested() {
		// As many tiles as are kept, this one the least recently used.
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		hold(LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM, 0);
		hold(LONGITUDE + 2 * DETAIL_TILE_DEGREES, DETAIL_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);
		assertFalse(Double.isNaN(snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM)));

		// Taking and reading the snapshot is no use of it: it makes way for the next tile.
		hold(LONGITUDE + 3 * DETAIL_TILE_DEGREES, DETAIL_ZOOM, 0);
		assertFalse(isHeld(LONGITUDE, DETAIL_ZOOM));

		TerrainSnapshot requested = new TerrainSnapshot(tiles, terrain);
		assertFalse(Double.isNaN(requested.getElevation(LATITUDE,
				LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM)));
		requested.request();
		// The one read is now the most recently used: the next one makes way instead.
		hold(LONGITUDE + 4 * DETAIL_TILE_DEGREES, DETAIL_ZOOM, 0);
		assertTrue(isHeld(LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM));
		assertFalse(isHeld(LONGITUDE + 2 * DETAIL_TILE_DEGREES, DETAIL_ZOOM));
	}

	@Test
	public void requestAsksTheTerrainForEachTileLookedUpOnce() {
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);

		// Back and forth between the detailed tile held and the next one, which is not, so
		// the base one under it is looked up as well. Neither is.
		double next = LONGITUDE + DETAIL_TILE_DEGREES;
		for (int lookup = 0; lookup < 1000; lookup++) {
			snapshot.getElevation(LATITUDE, lookup % 2 == 0 ? LONGITUDE : next, DETAIL_ZOOM);
		}
		snapshot.request();

		assertEquals(Arrays.asList(name(DETAIL_ZOOM, tileAt(LONGITUDE, DETAIL_ZOOM)),
				name(DETAIL_ZOOM, tileAt(next, DETAIL_ZOOM)),
				name(BASE_ZOOM, tileAt(next, BASE_ZOOM))), asked);
	}

	@Test
	public void requestAsksForAsManyTilesAsWereLookedUp() {
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);

		// More tiles than are ever held, none of which is.
		for (int tile = 0; tile < 100; tile++) {
			assertTrue(Double.isNaN(snapshot.getElevation(LATITUDE,
					LONGITUDE + tile * DETAIL_TILE_DEGREES, DETAIL_ZOOM)));
		}
		snapshot.request();

		// Each detailed tile, and the base ones under them, four detailed tiles wide.
		assertEquals(100 + 26, asked.size());
		assertEquals(100 + 26, asked.stream().distinct().count());
	}

	@Test
	public void nothingIsAskedForOnceTheTilesWereLetGoOf() {
		hold(LONGITUDE, DETAIL_ZOOM, 0);
		TerrainSnapshot snapshot = new TerrainSnapshot(tiles, terrain);
		snapshot.getElevation(LATITUDE, LONGITUDE, DETAIL_ZOOM);
		snapshot.getElevation(LATITUDE, LONGITUDE + DETAIL_TILE_DEGREES, DETAIL_ZOOM);

		// As once the last A.R. screen is gone: asking would fetch tiles nobody uses.
		tiles.release();
		snapshot.request();

		assertEquals(Arrays.asList(), asked);
	}
}
