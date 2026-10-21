package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.editor.GeneralTags;
import com.climbtheworld.app.map.editor.ITags;
import com.climbtheworld.app.map.editor.OtherTags;
import com.climbtheworld.app.map.editor.ParentMembership;
import com.climbtheworld.app.map.editor.RelationMemberList;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmEntity.EntityClimbingType;
import com.climbtheworld.app.storage.database.OsmEntity.EntityOsmType;
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
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import needle.UiRelatedTask;

/**
 * Edits a crag or an area relation: its tags, its members in order, and for a crag the areas it
 * is in. Members are added by tapping them on the map. Opened from the relation's info dialog it
 * saves by itself; opened from the editor of one of its members it is part of that edit, and
 * hands the relation back, a crag with the areas it joined or left, for that editor to save with
 * the member.
 */
public class EditRelationActivity extends MapWidgetActivity {
	/** The stored relation to edit. */
	public static final String EXTRA_RELATION_ID = "relationID";
	/** The relation to edit as part of a member's edit; it comes back under the same key. */
	public static final String EXTRA_RELATION_JSON = "relationJson";
	/** With EXTRA_RELATION_JSON: the member whose editor opened this one, as that editor has it. */
	public static final String EXTRA_MEMBER_JSON = "memberJson";
	/**
	 * With EXTRA_RELATION_JSON, for a crag: the areas changed earlier in the member's edit, as a
	 * JSON array; the areas changed here come back under the same key. See putParents.
	 */
	public static final String EXTRA_PARENTS_JSON = "parentsJson";

	private static final int LOCATION_UPDATE = 5000;
	/** Where a collection is shown, read by DataManager.toDisplayableNode. */
	private static final String KEY_CENTER = "center";
	private static final double MAP_ZOOM_LEVEL = 17;
	private static final int REQUEST_ADVANCED_EDITOR = 0;
	private static final int REQUEST_AREA_EDITOR = 1;

	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;
	private ViewGroup containerTags;
	private OsmEditStore store;
	private boolean partOfMemberEdit;

	/** The relation being edited; the member list changes its members in place. */
	private OsmCollectionEntity relation;
	/** The relation before the form changed its tags; see FormChanges. */
	private JSONObject originalJson;
	private JSONObject untouchedJson;
	private GeoNode editNode;
	private final List<ITags> tagSections = new ArrayList<>();
	private RelationMemberList memberList;
	private ParentMembership areaMembership;

	private static final class Loaded {
		final OsmCollectionEntity relation;
		final Map<String, OsmEntity> members;
		final Map<String, Integer> routeCounts;
		final String highlightedKey;
		final List<OsmCollectionEntity> editedParents;

		Loaded(OsmCollectionEntity relation, Map<String, OsmEntity> members,
		       Map<String, Integer> routeCounts, String highlightedKey,
		       List<OsmCollectionEntity> editedParents) {
			this.relation = relation;
			this.members = members;
			this.routeCounts = routeCounts;
			this.highlightedKey = highlightedKey;
			this.editedParents = editedParents;
		}
	}

	/** Puts the relations into the intent under EXTRA_PARENTS_JSON. */
	public static void putParents(Intent intent, List<OsmCollectionEntity> parents) {
		JSONArray array = new JSONArray();
		for (OsmCollectionEntity parent : parents) {
			array.put(parent.jsonNodeInfo);
		}
		intent.putExtra(EXTRA_PARENTS_JSON, array.toString());
	}

	/** The relations under EXTRA_PARENTS_JSON, none without it. */
	public static List<OsmCollectionEntity> parents(Intent intent) throws JSONException {
		List<OsmCollectionEntity> result = new ArrayList<>();
		String json = intent.getStringExtra(EXTRA_PARENTS_JSON);
		if (json == null) {
			return result;
		}
		JSONArray array = new JSONArray(json);
		for (int index = 0; index < array.length(); index++) {
			result.add(new OsmCollectionEntity(array.getJSONObject(index)));
		}
		return result;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MapLibre.getInstance(this);
		setContentView(R.layout.activity_edit_relation);

		EdgeToEdge.padForSystemBars(findViewById(R.id.main));

		store = new OsmEditStore(this);
		containerTags = findViewById(R.id.containerTags);
		partOfMemberEdit = getIntent().hasExtra(EXTRA_RELATION_JSON);
		if (partOfMemberEdit) {
			// Deleting is for the relation's own edit, not a member's.
			findViewById(R.id.ButtonDelete).setVisibility(View.GONE);
		}

		EditorMapSizer.sizeOnFirstLayout(findViewById(R.id.geoNodeEditView), findViewById(R.id.mapViewContainer));
		mapWidget = new MapLibreMapWidget(this, findViewById(R.id.mapViewContainer),
				savedInstanceState, MapLibreMapWidget.Profile.NODE_EDITOR, true, false);
		mapWidget.setMapAutoFollow(false);
		mapWidget.setOnMapClickListener(coordinate -> {
			// Only the pins and labels on the map do something here.
		});
		mapWidget.setOnPoiClickListener(this::onPoiTapped);
		mapWidget.setOnCollectionClickListener(this::onCollectionTapped);
		mapWidget.setOnTouchListener(EditorMapSizer.holdScrollWhileTouched());

		buildPopupMenu();

		deviceLocationManager = new DeviceLocationManager(this, LOCATION_UPDATE);
		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
		mapWidget.setOnFollowCompassChangeListener(orientationManager::setFastUpdates);

		load();
	}

	private void load() {
		Intent intent = getIntent();
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Loaded>() {
			@Override
			protected Loaded doWork() {
				try {
					if (partOfMemberEdit) {
						OsmCollectionEntity loaded = new OsmCollectionEntity(
								new JSONObject(intent.getStringExtra(EXTRA_RELATION_JSON)));
						Map<String, OsmEntity> members = store.loadMembers(loaded.getMembers());
						// As stored, before the member whose editor opened this one stands in.
						Map<String, Integer> routeCounts = countRoutes(members);
						List<OsmCollectionEntity> editedParents = parents(intent);
						String memberJson = intent.getStringExtra(EXTRA_MEMBER_JSON);
						if (memberJson == null) {
							return new Loaded(loaded, members, routeCounts, null, editedParents);
						}
						// The member as its editor has it, which may not be stored yet.
						OsmEntity member = fromJson(new JSONObject(memberJson));
						String key = RelationMembers.key(member.osmType.name(), member.osmID);
						members.put(key, member);
						return new Loaded(loaded, members, routeCounts, key, editedParents);
					}
					OsmEntity stored = store.load(EntityOsmType.relation,
							intent.getLongExtra(EXTRA_RELATION_ID, 0));
					if (!(stored instanceof OsmCollectionEntity)) {
						return null;
					}
					OsmCollectionEntity loaded = (OsmCollectionEntity) stored;
					Map<String, OsmEntity> members = store.loadMembers(loaded.getMembers());
					return new Loaded(loaded, members, countRoutes(members), null,
							Collections.emptyList());
				} catch (JSONException e) {
					return null;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(Loaded loaded) {
				if (loaded == null) {
					DialogBuilder.toastOnMainThread(EditRelationActivity.this,
							getString(R.string.node_not_found));
					finish();
					return;
				}
				buildUi(loaded);
			}
		});
	}

	private void buildUi(Loaded loaded) {
		relation = loaded.relation;
		originalJson = OsmElements.copy(relation.jsonNodeInfo);
		boolean area = isArea();
		((TextView) findViewById(R.id.textRelationType)).setText(area ? R.string.area : R.string.crag);

		memberList = new RelationMemberList(this, findViewById(R.id.containerMembers), area,
				relation.getMembers(), loaded.members, loaded.routeCounts, loaded.highlightedKey,
				this::updateMapMarkers);
		if (!area) {
			areaMembership = new ParentMembership(this, findViewById(R.id.containerParents),
					ParentMembership.Kind.AREA, relation.osmID, this::locatedRelation,
					mapWidget::getVisibleBounds, loaded.editedParents);
			areaMembership.setParentEditor(this::openAreaEditor);
		}
		buildTagSections();

		GeoNode located = locatedRelation();
		mapWidget.centerOnLocation(new MapCoordinate(
				located.decimalLatitude, located.decimalLongitude, 0), MAP_ZOOM_LEVEL);
		updateMapMarkers();
	}

	private void buildTagSections() {
		containerTags.removeAllViews();
		tagSections.clear();
		editNode = new GeoNode(OsmElements.copy(originalJson));

		GeneralTags generalTags = new GeneralTags(editNode, this, containerTags);
		generalTags.hideLocation();
		tagSections.add(generalTags);
		// The crag tags (grades, routes, lengths, styles) are for crag and area nodes, not relations.
		if (editNode.getNodeType() != OsmEntity.EntityClimbingType.crag
				&& editNode.getNodeType() != OsmEntity.EntityClimbingType.area) {
			tagSections.add(new OtherTags(editNode, this, containerTags));
		}
		for (ITags section : tagSections) {
			section.showTags();
		}

		// With the sections showing and before any change.
		GeoNode untouched = new GeoNode(OsmElements.copy(originalJson));
		saveSections(untouched);
		untouchedJson = untouched.jsonNodeInfo;
	}

	private boolean saveSections(GeoNode node) {
		boolean success = true;
		for (ITags section : tagSections) {
			success &= section.saveToNode(node);
		}
		return success;
	}

	/** The relation with the user's changes and its members in their new order, or null. */
	private JSONObject resolveEdits() {
		if (!saveSections(editNode)) {
			return null;
		}
		JSONObject result = FormChanges.resolve(originalJson, untouchedJson, editNode.jsonNodeInfo);
		try {
			result.put(ClimbingTags.KEY_MEMBERS, relation.getMembers());
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		return result;
	}

	/**
	 * Opens the area, one the crag is in or a new one, as part of this edit, with the crag as the
	 * form has it; the area comes back to be saved with the crag.
	 */
	private void openAreaEditor(OsmCollectionEntity area) {
		JSONObject edited = resolveEdits();
		if (edited == null) {
			return;
		}
		GeoNode located = locatedRelation();
		try {
			// The area's editor places the crag there, as it may have unsaved members.
			edited.put(KEY_CENTER, new JSONObject()
					.put(ClimbingTags.KEY_LAT, located.decimalLatitude)
					.put(ClimbingTags.KEY_LON, located.decimalLongitude));
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
		Intent intent = new Intent(this, EditRelationActivity.class);
		intent.putExtra(EXTRA_RELATION_JSON, area.jsonNodeInfo.toString());
		intent.putExtra(EXTRA_MEMBER_JSON, edited.toString());
		startActivityForResult(intent, REQUEST_AREA_EDITOR);
	}

	private boolean isArea() {
		return relation.entityClimbingType == EntityClimbingType.area;
	}

	/** The relation placed in the middle of its members, for the map and for nearby areas. */
	private GeoNode locatedRelation() {
		double north = -90;
		double south = 90;
		double east = -180;
		double west = 180;
		boolean located = false;
		for (int index = 0; index < memberList.size(); index++) {
			DisplayableGeoNode pin = memberList.pinAt(index);
			if (pin == null) {
				continue;
			}
			GeoNode place = pin.getGeoNode();
			north = Math.max(north, place.decimalLatitude);
			south = Math.min(south, place.decimalLatitude);
			east = Math.max(east, place.decimalLongitude);
			west = Math.min(west, place.decimalLongitude);
			located = true;
		}
		GeoNode result = new GeoNode(OsmElements.copy(relation.jsonNodeInfo));
		if (located) {
			result.updatePOILocation((north + south) / 2, (east + west) / 2, 0);
		} else {
			result.updatePOILocation(Globals.virtualCamera.decimalLatitude,
					Globals.virtualCamera.decimalLongitude, 0);
		}
		return result;
	}

	/** Marks the members on the map, with the route numbers of their new order. */
	private void updateMapMarkers() {
		List<DisplayableGeoNode> markers = new ArrayList<>();
		int[] routeNumbers = memberList.routeNumbers();
		for (int index = 0; index < memberList.size(); index++) {
			DisplayableGeoNode marker = memberList.pinAt(index);
			if (marker == null) {
				continue;
			}
			marker.setRouteNumber(routeNumbers[index]);
			markers.add(marker);
		}
		mapWidget.setEditMarkers(markers);
	}

	/** A crag takes routes and an area crags, tapped on the map. */
	private void onPoiTapped(DisplayableGeoNode poi) {
		if (relation == null) {
			return;
		}
		GeoNode tapped = poi.getGeoNode();
		OsmEntity.EntityClimbingType wanted = isArea() ? OsmEntity.EntityClimbingType.crag : OsmEntity.EntityClimbingType.route;
		if (!EntityOsmType.node.name().equals(tapped.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE,
				EntityOsmType.node.name())) || tapped.getNodeType() != wanted) {
			refuseTapped();
			return;
		}
		addTapped(EntityOsmType.node, tapped.osmID);
	}

	private void onCollectionTapped(OsmCollectionEntity collection) {
		if (relation == null) {
			return;
		}
		if (!isArea() || collection.osmType != EntityOsmType.relation
				|| collection.entityClimbingType != EntityClimbingType.crag
				|| collection.osmID == relation.osmID) {
			refuseTapped();
			return;
		}
		addTapped(EntityOsmType.relation, collection.osmID);
	}

	private void refuseTapped() {
		Toast.makeText(this, isArea() ? R.string.only_crags_in_area : R.string.only_routes_in_crag,
				Toast.LENGTH_SHORT).show();
	}

	private void addTapped(EntityOsmType osmType, long osmId) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<OsmEntity>() {
			private int routeCount;

			@Override
			protected OsmEntity doWork() {
				OsmEntity member = store.load(osmType, osmId);
				routeCount = countRoutes(ClimbingRouteCounter.forDatabase(EditRelationActivity.this),
						member);
				return member;
			}

			@Override
			protected void thenDoUiRelatedWork(OsmEntity member) {
				if (member == null) {
					return;
				}
				String name = member.getTags().optString(ClimbingTags.KEY_NAME);
				if (memberList.contains(member)) {
					Toast.makeText(EditRelationActivity.this,
							getString(R.string.member_already_added, name), Toast.LENGTH_SHORT).show();
					return;
				}
				memberList.add(member, routeCount);
				Toast.makeText(EditRelationActivity.this,
						getString(R.string.member_added, name), Toast.LENGTH_SHORT).show();
			}
		});
	}

	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.ButtonCancel) {
			finish();
		} else if (relation == null) {
			// Still loading.
			return;
		} else if (id == R.id.ButtonSave) {
			JSONObject edited = resolveEdits();
			if (edited == null) {
				return;
			}
			if (relation.getMembers().length() > 0) {
				save(edited);
				return;
			}
			new AlertDialog.Builder(this)
					.setMessage(getString(R.string.crag_deleted_when_empty, editNode.getName()))
					.setPositiveButton(android.R.string.ok, (dialog, which) -> save(edited))
					.setNegativeButton(android.R.string.cancel, null)
					.show();
		} else if (id == R.id.ButtonDelete) {
			String message = getString(R.string.delete_relation_message);
			List<String> areas = areaMembership != null
					? areaMembership.storedParentNames() : Collections.emptyList();
			if (!areas.isEmpty()) {
				message += "\n\n" + getString(R.string.delete_also_from_areas, TextUtils.join(", ", areas));
			}
			new AlertDialog.Builder(this)
					.setTitle(getString(R.string.delete_confirmation, editNode.getName()))
					.setMessage(message)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setPositiveButton(android.R.string.yes, (dialog, which) ->
							Constants.DB_EXECUTOR.execute(new UiRelatedTask<Boolean>() {
								@Override
								protected Boolean doWork() {
									store.deleteRelation(relation.osmID);
									return true;
								}

								@Override
								protected void thenDoUiRelatedWork(Boolean result) {
									finish();
								}
							}))
					.setNegativeButton(android.R.string.no, null)
					.show();
		}
	}

	/**
	 * Hands the relation back to the member's editor, or stores it, in both cases with the areas
	 * it joined or left. Without members it is deleted.
	 */
	private void save(JSONObject edited) {
		if (partOfMemberEdit) {
			Intent result = new Intent().putExtra(EXTRA_RELATION_JSON, edited.toString());
			if (areaMembership != null) {
				// The member's editor leaves out a crag it doesn't keep.
				putParents(result, areaMembership.changedParents());
			}
			setResult(RESULT_OK, result);
			finish();
			return;
		}
		OsmCollectionEntity saved = new OsmCollectionEntity(edited);
		List<OsmCollectionEntity> relations = new ArrayList<>();
		relations.add(saved);
		if (saved.getMembers().length() > 0 && areaMembership != null) {
			relations.addAll(areaMembership.changedParents());
		}
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				store.save(Collections.emptyList(), relations);
				return true;
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean result) {
				finish();
			}
		});
	}

	private void buildPopupMenu() {
		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(view -> {
			if (relation == null) {
				return;
			}
			PopupMenu popup = new PopupMenu(EditRelationActivity.this, view);
			popup.getMenuInflater().inflate(R.menu.edit_options, popup.getMenu());
			popup.setOnMenuItemClickListener(item -> {
				int id = item.getItemId();
				GeoNode located = locatedRelation();
				if (id == R.id.advanceEditor) {
					JSONObject edited = resolveEdits();
					if (edited != null) {
						Intent intent = new Intent(this, EditNodeAdvancedActivity.class);
						intent.putExtra("nodeJson", edited.toString());
						startActivityForResult(intent, REQUEST_ADVANCED_EDITOR);
					}
				} else if (id == R.id.openStreetMapEditor) {
					String url = relation.isNew()
							? String.format(Locale.ROOT, "https://www.openstreetmap.org/edit#map=19/%f/%f",
							located.decimalLatitude, located.decimalLongitude)
							: String.format(Locale.ROOT, "https://www.openstreetmap.org/edit?relation=%d",
							relation.osmID);
					openExternalEditor(url);
				} else if (id == R.id.vespucci) {
					MapBounds bbox = DataManager.computeBoundingBox(new Vector4d(
							located.decimalLatitude, located.decimalLongitude, 0, 0), 100);
					String url = String.format(Locale.ROOT,
							"josm:/load_and_zoom?left=%f&bottom=%f&right=%f&top=%f",
							bbox.getWest(), bbox.getSouth(), bbox.getEast(), bbox.getNorth());
					if (!relation.isNew()) {
						url = url + "&select=r" + relation.osmID;
					}
					openExternalEditor(url);
				}
				return true;
			});
			popup.show();
		});
	}

	private void openExternalEditor(String url) {
		try {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			finish();
		} catch (ActivityNotFoundException e) {
			DialogBuilder.showErrorDialog(this, getString(R.string.no_josm_app), null);
		}
	}

	/**
	 * The route counts of the crags among the members, by the same keys, which their pins show;
	 * runs off the UI thread.
	 */
	private Map<String, Integer> countRoutes(Map<String, OsmEntity> members) {
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(this);
		Map<String, Integer> result = new HashMap<>();
		for (Map.Entry<String, OsmEntity> member : members.entrySet()) {
			result.put(member.getKey(), countRoutes(routeCounter, member.getValue()));
		}
		return result;
	}

	/** The routes of a crag, which its pin shows; 0 for a route, whose pin shows none. */
	private static int countRoutes(ClimbingRouteCounter routeCounter, OsmEntity member) {
		return member instanceof OsmCollectionEntity
				? routeCounter.summarize((OsmCollectionEntity) member).getRouteCount() : 0;
	}

	private static OsmEntity fromJson(JSONObject json) {
		if (EntityOsmType.node.name().equals(json.optString(ClimbingTags.KEY_TYPE))) {
			return new OsmNode(json);
		}
		return new OsmCollectionEntity(json);
	}

	@Override
	protected void onResume() {
		super.onResume();
		deviceLocationManager.requestUpdates(this);
		orientationManager.requestUpdates(this);
	}

	@Override
	protected void onPause() {
		deviceLocationManager.stopUpdates();
		orientationManager.stopUpdates();
		super.onPause();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_AREA_EDITOR && resultCode == RESULT_OK) {
			try {
				areaMembership.parentEdited(new OsmCollectionEntity(
						new JSONObject(data.getStringExtra(EXTRA_RELATION_JSON))));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return;
		}
		if (requestCode == REQUEST_ADVANCED_EDITOR && resultCode == RESULT_OK) {
			try {
				// The advanced editor's tags are taken as they are, and the form starts from them.
				originalJson = new JSONObject(data.getStringExtra("nodeJson"));
			} catch (JSONException e) {
				e.printStackTrace();
				return;
			}
			buildTagSections();
		}
	}
}
