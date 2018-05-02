package com.ar.climbing.oauth;

import android.os.AsyncTask;
import android.util.Log;

import com.ar.climbing.utils.Constants;

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
    public static final String OAUTH_PATH = "xyzdroid:/oauth/";

    public OAuthHelper(Constants.OSM_API oAuth) throws OAuthException {
        mConsumer = new OkHttpOAuthConsumer(oAuth.consumerKey, oAuth.consumerSecret);
        mProvider = new OkHttpOAuthProvider(oAuth.oAuthUrl + "oauth/request_token",
                oAuth.oAuthUrl + "oauth/access_token",
                oAuth.oAuthUrl + "oauth/authorize");
        mProvider.setOAuth10a(true);
        mCallbackUrl = OAUTH_PATH;
        return;
    }

    /**
     * this constructor is for access to the singletons
     */
    public OAuthHelper() {
    }

    /**
     * Returns an OAuthConsumer initialized with the consumer keys for the API in question
     * 
     * @param oAuth
     * @return an initialized OAuthConsumer
     */
    public OkHttpOAuthConsumer getConsumer(Constants.OSM_API oAuth) {
        return new OkHttpOAuthConsumer(oAuth.consumerKey, oAuth.consumerSecret);
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
