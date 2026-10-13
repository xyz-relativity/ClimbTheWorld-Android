package com.climbtheworld.app.storage.upload;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** The calls of the OSM API 0.6 an upload makes, as the logged in user. Calls block. */
public class OsmApi {
	/** Ids per multi-fetch, well within the length of a URL. */
	private static final int FETCH_CHUNK_SIZE = 200;
	private static final int REQUEST_TIMEOUT_SECONDS = 120;
	private static final MediaType XML = MediaType.parse("text/xml; charset=utf-8");
	private static final String WRITE_PERMISSION = "allow_write_api";

	/** An error answer of the API; its message is OSM's explanation. */
	public static class OsmApiException extends IOException {
		public final int code;

		OsmApiException(int code, String message) {
			super(message);
			this.code = code;
		}
	}

	private final String apiUrl;
	private final OkHttpClient client;

	/** @param apiUrl like https://api.openstreetmap.org/api/0.6 */
	public OsmApi(String apiUrl, String accessToken, String userAgent) {
		this.apiUrl = apiUrl;
		this.client = new OkHttpClient.Builder()
				.connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
						.header("Authorization", "Bearer " + accessToken)
						.header("User-Agent", userAgent)
						.build()))
				.build();
	}

	/** @return whether the login lets the app change the map. */
	public boolean canWrite() throws IOException {
		try {
			JSONArray permissions = new JSONObject(execute(new Request.Builder()
					.url(apiUrl + "/permissions.json").get().build()))
					.optJSONArray("permissions");
			for (int index = 0; permissions != null && index < permissions.length(); index++) {
				if (WRITE_PERMISSION.equals(permissions.optString(index))) {
					return true;
				}
			}
			return false;
		} catch (JSONException e) {
			throw new IOException(e);
		}
	}

	/**
	 * The current versions of elements of one type, by PendingChange key. Deleted ones come with
	 * visible false.
	 */
	public Map<String, JSONObject> fetch(String type, List<Long> ids) throws IOException {
		Map<String, JSONObject> result = new HashMap<>();
		for (int start = 0; start < ids.size(); start += FETCH_CHUNK_SIZE) {
			StringBuilder idList = new StringBuilder();
			for (Long id : ids.subList(start, Math.min(ids.size(), start + FETCH_CHUNK_SIZE))) {
				idList.append(idList.length() > 0 ? "," : "").append(id);
			}
			String body = execute(new Request.Builder()
					.url(apiUrl + "/" + type + "s.json?" + type + "s=" + idList).get().build());
			try {
				JSONArray elements = new JSONObject(body).optJSONArray("elements");
				for (int index = 0; elements != null && index < elements.length(); index++) {
					JSONObject element = elements.getJSONObject(index);
					result.put(type + "|" + element.getLong("id"), element);
				}
			} catch (JSONException e) {
				throw new IOException(e);
			}
		}
		return result;
	}

	/** @return the new changeset's id. */
	public long createChangeset(Map<String, String> tags) throws IOException {
		String id = execute(new Request.Builder().url(apiUrl + "/changeset/create")
				.put(RequestBody.create(OsmChangeXml.changeset(tags), XML)).build());
		try {
			return Long.parseLong(id.trim());
		} catch (NumberFormatException e) {
			throw new IOException("Unexpected changeset id: " + id, e);
		}
	}

	/** Sends an osmChange document, which OSM applies all or nothing. */
	public DiffResult upload(long changesetId, String osmChange) throws IOException {
		return DiffResult.parse(execute(new Request.Builder()
				.url(apiUrl + "/changeset/" + changesetId + "/upload")
				.post(RequestBody.create(osmChange, XML)).build()));
	}

	public void closeChangeset(long changesetId) throws IOException {
		execute(new Request.Builder().url(apiUrl + "/changeset/" + changesetId + "/close")
				.put(RequestBody.create("", XML)).build());
	}

	private String execute(Request request) throws IOException {
		try (Response response = client.newCall(request).execute()) {
			String body = response.body().string();
			if (!response.isSuccessful()) {
				throw new OsmApiException(response.code(),
						body.isEmpty() ? response.message() : body);
			}
			return body;
		}
	}
}
