package com.climbtheworld.app.oauth;

import android.os.AsyncTask;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.Constants;

import org.osmdroid.tileprovider.util.ManifestUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthProvider;

public class OAuthHelper {
	private static OAuthHelper helper = null;
	private final AppCompatActivity parent;
	private final OAuthConsumer oAuthConsumer;
	private final OAuthProvider oAuthProvider;

	//this two fields as used in the MainActivity: com.ar.climbing.activitys.MainActivity.initializeGlobals()
	public static final String OAUTH_PATH = "climbtheworld://oauth/";

	//oAUth API to use.
	private static final Constants.OSM_API OAUTH_API = Constants.DEFAULT_API;

	public static void resetOauth(Configs configs) {
		configs.setString(Configs.ConfigKey.oauthToken, null);
		configs.setString(Configs.ConfigKey.oauthVerifier, null);
	}

	public static String getToken(Configs configs) {
		return configs.getString(Configs.ConfigKey.oauthToken);
	}

	public static String getSecret(Configs configs) {
		return configs.getString(Configs.ConfigKey.oauthVerifier);
	}

	public static boolean needsAuthentication(Configs configs) {
		return (configs.getString(Configs.ConfigKey.oauthToken) == null
				|| configs.getString(Configs.ConfigKey.oauthVerifier) == null);
	}

	private OAuthHelper(AppCompatActivity parent) throws OAuthException {
		this.parent = parent;
		String[] data = getKeyAndSecret(OAUTH_API);
		oAuthConsumer = new OkHttpOAuthConsumer(data[0], data[1]);
		oAuthProvider = new OkHttpOAuthProvider(OAUTH_API.oAuthUrl + "oauth/request_token",
				OAUTH_API.oAuthUrl + "oauth/access_token",
				OAUTH_API.oAuthUrl + "oauth/authorize");
		oAuthProvider.setOAuth10a(true);
	}

	public static synchronized OAuthHelper getInstance(AppCompatActivity parent) throws OAuthException {
		if (helper == null) {
			helper = new OAuthHelper(parent);
		}

		return helper;
	}

	private String[] getKeyAndSecret(Constants.OSM_API oAuth) throws OAuthException {
		String[] data = new String[2];
		data[0] = ManifestUtil.retrieveKey(parent,oAuth.name() + ".KEY");
		data[1] = ManifestUtil.retrieveKey(parent,oAuth.name() + ".SECRET");

		return data;
	}

	/**
	 * Returns an OAuthConsumer initialized with the consumer keys for the API in question
	 *
	 * @param oAuth
	 * @return an initialized OAuthConsumer
	 */
	public OkHttpOAuthConsumer getConsumer(Constants.OSM_API oAuth) throws OAuthException {
		String[] data = getKeyAndSecret(oAuth);
		return new OkHttpOAuthConsumer(data[0], data[1]);
	}

	/**
	 * Get the request token
	 *
	 * @return the token or null
	 * @throws OAuthException     if an error happened during the OAuth handshake
	 * @throws TimeoutException   if we waited too long for a response
	 * @throws ExecutionException
	 */
	public String getRequestToken() throws OAuthException, TimeoutException, ExecutionException {
		class RequestTokenTask extends AsyncTask<Void, Void, String> {
			private OAuthException ex = null;

			@Override
			protected String doInBackground(Void... params) {
				try {
					return oAuthProvider.retrieveRequestToken(oAuthConsumer, OAUTH_PATH);
				} catch (OAuthException e) {
					Log.d("OAuthHelper", "getRequestToken " + e);
					ex = e;
				}
				return null;
			}

			/**
			 * Get the any OAuthException that was thrown
			 *
			 * @return the exception
			 */
			private OAuthException getException() {
				return ex;
			}
		}
		RequestTokenTask requester = new RequestTokenTask();
		requester.execute();
		String result = null;
		try {
			result = requester.get(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) { // NOSONAR cancel does interrupt the thread in question
			requester.cancel(true);
			throw new TimeoutException(e.getMessage());
		}
		if (result == null) {
			OAuthException ex = requester.getException();
			if (ex != null) {
				throw ex;
			}
		}
		return result;
	}

	/**
	 * Queries the service provider for an access token.
	 *
	 * @param verifier
	 * @return
	 * @throws OAuthMessageSignerException
	 * @throws OAuthNotAuthorizedException
	 * @throws OAuthExpectationFailedException
	 * @throws OAuthCommunicationException
	 */
	public String[] getAccessToken(String verifier)
			throws OAuthMessageSignerException, OAuthNotAuthorizedException, OAuthExpectationFailedException, OAuthCommunicationException {
		Log.d("OAuthHelper", "verifier: " + verifier);
		if (oAuthProvider == null || oAuthConsumer == null) {
			throw new OAuthExpectationFailedException("OAuthHelper not initialized!");
		}
		oAuthProvider.retrieveAccessToken(oAuthConsumer, verifier);
		return new String[]{oAuthConsumer.getToken(), oAuthConsumer.getTokenSecret()};
	}

	public static String getBaseUrl(String url) {
		return url.replaceAll("/api/[0-9]+(?:\\.[0-9]+)+/?$", "/");
	}
}
