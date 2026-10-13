package com.climbtheworld.app.oauth;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The OAuth 2 authorization code flow with PKCE (RFC 7636) that openstreetmap.org takes from apps,
 * which can't keep a client secret: the app proves it started the login by showing the verifier
 * whose hash it sent first.
 */
public final class OsmOAuth2 {
	/** Registered with the OSM OAuth 2 application; the app's OAuthRedirectActivity takes it. */
	public static final String REDIRECT_URI = "climbtheworld://oauth";
	public static final String SCOPES = "read_prefs write_api";
	private static final int VERIFIER_BYTES = 48;

	private OsmOAuth2() {
	}

	/** A random secret of 64 URL-safe characters, kept until the code is exchanged. */
	public static String newSecret(SecureRandom random) {
		byte[] bytes = new byte[VERIFIER_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** The S256 challenge sent with the authorization request. */
	public static String codeChallenge(String verifier) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** @param serverUrl the OSM website, like https://www.openstreetmap.org/ */
	public static String authorizationUrl(String serverUrl, String clientId, String verifier,
	                                      String state) {
		return serverUrl + "oauth2/authorize"
				+ "?response_type=code"
				+ "&client_id=" + encode(clientId)
				+ "&redirect_uri=" + encode(REDIRECT_URI)
				+ "&scope=" + encode(SCOPES)
				+ "&state=" + encode(state)
				+ "&code_challenge=" + encode(codeChallenge(verifier))
				+ "&code_challenge_method=S256";
	}

	public static String tokenUrl(String serverUrl) {
		return serverUrl + "oauth2/token";
	}

	/** The form body exchanging the authorization code for a token. */
	public static String tokenRequestBody(String clientId, String code, String verifier) {
		return "grant_type=authorization_code"
				+ "&code=" + encode(code)
				+ "&redirect_uri=" + encode(REDIRECT_URI)
				+ "&client_id=" + encode(clientId)
				+ "&code_verifier=" + encode(verifier);
	}

	/** @return the access token of a token response; OSM's tokens don't expire. */
	public static String accessToken(String tokenResponse) throws JSONException {
		String token = new JSONObject(tokenResponse).optString("access_token");
		if (token.isEmpty()) {
			throw new JSONException("No access token in the response");
		}
		return token;
	}

	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
