package com.climbtheworld.app.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import needle.UiRelatedTask;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Ground elevation above sea level from the Mapterhorn terrain tiles: Terrarium encoded WebP tiles
 * covering the world at zoom 12 (about 30 m) and some regions in more detail. Tiles are fetched
 * in the background and cached on disk, so lookups never block; they return NaN until the tile
 * is there.
 * <p>
 * Prototype: not tied into the offline downloads yet. Only to be used from the main thread.
 */
public class TerrainElevation {
	// Available everywhere.
	public static final int BASE_ZOOM = 12;
	// Only where detailed data exists; elsewhere lookups fall back to the base zoom.
	public static final int DETAIL_ZOOM = 14;
	// As the Mapterhorn TileJSON gives it, to be shown wherever the terrain is used.
	public static final String ATTRIBUTION =
			"<a href='https://mapterhorn.com/attribution'>© Mapterhorn</a>";
	public static final String TILE_URL = "https://tiles.mapterhorn.com/{z}/{x}/{y}.webp";
	public static final String ENCODING = "terrarium";
	public static final int TILE_SIZE = 512;
	private static final String TAG = TerrainElevation.class.getSimpleName();
	private static final long TILE_TIMEOUT_SECONDS = 20;
	private static final long RETRY_DELAY_MS = 60_000;
	// Each decoded tile takes 1 MB. The AR terrain wireframe reads all around the observer, which
	// takes up to about 13 tiles at mid latitudes and 20 near the polar circles, where the tiles
	// cover less ground; fewer than it needs would keep evicting and reloading them.
	private static final int MAX_CACHED_TILES = 24;
	// The tiles smooth a wall into a slope a few meters wide, so the ground next to its foot
	// reads meters too high; past these distances it reads the ground clear of the wall, see
	// getFootElevation.
	private static final double FOOT_NEAR_METERS = 6;
	private static final double FOOT_FAR_METERS = 12;
	private static final int FOOT_DIRECTIONS = 8;
	// How many of the directions lowest at FOOT_NEAR_METERS are weighed, see footElevation.
	private static final int FOOT_LOWEST_DIRECTIONS = 3;
	private static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	private final File cacheDirectory;
	private final OkHttpClient httpClient;
	private final Map<Long, float[]> tiles =
			new LinkedHashMap<Long, float[]>(MAX_CACHED_TILES, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
					return size() > MAX_CACHED_TILES;
				}
			};
	private final Set<Long> loadingTiles = new HashSet<>();
	// Tiles that do not exist at their zoom, as the detailed data does not cover them.
	private final Set<Long> missingTiles = new HashSet<>();
	// Tiles that could not be fetched, by when, to retry them later.
	private final Map<Long, Long> failedTiles = new HashMap<>();
	// Changes whenever a tile loads or turns out to be missing, which can change lookups.
	private int version;

	public TerrainElevation(Context context) {
		cacheDirectory = new File(context.getCacheDir(), "terrain");
		httpClient = new OkHttpClient.Builder()
				.connectTimeout(TILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(TILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build();
	}

	private static float[] decode(byte[] data) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		options.inPremultiplied = false;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
		if (bitmap == null) {
			return null;
		}
		if (bitmap.getWidth() != TILE_SIZE || bitmap.getHeight() != TILE_SIZE) {
			bitmap.recycle();
			return null;
		}

		int[] pixels = new int[TILE_SIZE * TILE_SIZE];
		bitmap.getPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE);
		bitmap.recycle();

		float[] elevations = new float[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			elevations[i] = decodeTerrarium(pixels[i]);
		}
		return elevations;
	}

	/**
	 * Terrarium stores the elevation in the colour: red * 256 + green + blue / 256 - 32768.
	 */
	static float decodeTerrarium(int argb) {
		int red = (argb >> 16) & 0xff;
		int green = (argb >> 8) & 0xff;
		int blue = argb & 0xff;
		return red * 256 + green + blue / 256f - 32768;
	}

	/**
	 * @return the position in tiles of the coordinates at the zoom, x growing east and y south
	 */
	static double[] toTileCoordinates(double latitude, double longitude, int zoom) {
		double tileCount = 1 << zoom;
		double latitudeRadians = Math.toRadians(latitude);
		return new double[]{
				(longitude + 180) / 360 * tileCount,
				(1 - Math.log(Math.tan(latitudeRadians) + 1 / Math.cos(latitudeRadians)) / Math.PI)
						/ 2 * tileCount};
	}

	/**
	 * Bilinear interpolation between the four pixels around a position in the tile, in pixels
	 * from its top left corner. Positions closer to the edge than a pixel centre take the edge
	 * pixels.
	 */
	static double interpolate(float[] tile, double pixelX, double pixelY) {
		// Pixel centres sit half a pixel in from the pixel corners.
		double x = Math.max(0, Math.min(TILE_SIZE - 1, pixelX - 0.5));
		double y = Math.max(0, Math.min(TILE_SIZE - 1, pixelY - 0.5));
		int left = (int) Math.floor(x);
		int top = (int) Math.floor(y);
		int right = Math.min(left + 1, TILE_SIZE - 1);
		int bottom = Math.min(top + 1, TILE_SIZE - 1);
		double fractionX = x - left;
		double fractionY = y - top;

		double topElevation = tile[top * TILE_SIZE + left] * (1 - fractionX)
				+ tile[top * TILE_SIZE + right] * fractionX;
		double bottomElevation = tile[bottom * TILE_SIZE + left] * (1 - fractionX)
				+ tile[bottom * TILE_SIZE + right] * fractionX;
		return topElevation * (1 - fractionY) + bottomElevation * fractionY;
	}

	private static long tileKey(int zoom, int x, int y) {
		return ((long) zoom << 58) | ((long) x << 29) | y;
	}

	/**
	 * @param zoom tile zoom to read from, falling back to {@link #BASE_ZOOM} where it has no data
	 * @return the ground elevation above sea level in meters, or NaN while it is not known
	 */
	public double getElevation(double latitude, double longitude, int zoom) {
		double elevation = sample(latitude, longitude, zoom);
		if (Double.isNaN(elevation) && zoom != BASE_ZOOM) {
			// Detailed tiles only exist in some regions, and may still be loading.
			elevation = sample(latitude, longitude, BASE_ZOOM);
		}
		return elevation;
	}

	/**
	 * Ground elevation at the foot of a wall, for a point mapped next to it, such as the bottom of
	 * a climbing route. The tiles smooth the wall into a slope, so the point itself reads meters
	 * too high. Instead, the slope of the ground clear of the wall is extended back to the point,
	 * see footElevation.
	 *
	 * @return the elevation above sea level in meters, or NaN while it is not known
	 */
	public double getFootElevation(double latitude, double longitude, int zoom) {
		double elevation = getElevation(latitude, longitude, zoom);
		if (Double.isNaN(elevation)) {
			return elevation;
		}

		double metersPerDegreeLongitude = METERS_PER_DEGREE * Math.cos(Math.toRadians(latitude));
		double[] near = new double[FOOT_DIRECTIONS];
		double[] far = new double[FOOT_DIRECTIONS];
		for (int direction = 0; direction < FOOT_DIRECTIONS; direction++) {
			double angle = 2 * Math.PI * direction / FOOT_DIRECTIONS;
			double north = Math.cos(angle) / METERS_PER_DEGREE;
			double east = Math.sin(angle) / metersPerDegreeLongitude;
			near[direction] = getElevation(latitude + FOOT_NEAR_METERS * north,
					longitude + FOOT_NEAR_METERS * east, zoom);
			far[direction] = getElevation(latitude + FOOT_FAR_METERS * north,
					longitude + FOOT_FAR_METERS * east, zoom);
			if (Double.isNaN(near[direction]) || Double.isNaN(far[direction])) {
				// The ground around is in a tile still loading.
				return elevation;
			}
		}
		return footElevation(elevation, near, far);
	}

	/**
	 * The directions lowest at FOOT_NEAR_METERS lead away from the wall. In each of the three
	 * lowest, the slope of the ground is extended back to the point, see extendToFoot, and the
	 * middle result is kept, so a single direction that misleads does not decide: off the rounded
	 * corner of a wall the ground keeps falling, which reads too high, and onto a bench it
	 * flattens, which reads too low.
	 *
	 * @param elevation the elevation the point reads
	 * @param near      per direction, the elevation FOOT_NEAR_METERS away
	 * @param far       per direction, the elevation FOOT_FAR_METERS away
	 */
	static double footElevation(double elevation, double[] near, double[] far) {
		Integer[] downhillFirst = new Integer[near.length];
		for (int direction = 0; direction < near.length; direction++) {
			downhillFirst[direction] = direction;
		}
		Arrays.sort(downhillFirst, Comparator.comparingDouble(direction -> near[direction]));

		double[] extended = new double[FOOT_LOWEST_DIRECTIONS];
		for (int i = 0; i < FOOT_LOWEST_DIRECTIONS; i++) {
			int direction = downhillFirst[i];
			extended[i] = extendToFoot(elevation, near[direction], far[direction]);
		}
		Arrays.sort(extended);
		return extended[FOOT_LOWEST_DIRECTIONS / 2];
	}

	/**
	 * Extends the slope between the ground downhill near and far back to the point, which holds
	 * on flat ground as on a steep approach. Never higher than the point reads, which also covers
	 * ground dropping away again within reach, such as below a ledge, nor lower than near.
	 *
	 * @param elevation the elevation the point reads
	 * @param near      the elevation FOOT_NEAR_METERS downhill
	 * @param far       the elevation FOOT_FAR_METERS downhill, in the same direction
	 */
	static double extendToFoot(double elevation, double near, double far) {
		double extended = near
				+ (near - far) * FOOT_NEAR_METERS / (FOOT_FAR_METERS - FOOT_NEAR_METERS);
		return Math.min(elevation, Math.max(near, extended));
	}

	/**
	 * @return a number that changes whenever lookups may return something else than before
	 */
	public int getVersion() {
		return version;
	}

	private double sample(double latitude, double longitude, int zoom) {
		double[] tileCoordinates = toTileCoordinates(latitude, longitude, zoom);
		int x = (int) Math.floor(tileCoordinates[0]);
		int y = (int) Math.floor(tileCoordinates[1]);
		float[] tile = getTile(zoom, x, y);
		if (tile == null) {
			return Double.NaN;
		}
		return interpolate(tile, (tileCoordinates[0] - x) * TILE_SIZE,
				(tileCoordinates[1] - y) * TILE_SIZE);
	}

	private float[] getTile(int zoom, int x, int y) {
		long key = tileKey(zoom, x, y);
		float[] tile = tiles.get(key);
		if (tile != null || loadingTiles.contains(key) || missingTiles.contains(key)) {
			return tile;
		}
		Long failedAt = failedTiles.get(key);
		if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < RETRY_DELAY_MS) {
			return null;
		}

		loadTile(zoom, x, y, key);
		return null;
	}

	private void loadTile(int zoom, int x, int y, long key) {
		loadingTiles.add(key);
		Constants.WEB_EXECUTOR.execute(new UiRelatedTask<LoadedTile>() {
			@Override
			protected LoadedTile doWork() {
				return fetchTile(zoom, x, y);
			}

			@Override
			protected void thenDoUiRelatedWork(LoadedTile result) {
				loadingTiles.remove(key);
				if (result == null) {
					failedTiles.put(key, SystemClock.elapsedRealtime());
				} else if (result.elevations == null) {
					missingTiles.add(key);
					version++;
				} else {
					failedTiles.remove(key);
					tiles.put(key, result.elevations);
					version++;
				}
			}
		});
	}

	/**
	 * Runs on a background thread.
	 *
	 * @return the tile, a tile without elevations when the zoom has no data there, or null when
	 * it could not be fetched
	 */
	private LoadedTile fetchTile(int zoom, int x, int y) {
		File file = new File(cacheDirectory, zoom + "/" + x + "/" + y + ".webp");
		try {
			byte[] data;
			if (file.exists()) {
				data = Files.readAllBytes(file.toPath());
			} else {
				Request request = new Request.Builder()
						.url(TILE_URL.replace("{z}", String.valueOf(zoom))
								.replace("{x}", String.valueOf(x))
								.replace("{y}", String.valueOf(y)))
						.header("User-Agent", "ClimbTheWorld/" + Globals.versionName)
						.build();
				try (Response response = httpClient.newCall(request).execute()) {
					if (response.code() == 404) {
						return new LoadedTile(null);
					}
					if (!response.isSuccessful() || response.body() == null) {
						return null;
					}
					data = response.body().bytes();
				}
				File directory = file.getParentFile();
				if (directory != null && (directory.isDirectory() || directory.mkdirs())) {
					// Renamed into place, so an interrupted write never leaves a partial tile.
					File partialFile = new File(directory, file.getName() + ".part");
					Files.write(partialFile.toPath(), data);
					partialFile.renameTo(file);
				}
			}

			float[] elevations = decode(data);
			if (elevations == null) {
				// A corrupt cached file would otherwise fail every time.
				file.delete();
				return null;
			}
			return new LoadedTile(elevations);
		} catch (IOException e) {
			Log.d(TAG, "Terrain tile " + zoom + "/" + x + "/" + y + " not loaded", e);
			return null;
		}
	}

	private record LoadedTile(float[] elevations) {
	}
}
