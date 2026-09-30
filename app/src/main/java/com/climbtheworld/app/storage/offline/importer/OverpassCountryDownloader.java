package com.climbtheworld.app.storage.offline.importer;

import android.content.Context;

import com.climbtheworld.app.storage.OsmUtils;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Downloads a country's Overpass response and streams it into the offline database. */
public class OverpassCountryDownloader {
	private static final AtomicInteger NEXT_API_INDEX = new AtomicInteger();
	private static final int MAX_ATTEMPTS = 3;
	private static final long INITIAL_RETRY_DELAY_MILLIS = 2_000L;
	private static final long MAX_RETRY_AFTER_MILLIS = 30_000L;
	private final OkHttpClient httpClient;

	public OverpassCountryDownloader() {
		this(new OkHttpClient.Builder()
				.connectTimeout(Constants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(Constants.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build());
	}

	OverpassCountryDownloader(OkHttpClient httpClient) {
		this.httpClient = httpClient;
	}

	public long downloadAndImport(Context context, String countryIso) throws IOException, JSONException {
		if (!Globals.allowDataDownload(context)) {
			throw new IOException("Data downloads are disabled");
		}

		RequestBody body = new FormBody.Builder()
				.add("data", OsmUtils.buildCountryQuery(countryIso))
				.build();
		IOException lastFailure = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Request request = new Request.Builder()
					.url(nextApiUrl())
					.header("User-Agent", "ClimbTheWorld/" + Globals.versionName)
					.header("Referer", "https://github.com/xyz-relativity/ClimbTheWorld-Android")
					.post(body)
					.build();
			long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS << attempt;
			boolean shouldRetry;
			try (Response response = httpClient.newCall(request).execute()) {
				if (response.isSuccessful() && response.body() != null) {
					return new CountryOsmImporter(AppDatabase.getInstance(context))
							.importResponse(response.body().charStream(), countryIso);
				}

				lastFailure = new IOException("Overpass request failed: " + response.code() + " " +
						response.message());
				shouldRetry = isRetryable(response.code());
				retryDelayMillis = retryDelayMillis(response, attempt);
			} catch (IOException exception) {
				lastFailure = exception;
				shouldRetry = true;
			}
			if (!shouldRetry || attempt == MAX_ATTEMPTS - 1) {
				break;
			}
			sleepBeforeRetry(retryDelayMillis);
		}
		throw lastFailure != null ? lastFailure : new IOException("Overpass request failed");
	}

	private static boolean isRetryable(int statusCode) {
		return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
	}

	private static long retryDelayMillis(Response response, int attempt) {
		String retryAfter = response.header("Retry-After");
		if (retryAfter != null) {
			try {
				long seconds = Long.parseLong(retryAfter.trim());
				return Math.min(Math.max(seconds, 0L) * 1_000L, MAX_RETRY_AFTER_MILLIS);
			} catch (NumberFormatException ignored) {
				// Use the bounded exponential backoff when Retry-After is an HTTP date.
			}
		}
		return INITIAL_RETRY_DELAY_MILLIS << attempt;
	}

	private static void sleepBeforeRetry(long delayMillis) throws IOException {
		try {
			Thread.sleep(delayMillis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Overpass retry interrupted", exception);
		}
	}

	private String nextApiUrl() {
		int index = Math.floorMod(NEXT_API_INDEX.getAndIncrement(), Constants.OVERPASS_API.length);
		return Constants.OVERPASS_API[index];
	}
}
