package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
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

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.editor.ArtificialTags;
import com.climbtheworld.app.map.editor.ContactTags;
import com.climbtheworld.app.map.editor.CragTags;
import com.climbtheworld.app.map.editor.GeneralTags;
import com.climbtheworld.app.map.editor.ITags;
import com.climbtheworld.app.map.editor.OtherTags;
import com.climbtheworld.app.map.editor.RouteTags;
import com.climbtheworld.app.map.editor.SpinnerMarkerArrayAdapter;
import com.climbtheworld.app.map.marker.GeoNodeMapMarker;
import com.climbtheworld.app.map.widget.MapViewWidget;
import com.climbtheworld.app.map.widget.MapWidgetBuilder;
import com.climbtheworld.app.sensors.location.DeviceLocationManager;
import com.climbtheworld.app.sensors.location.ILocationListener;
import com.climbtheworld.app.sensors.orientation.IOrientationListener;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.FolderOverlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import needle.UiRelatedTask;

public class EditNodeActivity extends AppCompatActivity implements IOrientationListener, ILocationListener {
	private GeoNode editNode;
	private MapViewWidget mapWidget;
	private DeviceLocationManager deviceLocationManager;
	private OrientationManager orientationManager;
	private Spinner dropdownType;
	private ViewGroup containerTags;
	private GeneralTags genericTags;

	private final Map<GeoNode.NodeTypes, List<ITags>> nodeTypesTags = new HashMap<>();
	private final List<ITags> allTagsHandlers = new ArrayList<>();

	private Intent intent;
	private long editNodeID;

	FolderOverlay editMarkersFolder = new FolderOverlay();

	private final static int locationUpdate = 5000;
	public static final double MAP_EDIT_ZOOM_LEVEL = 18;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_node);

		intent = getIntent();
		editNodeID = intent.getLongExtra("poiID", 0);

		doDatabaseWork(editNodeID);

		this.dropdownType = findViewById(R.id.spinnerNodeType);
		containerTags = findViewById(R.id.containerTags);

		mapWidget = MapWidgetBuilder.getBuilder(this, false)
				.enableAutoDownload()
				.setMapAutoFollow(false)
				.setFilterMethod(MapViewWidget.FilterType.GHOSTS)
				.setZoom(MAP_EDIT_ZOOM_LEVEL)
				.build();
		mapWidget.addTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				int action = motionEvent.getAction();
				ViewParent scrollParent = view.getParent();
				while (scrollParent != null && !(scrollParent instanceof ScrollView)) {
					scrollParent = scrollParent.getParent();
				}

				if (scrollParent != null) {
					switch (action) {
						case MotionEvent.ACTION_DOWN:
							// Disallow ScrollView to intercept touch events.
							scrollParent.requestDisallowInterceptTouchEvent(true);
							break;

						case MotionEvent.ACTION_UP:
							// Allow ScrollView to intercept touch events.
							scrollParent.requestDisallowInterceptTouchEvent(false);
							break;
					}
				}

				if ((motionEvent.getAction() == MotionEvent.ACTION_UP) && ((motionEvent.getEventTime() - motionEvent.getDownTime()) < UIConstants.ON_TAP_DELAY_MS)) {
					Point screenCoord = new Point();
					mapWidget.getOsmMap().getProjection().unrotateAndScalePoint((int) motionEvent.getX(), (int) motionEvent.getY(), screenCoord);
					GeoPoint gp = (GeoPoint) mapWidget.getOsmMap().getProjection().fromPixels(screenCoord.x, screenCoord.y);

					editNode.updatePOILocation(gp.getLatitude(), gp.getLongitude(), editNode.elevationMeters);
					updateMapMarker();
					genericTags.updateLocation(); //update location text boxes.

					return true;
				}
				return false;
			}
		});

		buildPopupMenu();

		//location
		deviceLocationManager = new DeviceLocationManager(this, locationUpdate);

		orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_NORMAL);
		orientationManager.addListener(this);
	}

	private void doDatabaseWork(final long poiId) {
		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<GeoNode>() {
					@Override
					protected GeoNode doWork() {
						AppDatabase appDB = AppDatabase.getInstance(EditNodeActivity.this);
						if (poiId == 0) {
							GeoNode tmpPoi = new GeoNode(intent.getDoubleExtra("poiLat", Globals.virtualCamera.decimalLatitude),
									intent.getDoubleExtra("poiLon", Globals.virtualCamera.decimalLongitude),
									Globals.virtualCamera.elevationMeters);

							tmpPoi.setClimbingType(GeoNode.NodeTypes.route);
							tmpPoi.osmID = appDB.getNewNodeID();

							return tmpPoi;
						} else {
							return appDB.nodeDao().loadNode(poiId);
						}
					}

					@Override
					protected void thenDoUiRelatedWork(GeoNode result) {
						editNode = result;
						buildUi();
						updateMapMarker();
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

						switch (item.getItemId()) {
							case R.id.advanceEditor:
								intent = new Intent(EditNodeActivity.this, EditNodeAdvancedActivity.class);

								GeoNode tempNode = new GeoNode(editNode.jsonNodeInfo);
								synchronizeNode(tempNode);

								intent.putExtra("nodeJson", tempNode.toJSONString());
								startActivityForResult(intent, 0);
								break;

							case R.id.openStreetMapEditor:
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
								break;

							case R.id.vespucci:
								BoundingBox bbox = DataManager.computeBoundingBox(new Vector4d(editNode.decimalLatitude, editNode.decimalLongitude, editNode.elevationMeters, 0), 10);
								urlFormat = String.format(Locale.getDefault(), "josm:/load_and_zoom?left=%f&bottom=%f&right=%f&top=%f",
										bbox.getLonWest(), bbox.getLatSouth(), bbox.getLonEast(), bbox.getLatNorth());

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
								break;
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
		ITags routeTags = new RouteTags(editNode, this, containerTags);
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
		tags.add(contactInfoTags);
		nodeTypesTags.put(GeoNode.NodeTypes.route, tags);

		tags = new ArrayList<>();
		tags.add(generalTags);
		tags.add(cragTags);
		tags.add(contactInfoTags);
		nodeTypesTags.put(GeoNode.NodeTypes.crag, tags);

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
		mapWidget.addCustomOverlay(editMarkersFolder);
		mapWidget.centerOnGoePoint(Globals.geoNodeToGeoPoint(editNode));

		buildNodeFragments();

		dropdownType.setOnItemSelectedListener(null);
		dropdownType.setAdapter(new SpinnerMarkerArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, GeoNode.NodeTypes.values(), editNode));
		int pos = Arrays.asList(GeoNode.NodeTypes.values()).indexOf(editNode.getNodeType());
		dropdownType.setSelection(pos);
		dropdownType.setTag(pos);
		if (editNodeID == 0) {
			dropdownType.performClick();
		}
		dropdownType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
//                if((int)dropdownType.getTag() != pos) //this is used to prevent self on select event.
				{
					dropdownType.setTag(pos);
					switchNodeType(GeoNode.NodeTypes.values()[pos]);
					updateMapMarker();
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {

			}
		});
	}

	private void switchNodeType(GeoNode.NodeTypes type) {
		for (ITags tags : nodeTypesTags.get(editNode.getNodeType())) {
			tags.hideTags();
		}

		editNode.setClimbingType(type);

		for (ITags tags : nodeTypesTags.get(editNode.getNodeType())) {
			tags.showTags();
		}
	}

	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.ButtonCancel:
				finish();
				break;

			case R.id.ButtonSave:
				if (synchronizeNode(editNode)) {

					editNode.updateDate = System.currentTimeMillis();
					editNode.localUpdateState = ClimbingTags.TO_UPDATE_STATE;
					Constants.DB_EXECUTOR
							.execute(new UiRelatedTask<Boolean>() {
								@Override
								protected Boolean doWork() {
									AppDatabase appDB = AppDatabase.getInstance(EditNodeActivity.this);
									if (editNode.osmID < 0 && editNode.localUpdateState == ClimbingTags.TO_DELETE_STATE) {
										appDB.nodeDao().deleteNodes(editNode);
									} else {
										appDB.nodeDao().insertNodesWithReplace(editNode);
									}

									return true;
								}

								@Override
								protected void thenDoUiRelatedWork(Boolean result) {
									finish();
								}
							});
					finish();
				}
				break;

			case R.id.ButtonDelete:
				new AlertDialog.Builder(this)
						.setTitle(getResources().getString(R.string.delete_confirmation, editNode.getName()))
						.setMessage(R.string.delete_confirmation_message)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog, int whichButton) {
								editNode.updateDate = System.currentTimeMillis();
								editNode.localUpdateState = ClimbingTags.TO_DELETE_STATE;

								Constants.DB_EXECUTOR
										.execute(new UiRelatedTask<Boolean>() {
											@Override
											protected Boolean doWork() {
												AppDatabase appDB = AppDatabase.getInstance(EditNodeActivity.this);
												if (editNode.osmID < 0 && editNode.localUpdateState == ClimbingTags.TO_DELETE_STATE) {
													appDB.nodeDao().deleteNodes(editNode);
												} else {
													appDB.nodeDao().insertNodesWithReplace(editNode);
												}

												return true;
											}

											@Override
											protected void thenDoUiRelatedWork(Boolean result) {
												finish();
											}
										});
							}
						})
						.setNegativeButton(android.R.string.no, null).show();
				break;
		}
	}

	public void updateMapMarker() {
		editMarkersFolder.getItems().clear();
		editMarkersFolder.add(new GeoNodeMapMarker(this, mapWidget.getOsmMap(), new DisplayableGeoNode(editNode, false)));
		mapWidget.invalidate(true);
	}

	private boolean synchronizeNode(GeoNode node) {
		boolean success = true;
		List<ITags> activeTags = nodeTypesTags.get(node.getNodeType());
		for (ITags tag : allTagsHandlers) {
			if (!activeTags.contains(tag)) {
				tag.cancelNode(node);
			}
		}

		for (ITags tags : activeTags) {
			success = tags.saveToNode(node);
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

		mapWidget.onLocationChange(Globals.geoNodeToGeoPoint(Globals.virtualCamera));
	}

	@Override
	protected void onResume() {
		super.onResume();
		Globals.onResume(this);

		mapWidget.onResume();

		deviceLocationManager.requestUpdates(this);
		orientationManager.onResume();
	}

	@Override
	protected void onPause() {
		deviceLocationManager.removeUpdates();
		orientationManager.onPause();
		Globals.onPause(this);

		mapWidget.onPause();

		super.onPause();
	}

	protected void onActivityResult(int requestCode, int resultCode,
	                                Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			String nodeJson = data.getStringExtra("nodeJson");
			try {
				editNode = new GeoNode(nodeJson);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			buildUi();
		}
	}
}
