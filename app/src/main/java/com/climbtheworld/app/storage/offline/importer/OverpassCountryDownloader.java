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
		Request request = new Request.Builder()
				.url(nextApiUrl())
				.header("User-Agent", "ClimbTheWorld/" + Globals.versionName)
				.header("Referer", "https://github.com/xyz-relativity/ClimbTheWorld-Android")
				.post(body)
				.build();

		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful() || response.body() == null) {
				throw new IOException("Overpass request failed: " + response.code() + " " +
						response.message());
			}
			return new CountryOsmImporter(AppDatabase.getInstance(context))
					.importResponse(response.body().charStream(), countryIso);
		}
	}

	private String nextApiUrl() {
		int index = Math.floorMod(NEXT_API_INDEX.getAndIncrement(), Constants.OVERPASS_API.length);
		return Constants.OVERPASS_API[index];
	}
}
