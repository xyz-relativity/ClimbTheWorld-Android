package com.climbtheworld.app.osm;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.NodesDataManagerActivity;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.DialogBuilder;
import com.climbtheworld.app.utils.Globals;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import se.akerfeldt.okhttp.signpost.OkHttpOAuthConsumer;
import se.akerfeldt.okhttp.signpost.SigningInterceptor;

public class OsmManager {
    private enum OSM_PERMISSIONS {
        allow_write_api
    }

    private static final String API_URL = Constants.DEFAULT_API.apiUrl;

    private static final String PERMISSION_URL = API_URL + "/permissions";
    private static final String CHANGE_SET_CREATE_URL = API_URL + "/changeset/create";
    private static final String CHANGE_SET_CLOSE_URL = API_URL + "/changeset/%d/close";
    private static final String NODE_CREATE_URL = API_URL + "/node/create";
    private static final String NODE_GET_URL = API_URL + "/node/%d";
    private static final String NODE_UPDATE_URL = API_URL + "/changeset/%d/upload";
    private static final String NODE_DELETE_URL = API_URL + "/node/%d";

    private Activity parent;
    private OkHttpClient client;

    public OsmManager (Activity parent) throws PackageManager.NameNotFoundException {
        this.parent = parent;

        OkHttpClient httpClient = new OkHttpClient();
        OkHttpClient.Builder builder = httpClient.newBuilder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60,
                TimeUnit.SECONDS);
        OkHttpOAuthConsumer consumer = (new OAuthHelper()).getConsumer(Constants.DEFAULT_API);
        consumer.setTokenWithSecret(Globals.oauthToken, Globals.oauthSecret);
        builder.addInterceptor(new SigningInterceptor(consumer));
        client = builder.build();
    }

    public void pushData(final List<Long> toChange, final Dialog status) {
        (new Thread() {
            public void run() {
                Map<Long, GeoNode> updates;

                try {
                    Response response;

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.osm_permission_check);
                        }
                    });

                    if (!hasPermission(OSM_PERMISSIONS.allow_write_api)) {
                        DialogBuilder.showErrorDialog(parent, parent.getString(R.string.osm_permission_failed_message), null);
                        status.dismiss();
                        return;
                    }

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.osm_start_change_set);
                        }
                    });
                    response = client.newCall(buildCreateChangeSetRequest()).execute();
                    long changeSetID = Long.parseLong(response.body().string());

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.osm_pushing_data);
                        }
                    });
                    updates = pushNodes(changeSetID, toChange);

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.osm_commit_change_set);
                        }
                    });
                    response = client.newCall(buildCloseChangeSetRequest(changeSetID)).execute();

                    parent.runOnUiThread(new Thread() {
                        public void run() {
                            ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.success);
                        }
                    });

                } catch (JSONException
                        | IOException
                        | XmlPullParserException
                        | PackageManager.NameNotFoundException e) {
                    parent.runOnUiThread(new Thread() {
                                             public void run() {
                                                 DialogBuilder.showErrorDialog(status.getContext(), e.getMessage(), null);
                                             }
                                         });
                    e.printStackTrace();
                    updates = new HashMap<>();
                }

                parent.runOnUiThread(new Thread() {
                    public void run() {
                        ((TextView) status.getWindow().findViewById(R.id.dialogMessage)).setText(R.string.osm_updating_local_data);
                    }
                });
                for (Long nodeID : updates.keySet()) {
                    GeoNode originalNode = Globals.appDB.nodeDao().loadNode(nodeID);
                    GeoNode node = updates.get(nodeID);
                    if (node.localUpdateState == GeoNode.TO_DELETE_STATE) {
                        Globals.appDB.nodeDao().deleteNodes(originalNode);
                    } else {
                        Globals.appDB.nodeDao().deleteNodes(originalNode);
                        node.localUpdateState = GeoNode.CLEAN_STATE;
                        Globals.appDB.nodeDao().insertNodesWithReplace(node);
                    }
                }

                status.dismiss();
                Globals.showNotifications(parent);

                parent.runOnUiThread(new Thread() {
                    public void run() {
                        ((NodesDataManagerActivity) parent).pushTab();
                    }
                });
            }
        }).start();
    }

    private Request buildCreateChangeSetRequest() throws PackageManager.NameNotFoundException {
        PackageInfo pInfo = parent.getPackageManager().getPackageInfo(parent.getPackageName(), 0);
        String version = pInfo.versionName;

        RequestBody body = RequestBody.create(MediaType.parse("xml"),
                "<osm>\n" +
                        "  <changeset>\n" +
                        "    <tag k=\"created_by\" v=\"" + parent.getString(R.string.app_name) + " " + version + "\"/>\n" +
                        "    <tag k=\"comment\" v=\"Test change set\"/>\n" +
                        "  </changeset>\n" +
                        "</osm>");

        return new Request.Builder()
                .url(CHANGE_SET_CREATE_URL)
                .put(body)
                .build();
    }

    private Request buildCloseChangeSetRequest(long changeSetID) {
        RequestBody body = RequestBody.create(MediaType.parse("text"), "");

        return new Request.Builder()
                .url(String.format(Locale.getDefault(), CHANGE_SET_CLOSE_URL, changeSetID))
                .put(body)
                .build();
    }

    private Request buildGetPermissionRequest() {
        return new Request.Builder()
                .url(PERMISSION_URL)
                .get()
                .build();
    }

    private Request buildGetNodeRequest(long nodeID) {
        return new Request.Builder()
                .url(String.format(Locale.getDefault(), NODE_GET_URL, nodeID))
                .get()
                .build();
    }

    private Request buildGetChangeSetRequest(long changeSetID) {
        return new Request.Builder()
                .url(Constants.DEFAULT_API + "/changeset/"+ changeSetID)
                .get()
                .build();
    }

    private boolean hasPermission(OSM_PERMISSIONS osmPermission) throws XmlPullParserException, IOException {
        Response response = client.newCall(buildGetPermissionRequest()).execute();
        if (getValue("permission", "name", response.body().string()).equalsIgnoreCase(osmPermission.toString())) {
            return true;
        }
        return false;
    }

    private Map<Long, GeoNode> pushNodes(long changeSetID, List<Long> nodeIDs) throws IOException, XmlPullParserException, JSONException {
        Map<Long, GeoNode> updates = new HashMap<>();
        for (Long nodeID : nodeIDs) {
            GeoNode node = Globals.appDB.nodeDao().loadNode(nodeID);
            updates.put(nodeID, node);
            switch (node.localUpdateState) {
                case GeoNode.TO_UPDATE_STATE:
                    if (node.getID() < 0) {
                        createNode(changeSetID, node);
                    } else {
                        updateNode(changeSetID, node);
                    }
                    break;
                case GeoNode.TO_DELETE_STATE:
                    deleteNode(changeSetID, node);
                    break;
            }

        }

        return updates;
    }

    private void createNode(long changeSetID, GeoNode node) throws IOException, JSONException {
        RequestBody body = RequestBody.create(MediaType.parse("xml"),
                String.format(Locale.getDefault(),
                        "<osm>\n" +
                                " <node changeset=\"%d\" lat=\"%f\" lon=\"%f\">\n" +
                                "%s\n" +
                                " </node>\n" +
                                "</osm>", changeSetID, node.decimalLatitude, node.decimalLongitude, nodeJsonToXml(node.toJSONString())));

        Request request = new Request.Builder()
                .url(NODE_CREATE_URL)
                .put(body)
                .build();

        Response response = client.newCall(request).execute();
        node.osmID = Long.parseLong(response.body().string());
    }

    private void updateNode(long changeSetID, GeoNode node) throws IOException, XmlPullParserException, JSONException {
        Response response = client.newCall(buildGetNodeRequest(node.osmID)).execute();
        RequestBody body = RequestBody.create(MediaType.parse("xml"),
                String.format(Locale.getDefault(),
                        "<osmChange version=\"0.6\" generator=\"acme osm editor\">\n" +
                                "    <modify>\n" +
                                "        <node id=\"%d\" changeset=\"%d\" version=\"%s\" lat=\"%f\" lon=\"%f\">\n" +
                                "            %s" +
                                "        </node>\n" +
                                "    </modify>\n" +
                                "</osmChange>",
                        node.getID(),
                        changeSetID,
                        getValue("node", "version", response.body().string()),
                        node.decimalLatitude,
                        node.decimalLongitude,
                        nodeJsonToXml(node.toJSONString())));

        Request request = new Request.Builder()
                .url(String.format(Locale.getDefault(), NODE_UPDATE_URL, changeSetID))
                .post(body)
                .build();

        response = client.newCall(request).execute();
    }

    private void deleteNode(long changeSetID, GeoNode node)
            throws IOException, XmlPullParserException {
        Response response = client.newCall(buildGetNodeRequest(node.osmID)).execute();
        RequestBody body = RequestBody.create(MediaType.parse("xml"),
                String.format(Locale.getDefault(),
                        "<osm>\n" +
                                " <node id=\"%d\" changeset=\"%d\" version=\"%s\" lat=\"%f\" lon=\"%f\"/>\n" +
                                "</osm>", node.getID(), changeSetID, getValue("node", "version", response.body().string()), node.decimalLatitude, node.decimalLongitude));

        Request request = new Request.Builder()
                .url(String.format(Locale.getDefault(), NODE_DELETE_URL, node.getID()))
                .delete(body)
                .build();

        client.newCall(request).execute();
    }

    private String nodeJsonToXml(String json) throws JSONException {
        StringBuilder xmlTags = new StringBuilder();
        JSONObject jsonNodeInfo = new JSONObject(json);
        if (jsonNodeInfo.has(GeoNode.TAGS_KEY)) {
            JSONObject tags = jsonNodeInfo.getJSONObject(GeoNode.TAGS_KEY);
            for (int i = 0; i < tags.names().length(); ++i) {
                xmlTags.append(String.format("<tag k=\"%s\" v=\"%s\"/>\n", tags.names().getString(i), tags.getString(tags.names().getString(i))));
            }
        }
        return xmlTags.toString();
    }

    private String getValue(String field, String attribute, String xmlString) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xmlString));
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {

            if (eventType == XmlPullParser.START_TAG) {

                String name = parser.getName();
                if (name.equals(field)) {
                    return parser.getAttributeValue(null, attribute);
                }

            } else if (eventType == XmlPullParser.END_TAG) {
                //nothing to do
            }
            eventType = parser.next();

        }

        return "";
    }
}
