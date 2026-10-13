package com.climbtheworld.app.oauth;

import android.content.Context;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** The OpenStreetMap login: which server, the app's OAuth 2 client, and the stored token. */
public final class OAuthHelper {
	/** The OSM server the app logs in to and uploads to. */
	public static final Constants.OSM_API API = Constants.DEFAULT_API;
	private static final int REQUEST_TIMEOUT_SECONDS = 60;
	private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded");

	private OAuthHelper() {
	}

	public static boolean needsAuthentication(Configs configs) {
		String token = getToken(configs);
		return token == null || token.isEmpty();
	}

	public static String getToken(Configs configs) {
		return configs.getString(Configs.ConfigKey.oauth2Token);
	}

	public static void saveToken(Configs configs, String token) {
		configs.setString(Configs.ConfigKey.oauth2Token, token);
	}

	/** Forgets the token, so the next upload logs in again. */
	public static void resetOauth(Configs configs) {
		configs.setString(Configs.ConfigKey.oauth2Token, null);
	}

	/** The app's public OAuth 2 client id, registered on the OSM server; empty when missing. */
	public static String clientId(Context context) {
		return context.getString(API == Constants.OSM_API.OSM_0_6_API
				? R.string.OSM_OAUTH2_CLIENT_ID : R.string.OSM_SANDBOX_OAUTH2_CLIENT_ID).trim();
	}

	/** Trades the code of a finished login for a token. Blocks, so it runs off the UI thread. */
	public static String exchangeCode(Context context, String code, String verifier)
			throws IOException, JSONException {
		OkHttpClient client = new OkHttpClient.Builder()
				.connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.build();
		Request request = new Request.Builder()
				.url(OsmOAuth2.tokenUrl(API.oAuthUrl))
				.header("User-Agent", "ClimbTheWorld/" + Globals.versionName)
				.post(RequestBody.create(OsmOAuth2.tokenRequestBody(clientId(context), code, verifier), FORM))
				.build();
		try (Response response = client.newCall(request).execute()) {
			String body = response.body().string();
			if (!response.isSuccessful()) {
				throw new IOException("OSM refused the login: " + body);
			}
			return OsmOAuth2.accessToken(body);
		}
	}
}
