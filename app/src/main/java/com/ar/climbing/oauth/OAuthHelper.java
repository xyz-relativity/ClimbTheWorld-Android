package com.ar.climbing.oauth;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;

import com.ar.climbing.R;
import com.ar.climbing.utils.Globals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthProvider;

public class OAuthHelper {
    private static OAuthConsumer mConsumer;
    private static OAuthProvider mProvider;
    private static String mCallbackUrl;

    //this two fields as used in the MainActivity: com.ar.climbing.activitys.MainActivity.initializeGlobals()
    public static final String OAUTH_PATH = "xyzDroid:/oauth/";

    public OAuthHelper(String osmBaseUrl) throws OAuthException {
        Resources resources = Globals.baseContext.getResources();
        String urls[] = resources.getStringArray(R.array.api_urls);
        String keys[] = resources.getStringArray(R.array.api_consumer_keys);
        String secrets[] = resources.getStringArray(R.array.api_consumer_secrets);
        String oauth_urls[] = resources.getStringArray(R.array.api_oauth_urls);

        for (int i = 0; i < urls.length; i++) {
            if (urls[i].equalsIgnoreCase(osmBaseUrl)) {
                mConsumer = new OkHttpOAuthConsumer(keys[i], secrets[i]);
                mProvider = new OkHttpOAuthProvider(oauth_urls[i] + "oauth/request_token",
                        oauth_urls[i] + "oauth/access_token",
                        oauth_urls[i] + "oauth/authorize");
                mProvider.setOAuth10a(true);
                mCallbackUrl = OAUTH_PATH;
                return;
            }
        }
        throw new OAuthException("No matching OAuth configuration found for this API");
    }

    /**
     * this constructor is for access to the singletons
     */
    public OAuthHelper() {
    }

    /**
     * Returns an OAuthConsumer initialized with the consumer keys for the API in question
     * 
     * @param osmBaseUrl
     * @return an initialized OAuthConsumer
     */
    public OkHttpOAuthConsumer getConsumer(String osmBaseUrl) {
        Resources resources = Globals.baseContext.getResources();
        String urls[] = resources.getStringArray(R.array.api_urls);
        String keys[] = resources.getStringArray(R.array.api_consumer_keys);
        String secrets[] = resources.getStringArray(R.array.api_consumer_secrets);
        for (int i = 0; i < urls.length; i++) {
            if (urls[i].equalsIgnoreCase(osmBaseUrl)) {
                return new OkHttpOAuthConsumer(keys[i], secrets[i]);
            }
        }
        // TODO protect against failure
        return null;
    }

    /**
     * Get the request token
     * 
     * @return the token or null
     * @throws oauth.signpost.exception.OAuthException if an error happened during the OAuth handshake
     * @throws TimeoutException if we waited too long for a response
     * @throws ExecutionException
     */
    public String getRequestToken() throws oauth.signpost.exception.OAuthException, TimeoutException, ExecutionException {
        class RequestTokenTask extends AsyncTask<Void, Void, String> {
            private oauth.signpost.exception.OAuthException ex = null;

            @Override
            protected String doInBackground(Void... params) {
                try {
                    return mProvider.retrieveRequestToken(mConsumer, mCallbackUrl);
                } catch (oauth.signpost.exception.OAuthException e) {
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
            private oauth.signpost.exception.OAuthException getException() {
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
            oauth.signpost.exception.OAuthException ex = requester.getException();
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
        return new String[] { mConsumer.getToken(), mConsumer.getTokenSecret() };
    }

    public static String getBaseUrl(String url) {
        return url.replaceAll("/api/[0-9]+(?:\\.[0-9]+)+/?$", "/");
    }
}