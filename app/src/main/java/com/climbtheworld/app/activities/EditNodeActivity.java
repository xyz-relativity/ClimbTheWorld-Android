package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.editor.ArtificialTags;
import com.climbtheworld.app.map.editor.ContactTags;
import com.climbtheworld.app.map.editor.CragTags;
import com.climbtheworld.app.map.editor.GeneralTags;
import com.climbtheworld.app.map.editor.ITags;
import com.climbtheworld.app.map.editor.ITagsEvents;
import com.climbtheworld.app.map.editor.OtherTags;
import com.climbtheworld.app.map.editor.ParentMembership;
import com.climbtheworld.app.map.editor.RouteTags;
import com.climbtheworld.app.map.editor.SpinnerMarkerArrayAdapter;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.FormChanges;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import needle.UiRelatedTask;

public class EditNodeActivity extends AppCompatActivity implements IOrientationListener, ILocationListener, ITagsEvents {
	private GeoNode editNode;
	/** The node before the form changed it: as loaded, or as the advanced editor left it. */
	private JSONObject originalJson;
	/** What the form writes when nothing was changed in it; see FormChanges. */
	private JSONObject untouchedJson;
	private ParentMembership cragMembership;
	private GeoNode.NodeTypes[] nodeTypes;
	private MapLibreMapWidget mapWidget;
	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;
	private Spinner dropdownType;
	private ViewGroup containerTags;
	private GeneralTags genericTags;
	private ITags routeTags;

	private final Map<GeoNode.NodeTypes, List<ITags>> nodeTypesTags = new HashMap<>();
	private final List<ITags> allTagsHandlers = new ArrayList<>();

	private Intent intent;
	private long editNodeID;

	private final static int locationUpdate = 5000;
	private static final int REQUEST_ADVANCED_EDITOR = 0;
	private static final int REQUEST_CRAG_EDITOR = 1;
	public static final double MAP_EDIT_ZOOM_LEVEL = 18;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_edit_node);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});

		intent = getIntent();
		editNodeID = intent.getLongExtra("poiID", 0);

		doDatabaseWork(editNodeID);

		this.dropdownType = findViewById(R.id.spinnerNodeType);
		containerTags = findViewById(R.id.containerTags);

		mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer),
				savedInstanceState, MapLibreMapWidget.Profile.NODE_EDITOR, true, false);
		mapWidget.setMapAutoFollow(false);
		mapWidget.setOnMapClickListener(coordinate -> {
			if (editNode == null) {
				return;
			}
			editNode.updatePOILocation(
					coordinate.getLatitude(), coordinate.getLongitude(), editNode.elevationMeters);
			updateMapMarker();
			genericTags.updateLocation();
		});
		mapWidget.setOnTouchListener((view, motionEvent) -> {
			int action = motionEvent.getAction();
			ViewParent scrollParent = view.getParent();
			while (scrollParent != null && !(scrollParent instanceof ScrollView)) {
				scrollParent = scrollParent.getParent();
			}

			if (scrollParent != null) {
				switch (action) {
					case MotionEvent.ACTION_DOWN:
					scrollParent.requestDisallowInterceptTouchEvent(true);
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						scrollParent.requestDisallowInterceptTouchEvent(false);
						break;
				}
			}
			return false;
		});

		buildPopupMenu();

		//location
		deviceLocationManager = new DeviceLocationManager(this, locationUpdate);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
	}

	private void doDatabaseWork(final long poiId) {
		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<GeoNode>() {
					@Override
					protected GeoNode doWork() {
						if (poiId == 0) {
							OsmNode newNode = new OsmEditStore(EditNodeActivity.this).newNode(
									intent.getDoubleExtra("poiLat", Globals.virtualCamera.decimalLatitude),
									intent.getDoubleExtra("poiLon", Globals.virtualCamera.decimalLongitude));
							GeoNode tmpPoi = new GeoNode(newNode.jsonNodeInfo);
							tmpPoi.setClimbingType(GeoNode.NodeTypes.route);

							return tmpPoi;
						}

						OsmNode osmNode = AppDatabase.getInstance(EditNodeActivity.this).osmNodeDao()
								.find(OsmEntity.EntityOsmType.node, poiId);
						if (osmNode == null) {
							return null;
						}
						try {
							// A copy, so that leaving without saving changes nothing.
							GeoNode node = new GeoNode(new JSONObject(osmNode.jsonNodeInfo.toString()));
							node.countryIso = osmNode.countryIso;
							return node;
						} catch (JSONException e) {
							return null;
						}
					}

					@Override
					protected void thenDoUiRelatedWork(GeoNode result) {
						if (result == null) {
							DialogBuilder.toastOnMainThread(EditNodeActivity.this, getString(R.string.node_not_found));
							finish();
							return;
						}
						editNode = result;
						originalJson = copy(result.jsonNodeInfo);
						cragMembership = new ParentMembership(EditNodeActivity.this,
								findViewById(R.id.containerCrags), ParentMembership.Kind.CRAG,
								editNode.osmID, () -> editNode, () -> mapWidget.getVisibleBounds());
						cragMembership.setParentEditor(crag -> openCragEditor(crag));
						buildUi();
						updateMapMarker();
						if (editNodeID == 0) {
							dropdownType.performClick();
						}
					}
				});
	}

	private void buildPopupMenu() {
		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				//Creating the instance of PopupMenu
				PopupMenu popup = new PopupMenu(EditNodeActivity.this, view);
				popup.getMenuInflater().inflate(R.menu.edit_options, popup.getMenu());

				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						String urlFormat;
						Intent intent;

						int id = item.getItemId();
						if (id == R.id.advanceEditor) {
							JSONObject edited = resolveEdits();
							if (edited == null) {
								return true;
							}
							intent = new Intent(EditNodeActivity.this, EditNodeAdvancedActivity.class);
							intent.putExtra("nodeJson", edited.toString());
							startActivityForResult(intent, REQUEST_ADVANCED_EDITOR);
						} else if (id == R.id.openStreetMapEditor) {
							if (editNodeID > 0) {
								urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/edit?node=%d",
										editNode.getID());
							} else {
								urlFormat = String.format(Locale.getDefault(), "https://www.openstreetmap.org/edit#map=21/%f/%f",
										editNode.decimalLatitude, editNode.decimalLongitude);
							}

							intent = new Intent(Intent.ACTION_VIEW,
									Uri.parse(urlFormat));
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							EditNodeActivity.this.startActivity(intent);
							finish();
						} else if (id == R.id.vespucci) {
							MapBounds bbox = DataManager.computeBoundingBox(new Vector4d(editNode.decimalLatitude, editNode.decimalLongitude, editNode.elevationMeters, 0), 10);
							urlFormat = String.format(Locale.getDefault(), "josm:/load_and_zoom?left=%f&bottom=%f&right=%f&top=%f",
									bbox.getWest(), bbox.getSouth(), bbox.getEast(), bbox.getNorth());

							if (editNodeID > 0) {
								urlFormat = urlFormat + "&select=" + editNodeID;
							}

							try {
								intent = new Intent(Intent.ACTION_VIEW,
										Uri.parse(urlFormat));
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								EditNodeActivity.this.startActivity(intent);
								finish();
							} catch (ActivityNotFoundException e) {
								DialogBuilder.showErrorDialog(EditNodeActivity.this, getResources().getString(R.string.no_josm_app), null);
							}
						}
						return true;
					}
				});
				popup.show();
			}
		});
	}

	private void buildNodeFragments() {
		GeneralTags generalTags = new GeneralTags(editNode, this, containerTags);
		routeTags = new RouteTags(editNode, this, containerTags);
		ITags cragTags = new CragTags(editNode, this, containerTags);
		ITags artificialTags = new ArtificialTags(editNode, this, containerTags);
		ITags contactInfoTags = new ContactTags(editNode, this, containerTags);
		ITags otherTags = new OtherTags(editNode, this, containerTags);

		this.genericTags = generalTags;
		allTagsHandlers.add(generalTags);
		allTagsHandlers.add(routeTags);
		allTagsHandlers.add(cragTags);
		allTagsHandlers.add(artificialTags);
		allTagsHandlers.add(contactInfoTags);
		allTagsHandlers.add(otherTags);

		List<ITags> tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(routeTags);
		nodeTypesTags.put(GeoNode.NodeTypes.route, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(cragTags);
		nodeTypesTags.put(GeoNode.NodeTypes.crag, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(cragTags);
		nodeTypesTags.put(GeoNode.NodeTypes.area, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(artificialTags);
		tags.add(contactInfoTags);
		nodeTypesTags.put(GeoNode.NodeTypes.artificial, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(otherTags);
		nodeTypesTags.put(GeoNode.NodeTypes.unknown, tags);
	}

	private void buildUi() {
		mapWidget.centerOnLocation(new MapCoordinate(
				editNode.decimalLatitude, editNode.decimalLongitude, editNode.elevationMeters),
				MAP_EDIT_ZOOM_LEVEL);

		containerTags.removeAllViews();
		nodeTypesTags.clear();
		allTagsHandlers.clear();
		buildNodeFragments();

		nodeTypes = GeoNode.NodeTypes.nodeEditorValues(editNode.getNodeType());
		dropdownType.setOnItemSelectedListener(null);
		dropdownType.setAdapter(new SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nodeTypes, editNode));
		dropdownType.setSelection(Arrays.asList(nodeTypes).indexOf(editNode.getNodeType()));
		showSections();
		cragMembership.setCanAdd(editNode.getNodeType() == GeoNode.NodeTypes.route);

		// With the sections showing and before any change.
		GeoNode untouched = new GeoNode(copy(originalJson));
		synchronizeNode(untouched);
		untouchedJson = untouched.jsonNodeInfo;

		dropdownType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
				// The spinner also reports the selection it starts with.
				if (nodeTypes[pos] != editNode.getNodeType()) {
					switchNodeType(nodeTypes[pos]);
					updateMapMarker();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});
	}

	private void switchNodeType(GeoNode.NodeTypes type) {
		for (ITags tags : sectionsOf(editNode)) {
			tags.hideTags();
		}

		editNode.setClimbingType(type);

		showSections();
		cragMembership.setCanAdd(type == GeoNode.NodeTypes.route);
	}

	private void showSections() {
		for (ITags tags : sectionsOf(editNode)) {
			tags.showTags();
		}
		findViewById(R.id.textRouteTop).setVisibility(editNode.isRouteTop() ? View.VISIBLE : View.GONE);
	}

	/**
	 * The form sections of a node. A route's top only marks where the route ends, so the route's
	 * grade and details are left to its start.
	 */
	private List<ITags> sectionsOf(GeoNode node) {
		List<ITags> sections = nodeTypesTags.get(node.getNodeType());
		if (!node.isRouteTop()) {
			return sections;
		}
		List<ITags> topSections = new ArrayList<>(sections);
		topSections.remove(routeTags);
		return topSections;
	}

	/**
	 * Opens the crag, one the route is in or a new one, as part of this edit, with the route as the
	 * form has it; the crag comes back to be saved with the route.
	 */
	private void openCragEditor(OsmCollectionEntity crag) {
		JSONObject edited = resolveEdits();
		if (edited == null) {
			return;
		}
		Intent intent = new Intent(this, EditRelationActivity.class);
		intent.putExtra(EditRelationActivity.EXTRA_RELATION_JSON, crag.jsonNodeInfo.toString());
		intent.putExtra(EditRelationActivity.EXTRA_MEMBER_JSON, edited.toString());
		startActivityForResult(intent, REQUEST_CRAG_EDITOR);
	}

	@Override
	public void doRefresh() {
		updateMapMarker();
	}

	/** The node with the user's changes, or null when a field can't be read. */
	private JSONObject resolveEdits() {
		if (!synchronizeNode(editNode)) {
			return null;
		}
		return FormChanges.resolve(originalJson, untouchedJson, editNode.jsonNodeInfo);
	}

	private static JSONObject copy(JSONObject json) {
		try {
			return new JSONObject(json.toString());
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}

	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.ButtonCancel) {
			finish();
		} else if (editNode == null) {
			// Still loading.
			return;
		} else if (id == R.id.ButtonSave) {
			JSONObject edited = resolveEdits();
			if (edited == null) {
				return;
			}
			final OsmNode savedNode = new OsmNode(edited);
			final List<OsmCollectionEntity> crags = cragMembership.changedParents();
			Constants.DB_EXECUTOR
					.execute(new UiRelatedTask<Boolean>() {
						@Override
						protected Boolean doWork() {
							new OsmEditStore(EditNodeActivity.this)
									.save(Collections.singletonList(savedNode), crags);
							return true;
						}

						@Override
						protected void thenDoUiRelatedWork(Boolean result) {
							finish();
						}
					});
		} else if (id == R.id.ButtonDelete) {
			String message = getString(R.string.delete_confirmation_message);
			List<String> crags = cragMembership.storedParentNames();
			if (!crags.isEmpty()) {
				message += "\n\n" + getString(R.string.delete_also_from_crags, TextUtils.join(", ", crags));
			}
			new AlertDialog.Builder(this)
					.setTitle(getResources().getString(R.string.delete_confirmation, editNode.getName()))
					.setMessage(message)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int whichButton) {
							Constants.DB_EXECUTOR
									.execute(new UiRelatedTask<Boolean>() {
										@Override
										protected Boolean doWork() {
											return new OsmEditStore(EditNodeActivity.this)
													.deleteNode(editNode.osmID);
										}

										@Override
										protected void thenDoUiRelatedWork(Boolean deleted) {
											if (deleted) {
												finish();
											} else {
												DialogBuilder.showErrorDialog(EditNodeActivity.this,
														getString(R.string.node_part_of_way), null);
											}
										}
									});
						}
					})
					.setNegativeButton(android.R.string.no, null).show();
		}
	}

	public void updateMapMarker() {
		if (editNode != null) {
			mapWidget.setEditMarker(new DisplayableGeoNode(editNode, false));
		}
	}

	private boolean synchronizeNode(GeoNode node) {
		boolean success = true;
		List<ITags> activeTags = sectionsOf(node);
		for (ITags tag : allTagsHandlers) {
			if (!activeTags.contains(tag)) {
				tag.cancelNode(node);
			}
		}

		for (ITags tags : activeTags) {
			success &= tags.saveToNode(node);
		}
		updateMapMarker();
		return success;
	}

	@Override
	public void updateOrientation(OrientationManager.OrientationEvent event) {
		mapWidget.onOrientationChange(event.getAdjusted());
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

		mapWidget.onLocationChange(new MapCoordinate(pDecLatitude, pDecLongitude, pMetersAltitude));
	}

	@Override
	protected void onStart() {
		super.onStart();
		mapWidget.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);

		mapWidget.onResume();

		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		Globals.onPause(this);

		mapWidget.onPause();

		super.onPause();
	}

	@Override
	protected void onStop() {
		mapWidget.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapWidget.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		mapWidget.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		mapWidget.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}

	protected void onActivityResult(int requestCode, int resultCode,
	                                Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CRAG_EDITOR && resultCode == RESULT_OK) {
			try {
				cragMembership.parentEdited(new OsmCollectionEntity(new JSONObject(
						data.getStringExtra(EditRelationActivity.EXTRA_RELATION_JSON))));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return;
		}
		if (requestCode == REQUEST_ADVANCED_EDITOR && resultCode == RESULT_OK) {
			try {
				// The advanced editor's result is taken as it is, and the form starts from it.
				originalJson = new JSONObject(data.getStringExtra("nodeJson"));
			} catch (JSONException e) {
				e.printStackTrace();
				return;
			}
			editNode = new GeoNode(copy(originalJson));
			buildUi();
			updateMapMarker();
		}
	}
}
