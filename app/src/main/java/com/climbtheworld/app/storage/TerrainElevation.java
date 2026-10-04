package com.climbtheworld.app.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
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
 * Prototype: not tied into the offline downloads yet, and the Mapterhorn attribution is not shown.
 * Only to be used from the main thread.
 */
public class TerrainElevation {
	private static final String TAG = TerrainElevation.class.getSimpleName();
	private static final String TILE_URL = "https://tiles.mapterhorn.com/%d/%d/%d.webp";
	private static final int TILE_SIZE = 512;
	private static final long TILE_TIMEOUT_SECONDS = 20;
	private static final long RETRY_DELAY_MS = 60_000;
	// Each decoded tile takes 1 MB.
	private static final int MAX_CACHED_TILES = 12;

	// Available everywhere.
	public static final int BASE_ZOOM = 12;
	// Only where detailed data exists; elsewhere lookups fall back to the base zoom.
	public static final int DETAIL_ZOOM = 14;

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

	public TerrainElevation(Context context) {
		cacheDirectory = new File(context.getCacheDir(), "terrain");
		httpClient = new OkHttpClient.Builder()
				.connectTimeout(TILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(TILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build();
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
				} else {
					failedTiles.remove(key);
					tiles.put(key, result.elevations);
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
						.url(String.format(Locale.ROOT, TILE_URL, zoom, x, y))
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

	private record LoadedTile(float[] elevations) {
	}
}
