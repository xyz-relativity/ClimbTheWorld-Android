package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.R;

/**
 * What the AR view draws to show where the camera points, and whether it places the POIs at
 * their elevation.
 */
public enum HorizonMode {
	OFF(R.string.ar_horizon_off, R.drawable.ic_horizon_off, true),
	/**
	 * A flat line at eye level, with every POI on level ground with the observer, which needs no
	 * elevation data.
	 */
	HORIZON(R.string.ar_horizon_horizon, R.drawable.ic_horizon, false),
	/** A flat line at eye level. */
	HORIZON_ELEVATION(R.string.ar_horizon_horizon_elevation, R.drawable.ic_horizon_elevation,
			true),
	/** The terrain around the observer as a wireframe, see {@link TerrainWireframe}. */
	TERRAIN(R.string.ar_horizon_terrain, R.drawable.ic_horizon_terrain, true);

	public final int labelId;
	public final int iconId;
	public final boolean usesElevation;

	HorizonMode(int labelId, int iconId, boolean usesElevation) {
		this.labelId = labelId;
		this.iconId = iconId;
		this.usesElevation = usesElevation;
	}

	/**
	 * @return the mode after this one, back to the first after the last
	 */
	public HorizonMode next() {
		HorizonMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}
}
