package com.climbtheworld.app.map.style;

/**
 * The relief shaded over a base map, for the styles that ask for it, see
 * {@link MapStyleDefinition#getShadingBelowLayerId}, and the 3D terrain the map is draped over
 * on those styles, which shows once the map is tilted. Both are drawn from the Mapterhorn
 * terrain tiles, the ones the A.R. view places the routes on, see TerrainElevation: free to use,
 * with the attribution of their data sources.
 */
public final class TerrainShading {
	// The heights of the 3D terrain: true scale.
	public static final float TERRAIN_3D_EXAGGERATION = 1f;
	// The coarsest terrain tiles the relief is shaded from. MapLibre Native (13.7.1,
	// maplibre-native#4886) shades the one zoom 0 tile as if all of it lay at 85° of latitude,
	// about 12 times too steep, and would draw it below zoom 0.5 and in place of tiles still
	// loading.
	public static final float SOURCE_MIN_ZOOM = 1;
	// On a shaded style the map zooms out no further: below 0.5, MapLibre would want the zoom 0
	// terrain tile (it rounds the zoom), which SOURCE_MIN_ZOOM leaves out, so nothing would be
	// shaded. Not 0.5 itself, which may round down.
	public static final double MAP_MIN_ZOOM = 0.55;
	// Zoomed out, the terrain is so coarse that its slopes flatten: shaded stronger there, up to
	// WORLD_ZOOM, easing down to EXAGGERATION at REGION_ZOOM, to look about as strong throughout.
	public static final float WORLD_ZOOM = 2;
	public static final float WORLD_EXAGGERATION = 1f;
	public static final float REGION_ZOOM = 7;
	public static final float EXAGGERATION = 0.5f;
	public static final String SHADOW_COLOR = "#473b24";
	public static final String HIGHLIGHT_COLOR = "#ffffff";
	public static final int ACCENT_COLOR = 0xff473b24;

	private TerrainShading() {
	}
}
