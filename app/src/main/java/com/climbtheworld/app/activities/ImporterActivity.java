package com.climbtheworld.app.activities;

import android.content.DialogInterface;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.GeoNodeMapMarker;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogueUtils;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import needle.Needle;
import needle.UiRelatedTask;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImporterActivity extends AppCompatActivity {
	public static final int IMPORT_COUNTER = 5;
	private MapViewWidget mapWidget;
	private RadiusMarkerClusterer poiMarkersFolder;
	private Marker tapMarker;
	private ViewGroup newNodesView;
	private ScrollView newNodesScrollView;
	private final Map<Long, DisplayableGeoNode> nodesMap = new TreeMap<>();
	private final List<DisplayableGeoNode> addedNodes = new LinkedList<>();
	private View newNodesContainer;

	protected static class DownloadedData {
		public JSONObject theCrag;
		public List<JSONObject> theRoutes = new ArrayList<>();

		@Override
		public String toString() {
			StringBuilder response = new StringBuilder();
			try {
				response.append(theCrag.toString(2));

				for (JSONObject node : theRoutes) {
					response.append(node.toString(2));
				}

			} catch (JSONException e) {
				e.printStackTrace();
			}

			return response.toString();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_importer);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		newNodesView = findViewById(R.id.changesView);
		newNodesScrollView = findViewById(R.id.nodesScrollviewContainer);
		newNodesContainer = findViewById(R.id.nodesContainer);

		mapWidget = MapWidgetBuilder.getBuilder(this, false)
				.enableTapMarker()
				.enableAutoDownload()
				.setFilterMethod(MapViewWidget.FilterType.GHOSTS)
				.enableMinimap(6)
				.build();

		poiMarkersFolder = mapWidget.createClusterMarker();

		mapWidget.addCustomOverlay(poiMarkersFolder);

		mapWidget.addTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < UIConstants.ON_TAP_DELAY_MS)) {
					Point screenCoord = new Point();
					mapWidget.getOsmMap().getProjection().unrotateAndScalePoint((int) motionEvent.getX(), (int) motionEvent.getY(), screenCoord);
					GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels(screenCoord.x, screenCoord.y);
					tapMarker.setPosition(gp);
					mapWidget.invalidate(false);
				}
				return false;
			}
		});

		findViewById(R.id.plantButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				plantNode();
			}
		});

		findViewById(R.id.undoButton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				undoLastNode();
			}
		});
	}

	private void undoLastNode() {
		if (addedNodes.size() > 0) {
			int nodeIndex = (int) findLastNode();
			DisplayableGeoNode node = addedNodes.get(nodeIndex);
			node.setGhost(false);
			nodesMap.put(node.getGeoNode().osmID, node);
			mapWidget.getOsmMap().getController().setCenter(Globals.geoNodeToGeoPoint(node.getGeoNode()));
			tapMarker.setPosition(Globals.geoNodeToGeoPoint(node.getGeoNode()));
			addedNodes.remove(nodeIndex);

			poiMarkersFolder.getItems().remove(nodeIndex);
			poiMarkersFolder.invalidate();
			mapWidget.invalidate(false);

			addToUI();
		}
	}

	private long findLastNode() {
		int foundIndex = 0;
		long tmpId = 0;
		for (int i = 0; i < addedNodes.size(); ++i) {
			DisplayableGeoNode node = addedNodes.get(i);
			if (node.getGeoNode().osmID < tmpId) {
				tmpId = node.getGeoNode().osmID;
				foundIndex = i;
			}
		}
		return foundIndex;
	}

	private void plantNode() {
		if (newNodesView.getChildCount() > 0) {
			Long nodeId = Long.parseLong(((TextView) (newNodesView.getChildAt(newNodesView.getChildCount() - 1).findViewById(R.id.itemID))).getText().toString());
			DisplayableGeoNode node = nodesMap.get(nodeId);
			node.getGeoNode().decimalLatitude = tapMarker.getPosition().getLatitude();
			node.getGeoNode().decimalLongitude = tapMarker.getPosition().getLongitude();
			node.setGhost(true);
			addedNodes.add(node);

			GeoNodeMapMarker marker = new GeoNodeMapMarker(this, mapWidget.getOsmMap(), node);
			marker.setGhost(true);
			poiMarkersFolder.getItems().add(marker);
			poiMarkersFolder.invalidate();
			mapWidget.invalidate(false);

			nodesMap.remove(nodeId);
			newNodesView.removeView(newNodesView.getChildAt(newNodesView.getChildCount() - 1));
		}
		addToUI();
	}

	private void updateUI() {
		mapWidget.invalidateData();
		updateIconMarker();

		if (newNodesView.getChildCount() <= 0) {
			newNodesContainer.setVisibility(View.GONE);
		} else {
			newNodesContainer.setVisibility(View.VISIBLE);
		}

		((TextView) findViewById(R.id.nodesTextTotal)).setText("Total: " + (nodesMap.size() + addedNodes.size()));
		((TextView) findViewById(R.id.nodesTextPlaced)).setText("Placed: " + addedNodes.size());
		((TextView) findViewById(R.id.nodesTextLeft)).setText("Left: " + nodesMap.size());
	}

	private void updateIconMarker() {
		Drawable nodeIcon;
		if (newNodesView.getChildCount() == 0) {
			nodeIcon = getResources().getDrawable(R.drawable.ic_tap_marker);
			tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
		} else {
			Long nodeId = Long.parseLong(((TextView) (newNodesView.getChildAt(newNodesView.getChildCount() - 1).findViewById(R.id.itemID))).getText().toString());
			tapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
			nodeIcon = new PoiMarkerDrawable(this, null, nodesMap.get(nodeId), 0, 0).getDrawable();
		}
		tapMarker.setIcon(nodeIcon);
		tapMarker.setImage(nodeIcon);
	}

	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.buttonImport:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Enter 'The Crag' area ID");

				final LinearLayout group = new LinearLayout(this);
				group.setOrientation(LinearLayout.VERTICAL);

				TextView info = new TextView(this);
				info.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				info.setText("The id is part of an area URL. It is the number after the word 'area': https://www.thecrag.com/climbing/united-states/red-river-gorge/area/#######");
				info.setPadding(0, 0, 0, 20);
				group.addView(info);

				final EditText input = new EditText(this);
				input.setInputType(InputType.TYPE_CLASS_NUMBER);
				input.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				input.setHint("Area ID");
				input.requestFocus();
				group.addView(input);

				info = new TextView(this);
				info.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				info.setText("Grade system:");
				info.setPadding(0, 20, 0, 0);
				group.addView(info);

				final Spinner gradeSystemSpinner = new Spinner(this);
				gradeSystemSpinner.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
				gradeSystemSpinner.setOnItemSelectedListener(null);
				gradeSystemSpinner.setAdapter(new GradeSystem.GradeSystemArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GradeSystem.printableValues()));
				gradeSystemSpinner.setSelection(GradeSystem.fromString(Configs.instance(this).getString(Configs.ConfigKey.usedGradeSystem)).ordinal(), false);
				group.addView(gradeSystemSpinner);

				builder.setView(group);

				builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						downloadCrag(input.getText().toString(),
								GradeSystem.fromString(GradeSystem.printableValues()[gradeSystemSpinner.getSelectedItemPosition()].getMainKey()));
					}
				});
				builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});

				AlertDialog dialog = builder.create();
				dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

				dialog.show();
				break;

			case R.id.buttonSave:
				final GeoNode[] nodes = new GeoNode[addedNodes.size()];
				int i = 0;
				for (DisplayableGeoNode node : addedNodes) {
					nodes[i] = node.getGeoNode();
					i++;
				}

				Constants.DB_EXECUTOR
						.execute(new UiRelatedTask<Boolean>() {
							@Override
							protected Boolean doWork() {
								AppDatabase.getInstance(ImporterActivity.this).nodeDao().insertNodesWithReplace(nodes);
								return true;
							}

							@Override
							protected void thenDoUiRelatedWork(Boolean result) {
								Toast.makeText(ImporterActivity.this, addedNodes.size() + " routes saved to the device database.",
										Toast.LENGTH_LONG).show();

								addedNodes.clear();
								nodesMap.clear();
								updateUI();
							}
						});
				break;
		}
	}

	private void downloadCrag(final String areaID, final GradeSystem gradeSystem) {
		Constants.WEB_EXECUTOR.execute(new Runnable() {
			public void run() {
				try {
					OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30,
							TimeUnit.SECONDS).cookieJar(new CookieJar() {
						private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

						@Override
						public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
							cookieStore.put(url.topPrivateDomain(), cookies);
						}

						@Override
						public List<Cookie> loadForRequest(HttpUrl url) {
							List<Cookie> cookies = cookieStore.get(url.topPrivateDomain());
							return cookies != null ? cookies : new ArrayList<Cookie>();
						}
					}).build();

					//setup a web cookie
					Request request = new Request.Builder()
							.url("https://www.thecrag.com/")
							.get()
							.build();

					Response response = httpClient.newCall(request).execute();

					//setup a web cookie
					request = new Request.Builder()
							.url("https://www.thecrag.com/api/node/id/" + areaID)
							.get()
							.build();

					response = httpClient.newCall(request).execute();

					DownloadedData cragData = new DownloadedData();
					cragData.theCrag = new JSONObject(response.body().string());

					JSONArray routes = cragData.theCrag.getJSONObject("data").getJSONArray("childIDs");
					for (int i = 0; i < routes.length(); ++i) {
						request = new Request.Builder()
								.url("https://www.thecrag.com/api/node/id/" + routes.getString(i))
								.get()
								.build();

						response = httpClient.newCall(request).execute();
						cragData.theRoutes.add(new JSONObject(response.body().string()));
					}

					buildClimbingNodes(cragData, gradeSystem);
				} catch (IOException | JSONException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void buildClimbingNodes(ImporterActivity.DownloadedData cragData, GradeSystem gradeSystem) throws JSONException {
		nodesMap.clear();

		Long nodeID = AppDatabase.getInstance(this).getNewNodeID();

		JSONObject overpassJson = new JSONObject();
		overpassJson.put("elements", new JSONArray());

		JSONObject theCrag = overpassCrag(cragData.theCrag.getJSONObject("data"), nodeID);
		nodeID--;

		for (JSONObject node : cragData.theRoutes) {
			JSONObject obj = convertToOverpass(node.getJSONObject("data"), theCrag, nodeID, gradeSystem);
			if (obj != null) {
				overpassJson.getJSONArray("elements").put(obj);
				nodeID--;
			}
		}
		overpassJson.getJSONArray("elements").put(theCrag);

		DataManager.buildPOIsMapFromJsonString(overpassJson.toString(), nodesMap, "");

		addToUI();
	}

	private void addToUI() {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				newNodesView.removeAllViews();

				for (Long keyNode : nodesMap.keySet()) {
					final DisplayableGeoNode node = nodesMap.get(keyNode);
					node.getGeoNode().localUpdateState = ClimbingTags.TO_UPDATE_STATE;
					Drawable nodeIcon = new PoiMarkerDrawable(ImporterActivity.this, null, node, 0, 0);

					final View newViewElement = ListViewItemBuilder.getPaddedBuilder(ImporterActivity.this)
							.setTitle(node.getGeoNode().getName())
							.setDescription(DialogueUtils.buildDescription(ImporterActivity.this, node.getGeoNode()))
							.setIcon(nodeIcon)
							.build();

					((TextView) newViewElement.findViewById(R.id.itemID)).setText(String.valueOf(node.getGeoNode().osmID));
					newViewElement.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							NodeDialogBuilder.showNodeInfoDialog(ImporterActivity.this, node.getGeoNode());
						}
					});
					newNodesView.addView(newViewElement);
				}

				newNodesScrollView.postDelayed(new Runnable() {
					@Override
					public void run() {
						newNodesScrollView.fullScroll(View.FOCUS_DOWN);
					}
				}, 500);
				updateUI();
			}
		});
	}

	private JSONObject overpassCrag(JSONObject node, long nodeID) throws JSONException {
		JSONObject result = new JSONObject();
		result.put("type", "node");
		result.put("id", nodeID);
		JSONObject tags = new JSONObject();
		result.put(ClimbingTags.KEY_TAGS, tags);

		tags.put(ClimbingTags.KEY_CLIMBING, "crag");
		tags.put(ClimbingTags.KEY_SPORT, "climbing");
		tags.put(ClimbingTags.KEY_NAME, node.opt("name"));
		tags.put(ClimbingTags.KEY_ROUTES, "0");

		return result;
	}

	private JSONObject convertToOverpass(final JSONObject node, JSONObject crag, long nodeID, GradeSystem gradeSystem) throws JSONException {
		if (!node.getString("type").equalsIgnoreCase("route")) {
			return null;
		}
		JSONObject result = new JSONObject();
		result.put("type", "node");
		result.put("id", nodeID);

		JSONObject tags = new JSONObject();
		result.put(ClimbingTags.KEY_TAGS, tags);

		tags.put(ClimbingTags.KEY_CLIMBING, "route_bottom");
		tags.put(ClimbingTags.KEY_SPORT, "climbing");
		tags.put(ClimbingTags.KEY_NAME, node.opt("name"));

		String length = null;
		if (node.has("height")) {
			length = (String) node.getJSONArray("height").get(0);
			tags.put(ClimbingTags.KEY_LENGTH, String.valueOf(Math.ceil(Float.parseFloat(length))));
		}

		tags.put(ClimbingTags.KEY_BOLTS, node.opt("bolts"));

		if (node.has("pitches") && Integer.parseInt((String) node.get("pitches")) != 1) {
			tags.put(ClimbingTags.KEY_BOLTS, node.opt("pitches"));
		}

		GeoNode.ClimbingStyle style = null;
		if (node.has("style")) {
			try {
				style = GeoNode.ClimbingStyle.valueOf(node.getString("style").toLowerCase());
				tags.put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name(), "yes");
			} catch (IllegalArgumentException ex) {
				//empty
			}
		}

		Integer gradeIndex = null;
		if (node.getJSONObject("gradeAtom").has("grade")) {
			if (node.getJSONObject("gradeAtom").optString("gradeStyle").equalsIgnoreCase("Boulder")) {
				gradeIndex = GradeSystem.vGrade.indexOf(node.getJSONObject("gradeAtom").getString("grade"));
			} else if (node.getJSONObject("gradeAtom").optString("gradeStyle").equalsIgnoreCase("Free")) {
				gradeIndex = gradeSystem.indexOf(node.getJSONObject("gradeAtom").getString("grade"));
			} else {
				gradeIndex = -1;
				DialogBuilder.toastOnMainThread(this, "Did not understand grade system of type: " + node.getJSONObject("gradeAtom").optString("gradeStyle"));
			}
			tags.put(String.format(ClimbingTags.KEY_GRADE_TAG, "uiaa"), GradeSystem.uiaa.getGrade(gradeIndex));
		}

		upgradeCrag(gradeIndex, length, style, crag);

		return result;
	}

	private void upgradeCrag(Integer gradeIndex, String length, GeoNode.ClimbingStyle style, JSONObject crag) throws JSONException {
		JSONObject tags = crag.getJSONObject(ClimbingTags.KEY_TAGS);
		tags.put(ClimbingTags.KEY_ROUTES, String.valueOf(Integer.parseInt(tags.getString(ClimbingTags.KEY_ROUTES)) + 1));

		if (style != null) {
			tags.put(ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name(), "yes");
		}

		if (length != null) {
			if (!tags.has(ClimbingTags.KEY_MAX_LENGTH)) {
				tags.put(ClimbingTags.KEY_MAX_LENGTH, length);
			}

			if (!tags.has(ClimbingTags.KEY_MIN_LENGTH)) {
				tags.put(ClimbingTags.KEY_MIN_LENGTH, length);
			}

			Double tmpLength = tags.getDouble(ClimbingTags.KEY_MAX_LENGTH);
			if (Double.parseDouble(length) > tmpLength) {
				tags.put(ClimbingTags.KEY_MAX_LENGTH, length);
			}

			tmpLength = tags.getDouble(ClimbingTags.KEY_MIN_LENGTH);
			if (Double.parseDouble(length) < tmpLength) {
				tags.put(ClimbingTags.KEY_MIN_LENGTH, length);
			}
		}

		if (gradeIndex != null && gradeIndex != -1) {
			String maxGradeKey = String.format(ClimbingTags.KEY_GRADE_TAG_MAX, "uiaa");
			String minGradeKey = String.format(ClimbingTags.KEY_GRADE_TAG_MIN, "uiaa");
			if (!tags.has(maxGradeKey)) {
				tags.put(maxGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
			}

			if (!tags.has(minGradeKey)) {
				tags.put(minGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
			}

			Integer tmpIndex = GradeSystem.uiaa.indexOf(tags.getString(maxGradeKey));
			if (gradeIndex > tmpIndex) {
				tags.put(maxGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
			}

			tmpIndex = GradeSystem.uiaa.indexOf(tags.getString(minGradeKey));
			if (gradeIndex < tmpIndex) {
				tags.put(minGradeKey, GradeSystem.uiaa.getGrade(gradeIndex));
			}
		}
	}
}
