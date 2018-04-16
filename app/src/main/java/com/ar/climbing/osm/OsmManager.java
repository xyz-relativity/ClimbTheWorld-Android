package com.ar.climbing.osm;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.TextView;

import com.ar.climbing.R;
import com.ar.climbing.oauth.OAuthHelper;
import com.ar.climbing.storage.DataManager;
import com.ar.climbing.storage.database.GeoNode;
import com.ar.climbing.utils.Constants;
import com.ar.climbing.utils.Globals;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;

public class OsmManager {
    private enum OSM_PERMISSIONS {
        allow_write_api
    }
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
                        ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Checking permissions.");
                    }
                });

                Request request = new Request.Builder()
                        .url(Constants.DEFAULT_API + "/permissions")
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

                    Response response = client.newCall(signedRequest).execute();

                    if (!hasPermission(OSM_PERMISSIONS.allow_write_api, response.body().string())) {
                        Globals.showErrorDialog(parent, "You do not have write permissions on OpenStreetMaps.", null);
                        status.dismiss();
                        return;
                    }

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Creating change set.");
                        }
                    });

                    PackageInfo pInfo = parent.getPackageManager().getPackageInfo(parent.getPackageName(), 0);
                    String version = pInfo.versionName;

                    RequestBody body = RequestBody.create(MediaType.parse("xml"),
                            "<osm>\n" +
                            "  <changeset>\n" +
                            "    <tag k=\"created_by\" v=\"" + parent.getString(R.string.app_name) + " " + version + "\"/>\n" +
                            "    <tag k=\"comment\" v=\"Test change set\"/>\n" +
                            "  </changeset>\n" +
                            "</osm>");

                    request = new Request.Builder()
                            .url(Constants.DEFAULT_API + "/changeset/create")
                            .put(body)
                            .build();

                    signedRequest = (Request) consumer.sign(request).unwrap();

                    response = client.newCall(signedRequest).execute();
                    long changeSetID = Long.parseLong(response.body().string());
                    System.out.println("changesetID: " + changeSetID);

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Read change set.");
                        }
                    });

                    request = new Request.Builder()
                            .url(Constants.DEFAULT_API + "/changeset/"+ changeSetID)
                            .get()
                            .build();

                    signedRequest = (Request) consumer.sign(request).unwrap();

                    response = client.newCall(signedRequest).execute();
                    System.out.println(response.body().string());

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Closing change set.");
                        }
                    });

                    body = RequestBody.create(MediaType.parse("text"), "");

                    request = new Request.Builder()
                            .url(Constants.DEFAULT_API + "/changeset/"+ changeSetID +"/close")
                            .put(body)
                            .build();

                    signedRequest = (Request) consumer.sign(request).unwrap();

                    response = client.newCall(signedRequest).execute();
                    System.out.println(response.body().string());

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView)status.getWindow().findViewById(R.id.dialogMessage)).setText("Done.");
                        }
                    });

                } catch (OAuthMessageSignerException
                        | OAuthExpectationFailedException
                        | OAuthCommunicationException
                        | IOException
                        | XmlPullParserException
                        | PackageManager.NameNotFoundException e) {
                    Globals.showErrorDialog(status.getContext(), e.getMessage(), null);
                }

                status.dismiss();
            }
        }).start();
    }

    private boolean hasPermission(OSM_PERMISSIONS osmPermission, String xmlString) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xmlString));
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {

            if (eventType == XmlPullParser.START_TAG) {

                String name = parser.getName();
                if(name.equals("permission")) {

                    String ref = parser.getAttributeValue(null, "name");
                    if (ref.equalsIgnoreCase(osmPermission.toString())) {
                        return true;
                    }
                }

            } else if(eventType == XmlPullParser.END_TAG) {
                //nothing to do
            }
            eventType = parser.next();

        }

        return false;
    }

}
