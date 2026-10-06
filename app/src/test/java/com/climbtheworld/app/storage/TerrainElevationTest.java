package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerrainElevationTest {
	private static final int TILE_SIZE = 512;

	@Test
	public void decodeTerrarium() {
		assertEquals(0, TerrainElevation.decodeTerrarium(0xff800000), 1e-6);
		assertEquals(330.5, TerrainElevation.decodeTerrarium(0xff814a80), 1e-6);
		assertEquals(-1, TerrainElevation.decodeTerrarium(0xff7fff00), 1e-6);
	}

	@Test
	public void toTileCoordinates() {
		double[] centre = TerrainElevation.toTileCoordinates(0, 0, 1);
		assertEquals(1, centre[0], 1e-9);
		assertEquals(1, centre[1], 1e-9);

		// Val-David, Québec.
		double[] valDavid = TerrainElevation.toTileCoordinates(46.0305, -74.2056, 12);
		assertEquals(1203 + 361.0487 / TILE_SIZE, valDavid[0], 1e-6);
		assertEquals(1456 + 358.6765 / TILE_SIZE, valDavid[1], 1e-6);
	}

	@Test
	public void interpolateBetweenPixelCentres() {
		// Elevation grows by 1 per pixel to the east and by 1000 per pixel to the south.
		float[] tile = new float[TILE_SIZE * TILE_SIZE];
		for (int y = 0; y < TILE_SIZE; y++) {
			for (int x = 0; x < TILE_SIZE; x++) {
				tile[y * TILE_SIZE + x] = x + 1000 * y;
			}
		}

		assertEquals(10 + 1000 * 20, TerrainElevation.interpolate(tile, 10.5, 20.5), 1e-6);
		assertEquals(10.5 + 1000 * 20.25, TerrainElevation.interpolate(tile, 11, 20.75), 1e-6);
		// Closer to the edges than the outer pixel centres.
		assertEquals(0, TerrainElevation.interpolate(tile, 0.2, 0.1), 1e-6);
		assertEquals(511 + 1000 * 511, TerrainElevation.interpolate(tile, 511.9, 512), 1e-6);
	}
}
