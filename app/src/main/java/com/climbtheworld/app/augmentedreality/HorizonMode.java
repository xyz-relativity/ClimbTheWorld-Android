package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.R;

/**
 * What the AR view draws to show where the camera points. Whether it places the POIs at their
 * elevation is a separate setting, see Configs.isArElevation.
 */
public enum HorizonMode {
	OFF(R.string.ar_horizon_off, R.drawable.ic_horizon_off, R.drawable.ic_pois_flat,
			R.drawable.ic_pois_elevation),
	/** A flat line at eye level. */
	HORIZON(R.string.ar_horizon_horizon, R.drawable.ic_horizon, R.drawable.ic_pois_flat,
			R.drawable.ic_pois_elevation),
	/** The terrain around the observer as a wireframe, see {@link TerrainWireframe}. */
	TERRAIN(R.string.ar_horizon_terrain, R.drawable.ic_horizon_terrain,
			R.drawable.ic_pois_terrain_flat, R.drawable.ic_pois_terrain_elevation);

	public final int labelId;
	public final int iconId;
	/** Drawn over iconId, to show the POIs placed as if on flat ground. */
	public final int poisFlatIconId;
	/** Drawn over iconId, to show the POIs placed at their elevation. */
	public final int poisElevationIconId;

	HorizonMode(int labelId, int iconId, int poisFlatIconId, int poisElevationIconId) {
		this.labelId = labelId;
		this.iconId = iconId;
		this.poisFlatIconId = poisFlatIconId;
		this.poisElevationIconId = poisElevationIconId;
	}

	/**
	 * @return the mode after this one, back to the first after the last
	 */
	public HorizonMode next() {
		HorizonMode[] modes = values();
		return modes[(ordinal() + 1) % modes.length];
	}
}
