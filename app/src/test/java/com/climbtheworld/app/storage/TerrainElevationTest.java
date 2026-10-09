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

	@Test
	public void extendToFoot() {
		// Flat ground under a wall the tiles smoothed: the point reads 4 m high.
		assertEquals(100, TerrainElevation.extendToFoot(104, 100, 100), 1e-9);
		// A steep approach falling 0.5 m per meter: back at the point, the ground is at 100.
		assertEquals(100, TerrainElevation.extendToFoot(103, 97, 94), 1e-9);
		// A ledge above another wall: the far ground is at its foot, so the point reading stands.
		assertEquals(101, TerrainElevation.extendToFoot(101, 100, 90), 1e-9);
		// Ground rising again further out, as across a ditch: no lower than near.
		assertEquals(100, TerrainElevation.extendToFoot(104, 100, 102), 1e-9);
	}

	@Test
	public void footElevationOutvotesASingleMisleadingDirection() {
		// Le bloc de l'Île Sainte-Hélène, La CRAQ, at the outer corner of the wall: off the
		// corner, to the north-west, the ground keeps falling, and to the north it is the flat
		// car park the neighbouring routes start from, at 27.4.
		double[] near = {27.3, 27.4, 28.1, 31.8, 33.9, 31.8, 27.9, 27.0};
		double[] far = {27.2, 27.4, 28.1, 34.7, 34.3, 27.1, 24.1, 24.8};
		assertEquals(27.4, TerrainElevation.footElevation(29.1, near, far), 1e-9);

		// A steady approach falling south-west, with a bench to the west.
		near = new double[]{190, 190, 190, 190, 184.0, 183.4, 183.4, 190};
		far = new double[]{190, 190, 190, 190, 181.0, 180.0, 182.2, 190};
		assertEquals(186.8, TerrainElevation.footElevation(188.3, near, far), 1e-9);
	}
}
