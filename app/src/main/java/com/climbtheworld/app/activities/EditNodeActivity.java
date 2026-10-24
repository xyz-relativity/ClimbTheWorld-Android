package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ArCoreFeatures;
import com.climbtheworld.app.augmentedreality.placement.ArPlacement;
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
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.sensors.pressure.Barometer;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.FormChanges;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.storage.upload.OsmElements;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.EdgeToEdge;
import com.climbtheworld.app.utils.views.EditorMapSizer;
import com.climbtheworld.app.utils.views.SpinnerUtils;
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

public class EditNodeActivity extends MapWidgetActivity implements ITagsEvents {
	private GeoNode editNode;
	/** The node before the form changed it: as loaded, or as the advanced editor left it. */
	private JSONObject originalJson;
	/** What the form writes when nothing was changed in it; see FormChanges. */
	private JSONObject untouchedJson;
	private ParentMembership cragMembership;
	/**
	 * Whether the node can't be saved without a name: a new one, or one that had a name. Nodes
	 * mapped without one, such as most route tops, still save as they are.
	 */
	private boolean nameRequired;
	private OsmEntity.EntityClimbingType[] nodeTypes;
	private ArPlacement arPlacement;
	private DeviceLocationManager deviceLocationManager;
	// Steadies the GPS altitude the A.R. placement goes by, as in the A.R. view.
	private Barometer barometer;
	private OrientationManager orientationManager;
	private Spinner dropdownType;
	private ViewGroup containerTags;
	private GeneralTags genericTags;
	private ITags routeTags;

	private final Map<OsmEntity.EntityClimbingType, List<ITags>> nodeTypesTags = new HashMap<>();
	private final List<ITags> allTagsHandlers = new ArrayList<>();

	private Intent intent;
	private long editNodeID;

	/**
	 * A boolean extra: whether to open on the A.R. placement rather than the map, as from the A.R.
	 * view, which hands ARCore's world over for it; see ArPlacement.show.
	 */
	public static final String EXTRA_AR_PLACEMENT = "arPlacement";
	private final static int locationUpdate = 5000;
	private static final int REQUEST_ADVANCED_EDITOR = 0;
	private static final int REQUEST_CRAG_EDITOR = 1;
	public static final double MAP_EDIT_ZOOM_LEVEL = 18;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_edit_node);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		intent = getIntent();
		editNodeID = intent.getLongExtra("poiID", 0);

		doDatabaseWork(editNodeID);

		this.dropdownType = findViewById(R.id.spinnerNodeType);
		containerTags = findViewById(R.id.containerTags);

		EditorMapSizer.sizeOnFirstLayout(findViewById(R.id.geoNodeEditView), findViewById(R.id.mapViewContainer));
		mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer),
				savedInstanceState, MapLibreMapWidget.Profile.NODE_EDITOR, true, false);
		mapWidget.setMapAutoFollow(false);
		mapWidget.setOnMapClickListener(coordinate -> {
			moveNode(coordinate.getLatitude(), coordinate.getLongitude());
			// Where the node now is in the A.R. is not known.
			arPlacement.clearPin();
		});
		arPlacement = ArCoreFeatures.placement(this, findViewById(R.id.mapViewContainer),
				(latitude, longitude) -> {
					if (editNode == null) {
						return;
					}
					moveNode(latitude, longitude);
					// To find the node there when going back to the map.
					mapWidget.centerOnLocation(new MapCoordinate(latitude, longitude,
							editNode.elevationMeters), MAP_EDIT_ZOOM_LEVEL);
				});
		arPlacement.restore(savedInstanceState);
		// Opened from the A.R. view; made again, it goes by what it showed.
		if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_AR_PLACEMENT, false)) {
			arPlacement.show();
		}
		mapWidget.setOnTouchListener(EditorMapSizer.holdScrollWhileTouched());

		buildPopupMenu();

		//location
		deviceLocationManager = new DeviceLocationManager(this, locationUpdate);
		barometer = new Barometer(this);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
		mapWidget.setOnFollowCompassChangeListener(orientationManager::setFastUpdates);
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
							tmpPoi.setClimbingType(OsmEntity.EntityClimbingType.route);

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
						startEditing(result, Collections.emptyList());
						centerOnNode();
						if (editNodeID == 0) {
							dropdownType.performClick();
						}
					}
				});
	}

	/**
	 * Fills the form with the node.
	 *
	 * @param joinedCrags crags the node is in that are not stored so yet, see ParentMembership
	 */
	private void startEditing(GeoNode node, List<OsmCollectionEntity> joinedCrags) {
		editNode = node;
		originalJson = OsmElements.copy(node.jsonNodeInfo);
		nameRequired = editNodeID == 0 || !node.getName().trim().isEmpty();
		ViewGroup containerCrags = findViewById(R.id.containerCrags);
		containerCrags.removeAllViews();
		cragMembership = new ParentMembership(this, containerCrags, ParentMembership.Kind.CRAG,
				editNode.osmID, () -> editNode, () -> mapWidget.getVisibleBounds(), joinedCrags);
		cragMembership.setParentEditor(crag -> openCragEditor(crag));
		buildUi();
		updateMapMarker();
	}

	private void centerOnNode() {
		mapWidget.centerOnLocation(new MapCoordinate(
				editNode.decimalLatitude, editNode.decimalLongitude, editNode.elevationMeters),
				MAP_EDIT_ZOOM_LEVEL);
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
		nodeTypesTags.put(OsmEntity.EntityClimbingType.route, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(cragTags);
		nodeTypesTags.put(OsmEntity.EntityClimbingType.crag, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(cragTags);
		nodeTypesTags.put(OsmEntity.EntityClimbingType.area, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(artificialTags);
		tags.add(contactInfoTags);
		nodeTypesTags.put(OsmEntity.EntityClimbingType.artificial, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(otherTags);
		nodeTypesTags.put(OsmEntity.EntityClimbingType.others, tags);
	}

	private void buildUi() {
		containerTags.removeAllViews();
		nodeTypesTags.clear();
		allTagsHandlers.clear();
		buildNodeFragments();

		nodeTypes = OsmEntity.EntityClimbingType.nodeEditorValues(editNode.getNodeType());
		dropdownType.setOnItemSelectedListener(null);
		dropdownType.setAdapter(new SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nodeTypes, editNode));
		dropdownType.setSelection(Arrays.asList(nodeTypes).indexOf(editNode.getNodeType()));
		showSections();
		cragMembership.setCanAdd(editNode.getNodeType() == OsmEntity.EntityClimbingType.route);

		// With the sections showing and before any change.
		GeoNode untouched = new GeoNode(OsmElements.copy(originalJson));
		synchronizeNode(untouched);
		untouchedJson = untouched.jsonNodeInfo;

		dropdownType.setOnItemSelectedListener(SpinnerUtils.onItemSelected(pos -> {
			// The spinner also reports the selection it starts with.
			if (nodeTypes[pos] != editNode.getNodeType()) {
				switchNodeType(nodeTypes[pos]);
				updateMapMarker();
			}
		}));
	}

	private void switchNodeType(OsmEntity.EntityClimbingType type) {
		for (ITags tags : sectionsOf(editNode)) {
			tags.hideTags();
		}

		editNode.setClimbingType(type);

		showSections();
		cragMembership.setCanAdd(type == OsmEntity.EntityClimbingType.route);
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
	 * form has it and the areas changed so far; the crag comes back with the areas it joined or
	 * left, to be saved with the route.
	 */
	private void openCragEditor(OsmCollectionEntity crag) {
		JSONObject edited = resolveEdits();
		if (edited == null) {
			return;
		}
		Intent intent = new Intent(this, EditRelationActivity.class);
		intent.putExtra(EditRelationActivity.EXTRA_RELATION_JSON, crag.jsonNodeInfo.toString());
		intent.putExtra(EditRelationActivity.EXTRA_MEMBER_JSON, edited.toString());
		EditRelationActivity.putParents(intent, cragMembership.changedGrandparents());
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

	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.ButtonCancel) {
			finish();
		} else if (editNode == null) {
			// Still loading.
			return;
		} else if (id == R.id.ButtonSave) {
			save(this::finish);
		} else if (id == R.id.ButtonSaveAndAdd) {
			save(this::addAnother);
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

	/** Saves the node with its crags, then runs then on the UI thread. */
	private void save(Runnable then) {
		JSONObject edited = resolveEdits();
		if (edited == null) {
			return;
		}
		if (nameRequired && !genericTags.checkNameGiven()) {
			return;
		}
		final OsmNode savedNode = new OsmNode(edited);
		// The crags, then their areas.
		final List<OsmCollectionEntity> relations = cragMembership.changedParents();
		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<Boolean>() {
					@Override
					protected Boolean doWork() {
						new OsmEditStore(EditNodeActivity.this)
								.save(Collections.singletonList(savedNode), relations);
						return true;
					}

					@Override
					protected void thenDoUiRelatedWork(Boolean result) {
						then.run();
					}
				});
	}

	/**
	 * Starts the form over on a new node where the saved one is, of its type and at the end of its
	 * crags, for the next route of the crag. The map and the A.R. stay as they are, the A.R. still
	 * tied to the map, so that it need not be aligned again.
	 */
	private void addAnother() {
		final double latitude = editNode.decimalLatitude;
		final double longitude = editNode.decimalLongitude;
		final OsmEntity.EntityClimbingType type = editNode.getNodeType();
		final long[] cragIds = cragMembership.parentIds();
		// Loading again until the new node is in.
		editNode = null;
		editNodeID = 0;
		hideKeyboard();
		// The saved node now shows among the others.
		mapWidget.invalidateData();
		arPlacement.nextNode();
		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<GeoNode>() {
					/** The stored crags, listing the new node. */
					private final List<OsmCollectionEntity> crags = new ArrayList<>();

					@Override
					protected GeoNode doWork() {
						OsmEditStore store = new OsmEditStore(EditNodeActivity.this);
						GeoNode node = new GeoNode(store.newNode(latitude, longitude).jsonNodeInfo);
						node.setClimbingType(type);
						for (long cragId : cragIds) {
							OsmEntity crag = store.load(OsmEntity.EntityOsmType.relation, cragId);
							if (crag instanceof OsmCollectionEntity) {
								RelationMembers.append(((OsmCollectionEntity) crag).getMembers(),
										OsmEntity.EntityOsmType.node.name(), node.osmID, "");
								crags.add((OsmCollectionEntity) crag);
							}
						}
						return node;
					}

					@Override
					protected void thenDoUiRelatedWork(GeoNode node) {
						startEditing(node, crags);
						// Back to the map, to place it.
						((ScrollView) findViewById(R.id.geoNodeEditView)).smoothScrollTo(0, 0);
					}
				});
	}

	private void hideKeyboard() {
		View focused = getCurrentFocus();
		if (focused == null) {
			return;
		}
		focused.clearFocus();
		((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
				.hideSoftInputFromWindow(focused.getWindowToken(), 0);
	}

	/**
	 * Moves the node, keeping its elevation.
	 */
	private void moveNode(double latitude, double longitude) {
		if (editNode == null) {
			return;
		}
		editNode.updatePOILocation(latitude, longitude, editNode.elevationMeters);
		updateMapMarker();
		genericTags.updateLocation();
	}

	public void updateMapMarker() {
		if (editNode != null) {
			DisplayableGeoNode marker = new DisplayableGeoNode(editNode, false);
			mapWidget.setEditMarker(marker);
			arPlacement.setNode(marker);
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
		super.updateOrientation(event);
		arPlacement.onOrientation(event);
	}

	@Override
	public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
		super.updatePosition(pDecLatitude, pDecLongitude, pMetersAltitude, accuracy);
		arPlacement.onLocation(pDecLatitude, pDecLongitude, barometer.anchor(pMetersAltitude),
				accuracy);
	}

	@Override
	protected void onStart() {
		super.onStart();
		arPlacement.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		arPlacement.onResume();

		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
		barometer.start();
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		barometer.stop();
		arPlacement.onPause();
		super.onPause();
	}

	@Override
	protected void onStop() {
		arPlacement.onStop();
		super.onStop();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		arPlacement.onLowMemory();
	}

	@Override
	protected void onDestroy() {
		arPlacement.onDestroy();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		arPlacement.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}

	protected void onActivityResult(int requestCode, int resultCode,
	                                Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CRAG_EDITOR && resultCode == RESULT_OK) {
			try {
				cragMembership.parentEdited(new OsmCollectionEntity(new JSONObject(
								data.getStringExtra(EditRelationActivity.EXTRA_RELATION_JSON))),
						EditRelationActivity.parents(data));
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
			editNode = new GeoNode(OsmElements.copy(originalJson));
			centerOnNode();
			buildUi();
			updateMapMarker();
		}
	}
}
