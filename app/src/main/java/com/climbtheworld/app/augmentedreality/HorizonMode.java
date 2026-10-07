package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.R;

/**
 * What the AR view draws to show where the camera points.
 */
public enum HorizonMode {
	OFF(R.string.ar_horizon_off),
	/** A flat line at eye level. */
	HORIZON(R.string.ar_horizon_virtual_horizon),
	/** The terrain around the observer as a wireframe, see {@link TerrainWireframe}. */
	TERRAIN(R.string.ar_horizon_virtual_terrain);

	public final int labelId;

	HorizonMode(int labelId) {
		this.labelId = labelId;
	}
}
