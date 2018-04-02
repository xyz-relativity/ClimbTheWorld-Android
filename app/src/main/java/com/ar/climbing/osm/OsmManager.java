package com.ar.climbing.osm;

import android.app.Activity;
import android.app.Dialog;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.oauth.OAuthHelper;
import com.ar.climbing.storage.DataManager;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;

public class OsmManager {
    private OkHttpClient httpClient = new OkHttpClient();
    private Activity parent;

    public OsmManager (Activity parent) {
        this.parent = parent;
    }

    public void pushData(final List<Long> toChange, final Dialog status) {
        (new Thread() {
            public void run() {
                DataManager dataMgr = new DataManager();
                Map<Long, GeoNode> poiMap = new HashMap<>();
                dataMgr.downloadIDs(toChange, poiMap);
                parent.runOnUiThread(new Thread() {
                    public void run() {
                        ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Creating new change set.");
                    }
                });

                Request request = new Request.Builder()
                        .url(Constants.DEFAULT_API + "permissions")
                        .get()
                        .build();

                OAuthHelper oa = new OAuthHelper();
                OkHttpOAuthConsumer consumer = oa.getConsumer(OAuthHelper.getBaseUrl(Constants.DEFAULT_API));
                consumer.setTokenWithSecret(Globals.oauthToken, Globals.oauthSecret);

                OkHttpClient.Builder builder = httpClient.newBuilder().connectTimeout(45, TimeUnit.SECONDS).readTimeout(45,
                        TimeUnit.SECONDS);

                OkHttpClient client = builder.build();

                try {
                    Request signedRequest = (Request) consumer.sign(request).unwrap();

                    System.out.println("non sign " + Arrays.toString(request.headers().names().toArray()));
                    System.out.println("sign " + Arrays.toString(signedRequest.headers().names().toArray()));

                    System.out.println("auth " + signedRequest.headers().get("Authorization"));

                    Response response = client.newCall(signedRequest).execute();
                    System.out.println(response.body().string());
                } catch (OAuthMessageSignerException | OAuthExpectationFailedException | OAuthCommunicationException | IOException e) {
                    e.printStackTrace();
                }

                status.dismiss();
            }
        }).start();
    }
}
