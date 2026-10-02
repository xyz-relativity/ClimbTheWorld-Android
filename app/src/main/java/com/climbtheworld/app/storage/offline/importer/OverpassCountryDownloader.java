package com.climbtheworld.app.storage.offline.importer;

import android.content.Context;
import android.util.Log;

import com.climbtheworld.app.storage.OsmUtils;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads a country's Overpass response and streams it into the offline database. */
public class OverpassCountryDownloader {
	/**
	 * Receives download progress in percent, from the downloading thread or a timer thread, never
	 * concurrently and never after {@link #downloadAndImport} returns.
	 */
	public interface ProgressListener {
		ProgressListener NONE = percent -> {
		};

		/** @param percent 1..99; see {@link DownloadProgressTracker} for the phase ranges. */
		void onProgress(int percent);
	}

	private static final String TAG = OverpassCountryDownloader.class.getSimpleName();
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
		return downloadAndImport(context, countryIso, ProgressListener.NONE);
	}

	public long downloadAndImport(Context context, String countryIso, ProgressListener progress)
			throws IOException, JSONException {
		if (!Globals.allowDataDownload(context)) {
			throw new DownloadsDisabledException();
		}

		String query = OsmUtils.buildCountryQuery(countryIso);
		RequestBody body = new FormBody.Builder()
				.add("data", query)
				.build();
		try (DownloadProgressTracker tracker = DownloadProgressTracker.start(progress,
				TimeUnit.SECONDS.toMillis(Constants.HTTP_TIMEOUT_SECONDS))) {
			return downloadAndImport(context, countryIso, query, body, tracker);
		}
	}

	private long downloadAndImport(Context context, String countryIso, String query,
	                               RequestBody body, DownloadProgressTracker tracker)
			throws IOException, JSONException {
		IOException lastFailure = null;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String apiUrl = nextApiUrl();
			Log.d(TAG, "Overpass query for " + countryIso + " to " + apiUrl
					+ " (attempt " + (attempt + 1) + "/" + MAX_ATTEMPTS + "):\n" + query);
			Request request = new Request.Builder()
					.url(apiUrl)
					.header("User-Agent", "ClimbTheWorld/" + Globals.versionName)
					.header("Referer", "https://github.com/xyz-relativity/ClimbTheWorld-Android")
					.post(body)
					.build();
			long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS << attempt;
			boolean shouldRetry;
			tracker.startAttempt();
			try (Response response = httpClient.newCall(request).execute()) {
				ResponseBody responseBody = response.body();
				if (response.isSuccessful() && responseBody != null) {
					InputStream countingStream = new CountingInputStream(responseBody.byteStream(),
							responseBody.contentLength(), tracker);
					return new CountryOsmImporter(AppDatabase.getInstance(context)).importResponse(
							new InputStreamReader(countingStream, StandardCharsets.UTF_8), countryIso,
							tracker::onWriting);
				}

				lastFailure = new OverpassHttpException(response.code(), response.message());
				shouldRetry = OverpassHttpException.isServerBusy(response.code());
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

	/** Reports the bytes the importer has read from the response body. */
	private static final class CountingInputStream extends FilterInputStream {
		private final long contentLength;
		private final DownloadProgressTracker tracker;
		private long bytesRead;

		CountingInputStream(InputStream in, long contentLength, DownloadProgressTracker tracker) {
			super(in);
			this.contentLength = contentLength;
			this.tracker = tracker;
		}

		@Override
		public int read() throws IOException {
			int value = super.read();
			if (value >= 0) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int read = super.read(buffer, offset, length);
			if (read > 0) {
				count(read);
			}
			return read;
		}

		@Override
		public long skip(long count) throws IOException {
			long skipped = super.skip(count);
			if (skipped > 0) {
				count(skipped);
			}
			return skipped;
		}

		private void count(long bytes) {
			bytesRead += bytes;
			tracker.onBytesRead(bytesRead, contentLength);
		}
	}
}
