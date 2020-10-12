package com.climbtheworld.app.oauth;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.climbtheworld.app.ClimbTheWorld;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.Constants;

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
	private OAuthConsumer mConsumer;
	private OAuthProvider mProvider;
	private String mCallbackUrl;

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

	private OAuthHelper() throws OAuthException {
		String[] data = getKeyAndSecret(OAUTH_API);
		mConsumer = new OkHttpOAuthConsumer(data[0], data[1]);
		mProvider = new OkHttpOAuthProvider(OAUTH_API.oAuthUrl + "oauth/request_token",
				OAUTH_API.oAuthUrl + "oauth/access_token",
				OAUTH_API.oAuthUrl + "oauth/authorize");
		mProvider.setOAuth10a(true);
		mCallbackUrl = OAUTH_PATH;
	}

	public static synchronized OAuthHelper getInstance() throws OAuthException {
		if (helper == null) {
			helper = new OAuthHelper();
		}

		return helper;
	}

	private String[] getKeyAndSecret(Constants.OSM_API oAuth) throws OAuthException {
		ApplicationInfo ai = null;
		try {
			ai = ClimbTheWorld.getContext().getPackageManager().getApplicationInfo(ClimbTheWorld.getContext().getPackageName(), PackageManager.GET_META_DATA);
		} catch (PackageManager.NameNotFoundException e) {
			throw new OAuthCommunicationException(e);
		}
		Bundle bundle = ai.metaData;
		String[] data = new String[2];
		data[0] = bundle.getString(oAuth.name() + ".KEY");
		data[1] = bundle.getString(oAuth.name() + ".SECRET");

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
					return mProvider.retrieveRequestToken(mConsumer, mCallbackUrl);
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
		if (mProvider == null || mConsumer == null) {
			throw new OAuthExpectationFailedException("OAuthHelper not initialized!");
		}
		mProvider.retrieveAccessToken(mConsumer, verifier);
		return new String[]{mConsumer.getToken(), mConsumer.getTokenSecret()};
	}

	public static String getBaseUrl(String url) {
		return url.replaceAll("/api/[0-9]+(?:\\.[0-9]+)+/?$", "/");
	}
}
