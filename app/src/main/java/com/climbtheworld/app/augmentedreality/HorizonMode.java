package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.R;

/**
 * What the AR view draws to show where the camera points, and whether it places the POIs at
 * their elevation.
 */
public enum HorizonMode {
	OFF(R.string.ar_horizon_off, true),
	/**
	 * A flat line at eye level, with every POI on level ground with the observer, which needs no
	 * elevation data.
	 */
	HORIZON(R.string.ar_horizon_horizon, false),
	/** A flat line at eye level. */
	HORIZON_ELEVATION(R.string.ar_horizon_horizon_elevation, true),
	/** The terrain around the observer as a wireframe, see {@link TerrainWireframe}. */
	TERRAIN(R.string.ar_horizon_terrain, true);

	public final int labelId;
	public final boolean usesElevation;

	HorizonMode(int labelId, boolean usesElevation) {
		this.labelId = labelId;
		this.usesElevation = usesElevation;
	}
}
