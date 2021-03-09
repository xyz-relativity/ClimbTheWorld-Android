package com.climbtheworld.app.map;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Xml;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.views.UploadPagerFragment;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;

import org.json.JSONException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import oauth.signpost.exception.OAuthException;
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

	private static final int REQUEST_TIMEOUT = 120;

	private final AppCompatActivity parent;
	private final OkHttpClient client;
	XmlPullParserFactory factory;

	public OsmManager(AppCompatActivity parent) throws OAuthException {
		this.parent = parent;

		Configs configs = Configs.instance(parent);

		OkHttpOAuthConsumer consumer = OAuthHelper.getInstance().getConsumer(Constants.DEFAULT_API);
		consumer.setTokenWithSecret(OAuthHelper.getToken(configs), OAuthHelper.getSecret(configs));

		client = new OkHttpClient.Builder().connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS).readTimeout(REQUEST_TIMEOUT,
				TimeUnit.SECONDS).addInterceptor(new SigningInterceptor(consumer)).build();

		try {
			factory = XmlPullParserFactory.newInstance();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		}
		factory.setNamespaceAware(true);
	}

	public void pushData(final List<Long> toChange, UploadPagerFragment callback) {
		Constants.WEB_EXECUTOR.execute(new Runnable() {
			public void run() {
				Map<Long, GeoNode> updates;

				try {
					Response response;

					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.updateLoadingStatus(R.string.osm_permission_check);
						}
					});

					if (!hasPermission(OSM_PERMISSIONS.allow_write_api)) {
						Toast.makeText(parent, parent.getString(R.string.osm_permission_failed_message),
								Toast.LENGTH_LONG).show();
						DialogBuilder.dismissLoadingDialogue();
						return;
					}

					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.updateLoadingStatus(R.string.osm_start_change_set);
						}
					});
					response = client.newCall(buildCreateChangeSetRequest()).execute();
					if (!response.isSuccessful()) {
						throw new IOException("OSM request to create change set failed: " + response.body().string());
					}

					long changeSetID = Long.parseLong(response.body().string());

					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.updateLoadingStatus(R.string.osm_pushing_data);
						}
					});
					updates = pushNodes(changeSetID, toChange);

					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.updateLoadingStatus(R.string.osm_commit_change_set);
						}
					});
					response = client.newCall(buildCloseChangeSetRequest(changeSetID)).execute();
					if (!response.isSuccessful()) {
						throw new IOException("OSM request to close change set failed: " + response.body().string());
					}

					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.updateLoadingStatus(R.string.success);
						}
					});

				} catch (final Exception e) {
					parent.runOnUiThread(new Runnable() {
						public void run() {
							DialogBuilder.showErrorDialog(parent, e.getMessage(), null);
						}
					});
					e.printStackTrace();
					updates = new HashMap<>();
				}

				parent.runOnUiThread(new Runnable() {
					public void run() {
						DialogBuilder.updateLoadingStatus(R.string.osm_updating_local_data);
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

				DialogBuilder.dismissLoadingDialogue();
				Globals.showNotifications(parent);

				parent.runOnUiThread(new Runnable() {
					public void run() {
						callback.pushTab();
					}
				});
			}
		});
	}

	private Request buildCreateChangeSetRequest() throws PackageManager.NameNotFoundException, IOException {
		PackageInfo pInfo = parent.getPackageManager().getPackageInfo(parent.getPackageName(), 0);
		String version = pInfo.versionName;

		RequestBody body = RequestBody.create(MediaType.parse("xml"),
				generateChangesetXml(parent.getString(R.string.app_name) + " " + version, parent.getString(R.string.app_name)));

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
				.url(Constants.DEFAULT_API + "/changeset/" + changeSetID)
				.get()
				.build();
	}

	private boolean hasPermission(OSM_PERMISSIONS osmPermission) throws XmlPullParserException, IOException {
		Response response = client.newCall(buildGetPermissionRequest()).execute();
		if (!response.isSuccessful()) {
			throw new IOException("OSM permission check failed: " + response.body().string());
		}
		return getValue("permission", "name", response.body().string()).equalsIgnoreCase(osmPermission.toString());
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
				generateCreateXml(changeSetID, node));

		Request request = new Request.Builder()
				.url(NODE_CREATE_URL)
				.put(body)
				.build();

		Response response = client.newCall(request).execute();
		if (!response.isSuccessful()) {
			throw new IOException("OSM request to create a new node failed: " + response.body().string());
		} else {
			node.osmID = Long.parseLong(response.body().string());
		}
	}

	private void updateNode(long changeSetID, GeoNode node) throws IOException, XmlPullParserException, JSONException {
		Response response = client.newCall(buildGetNodeRequest(node.osmID)).execute();

		if (!response.isSuccessful()) {
			throw new IOException("OSM request to fetch remote node failed: " + response.body().string());
		}

		RequestBody body = RequestBody.create(generateUpdateXml(changeSetID, getValue("node", "version", response.body().string()), node), MediaType.parse("xml"));

		Request request = new Request.Builder()
				.url(String.format(Locale.getDefault(), NODE_UPDATE_URL, changeSetID))
				.post(body)
				.build();

		response = client.newCall(request).execute();

		if (!response.isSuccessful()) {
			throw new IOException("OSM request to update the node failed: " + response.body().string());
		}
	}

	private void deleteNode(long changeSetID, GeoNode node)
			throws IOException, XmlPullParserException {
		Response response = client.newCall(buildGetNodeRequest(node.osmID)).execute();

		if (!response.isSuccessful()) {
			throw new IOException("OSM request to delete node failed: " + response.body().string());
		}

		RequestBody body = RequestBody.create(generateDeleteXml(changeSetID, getValue("node", "version", response.body().string()), node), MediaType.parse("xml"));

		Request request = new Request.Builder()
				.url(String.format(Locale.getDefault(), NODE_DELETE_URL, node.getID()))
				.delete(body)
				.build();

		client.newCall(request).execute();
	}

	private String getValue(String field, String attribute, String xmlString) throws XmlPullParserException, IOException {
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

	private String generateChangesetXml(String createdBy, String comments) throws IOException {
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		serializer.setOutput(writer);
		try {
			serializer.startDocument(null, null);
			serializer.startTag(null, "osm");
			serializer.startTag(null, "changeset");
			serializer.startTag(null, "tag");
			serializer.attribute(null, "k", "created_by");
			serializer.attribute(null, "v", createdBy);
			serializer.endTag(null, "tag");

			serializer.startTag(null, "tag");
			serializer.attribute(null, "k", "comment");
			serializer.attribute(null, "v", comments);
			serializer.endTag(null, "tag");

			serializer.endTag(null, "changeset");
			serializer.endTag(null, "osm");
			serializer.endDocument();

			return writer.toString();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private String generateCreateXml(long changeSetID, GeoNode node) throws IOException {
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		serializer.setOutput(writer);
		try {
			serializer.startDocument(null, null);
			serializer.startTag(null, "osm");
			serializer.startTag(null, "node");
			serializer.attribute(null, "changeset", String.valueOf(changeSetID));
			serializer.attribute(null, "lat", String.valueOf(node.decimalLatitude));
			serializer.attribute(null, "lon", String.valueOf(node.decimalLongitude));

			Map<String, Object> tags = node.getNodeTagsMap();

			for (String tagKey : tags.keySet()) {
				serializer.startTag(null, "tag");
				serializer.attribute(null, "k", tagKey);
				serializer.attribute(null, "v", String.valueOf(tags.get(tagKey)));
				serializer.endTag(null, "tag");
			}

			serializer.endTag(null, "node");
			serializer.endTag(null, "osm");
			serializer.endDocument();

			return writer.toString();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private String generateUpdateXml(long changeSetID, String version, GeoNode node) throws IOException {
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		serializer.setOutput(writer);
		try {
			serializer.startDocument(null, null);
			serializer.startTag(null, "osmChange");
			serializer.attribute(null, "version", "0.6");
			serializer.attribute(null, "generator", "acme osm editor");
			serializer.startTag(null, "modify");
			serializer.startTag(null, "node");
			serializer.attribute(null, "id", String.valueOf(node.getID()));
			serializer.attribute(null, "changeset", String.valueOf(changeSetID));
			serializer.attribute(null, "version", version);
			serializer.attribute(null, "lat", String.valueOf(node.decimalLatitude));
			serializer.attribute(null, "lon", String.valueOf(node.decimalLongitude));

			Map<String, Object> tags = node.getNodeTagsMap();

			for (String tagKey : tags.keySet()) {
				serializer.startTag(null, "tag");
				serializer.attribute(null, "k", tagKey);
				serializer.attribute(null, "v", String.valueOf(tags.get(tagKey)));
				serializer.endTag(null, "tag");
			}

			serializer.endTag(null, "node");
			serializer.endTag(null, "modify");
			serializer.endTag(null, "osmChange");
			serializer.endDocument();

			return writer.toString();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	private String generateDeleteXml(long changeSetID, String version, GeoNode node) throws IOException {
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		serializer.setOutput(writer);
		try {
			serializer.startDocument(null, null);
			serializer.startTag(null, "osm");
			serializer.startTag(null, "node");
			serializer.attribute(null, "id", String.valueOf(node.osmID));
			serializer.attribute(null, "changeset", String.valueOf(changeSetID));
			serializer.attribute(null, "version", version);
			serializer.attribute(null, "lat", String.valueOf(node.decimalLatitude));
			serializer.attribute(null, "lon", String.valueOf(node.decimalLongitude));

			serializer.endTag(null, "node");
			serializer.endTag(null, "osm");
			serializer.endDocument();

			return writer.toString();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

}
