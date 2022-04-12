package com.climbtheworld.app.storage.views;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.OAuthActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.OsmManager;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.AppDatabase;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import needle.Needle;
import needle.UiRelatedProgressTask;
import oauth.signpost.exception.OAuthException;

public class UploadPagerFragment extends DataFragment implements IPagerViewFragment, View.OnClickListener {

	private List<GeoNode> updates;

	public UploadPagerFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		super(parent, viewID, new HashMap<>());
		downloadManager = new DataManager();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
			if (OAuthHelper.needsAuthentication(Configs.instance(parent))) {
				DialogBuilder.showErrorDialog(parent, parent.getString(R.string.oauth_failed), null);
			} else {
				pushToOsm();
			}
		}

		if (requestCode == Constants.OPEN_EDIT_ACTIVITY) {
			pushTab();
		}
	}

	@Override
	public int getViewId() {
		return this.viewID;
	}

	@Override
	public void onCreate(ViewGroup view) {
		this.view = view;
		findViewById(R.id.ButtonPush).setOnClickListener(this);
		findViewById(R.id.ButtonRevert).setOnClickListener(this);
		pushTab();
	}

	@Override
	public void onDestroy(ViewGroup view) {
	}

	@Override
	public void onViewSelected() {

	}

	public void pushTab() {
		final ViewGroup tab = findViewById(R.id.changesView);
		tab.removeAllViews();

		Constants.ASYNC_TASK_EXECUTOR
				.execute(new Runnable() {
					@Override
					public void run() {
						updates = AppDatabase.getInstance(parent).nodeDao().loadAllUpdatedNodes();

						for (final GeoNode node : updates) {
							Needle.onMainThread().execute(new Runnable() {
								@Override
								public void run() {
									final View newViewElement = ListViewItemBuilder.getPaddedBuilder(parent, null, true)
											.setTitle(node.getName())
											.setDescription(getResources().getStringArray(R.array.route_update_status)[node.localUpdateState])
											.setSwitchChecked(true)
											.setIcon(new PoiMarkerDrawable(parent, null, new DisplayableGeoNode(node), 0, 0))
											.build();

									((TextView) newViewElement.findViewById(R.id.itemID)).setText(String.valueOf(node.osmID));
									newViewElement.setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View view) {
											NodeDialogBuilder.showNodeInfoDialog(parent, node);
										}
									});
									tab.addView(newViewElement);
								}
							});
						}
					}
				});
	}

	@Override
	public void onClick(View v) {
		final List<Long> toChange = new ArrayList<>();

		switch (v.getId()) {
			case R.id.ButtonRevert: {
				aggregateSelectedItems(findViewById(R.id.changesView), toChange);

				if (toChange.size() == 0) {
					break;
				}

				AlertDialog alertDialog = new android.app.AlertDialog.Builder(parent)
						.setTitle(R.string.revert_confirmation)
						.setMessage(R.string.revert_confirmation_message)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog, int whichButton) {
								final List<GeoNode> undoNew = new ArrayList<>();
								final List<GeoNode> undoDelete = new ArrayList<>();
								final List<GeoNode> undoUpdates = new ArrayList<>();

								for (GeoNode node : updates) {
									if (!toChange.contains(node.getID())) {
										continue;
									}
									if (node.localUpdateState == ClimbingTags.TO_DELETE_STATE && node.osmID >= 0) {
										node.localUpdateState = ClimbingTags.CLEAN_STATE;
										undoDelete.add(node);
									}
									if (node.localUpdateState == ClimbingTags.TO_UPDATE_STATE && node.osmID < 0) {
										undoNew.add(node);
									}
									if (node.localUpdateState == ClimbingTags.TO_UPDATE_STATE && node.osmID >= 0) {
										undoUpdates.add(node);
									}
								}

								Constants.DB_EXECUTOR
										.execute(new UiRelatedProgressTask<Boolean, String>() {
											@Override
											protected Boolean doWork() {
												AppDatabase appDB = AppDatabase.getInstance(parent);
												appDB.nodeDao().updateNodes(undoDelete.toArray(new GeoNode[0]));
												updates.removeAll(undoDelete);
												appDB.nodeDao().deleteNodes(undoNew.toArray(new GeoNode[0]));
												updates.removeAll(undoNew);

												Map<Long, DisplayableGeoNode> poiMap = new HashMap<>();
												List<Long> toUpdate = new ArrayList<>();
												for (GeoNode node : undoUpdates) {
													toUpdate.add(node.getID());
												}
												try {
													downloadManager.downloadIDs(parent, toUpdate, poiMap);
												} catch (IOException | JSONException e) {
													publishProgress(e.getMessage());
													return false;
												}

												downloadManager.pushToDb(parent, poiMap, true);
												updates.removeAll(undoUpdates);
												Globals.showNotifications(parent);

												return true;
											}

											@Override
											protected void thenDoUiRelatedWork(Boolean result) {
												if (result) {
													pushTab();
												}
											}

											@Override
											protected void onProgressUpdate(String progress) {
												Toast.makeText(parent, parent.getResources().getString(R.string.exception_message,
														progress), Toast.LENGTH_LONG).show();
											}
										});
							}
						})
						.setNegativeButton(android.R.string.no, null).create();

				Drawable icon = parent.getDrawable(android.R.drawable.ic_dialog_alert).mutate();
				icon.setTint(parent.getResources().getColor(android.R.color.holo_orange_light));

				alertDialog.setIcon(icon);
				alertDialog.create();
				alertDialog.show();
			}
			break;

			case R.id.ButtonPush: {
				aggregateSelectedItems(findViewById(R.id.changesView), toChange);

				if (toChange.size() == 0) {
					break;
				}

				if (OAuthHelper.needsAuthentication(configs)) {
					DialogBuilder.showLoadingDialogue(parent, getResources().getString(R.string.loading_message), null);

					Intent intent = new Intent(parent, OAuthActivity.class);
					parent.startActivityForResult(intent, Constants.OPEN_OAUTH_ACTIVITY);
				} else {
					pushToOsm();
				}
			}
			break;
		}
	}

	private void aggregateSelectedItems(ViewGroup listView, List<Long> selectedList) {
		for (int i = 0; i < listView.getChildCount(); i++) {
			View child = listView.getChildAt(i);
			SwitchCompat checkBox = child.findViewById(R.id.switchTypeEnabled);
			if (checkBox.isChecked()) {
				TextView nodeID = child.findViewById(R.id.itemID);
				selectedList.add(Long.parseLong(nodeID.getText().toString()));
			}
		}
	}

	public void pushToOsm() {
		DialogBuilder.showLoadingDialogue(parent, parent.getString(R.string.osm_preparing_data), null);

		final List<Long> toChange = new ArrayList<>();
		aggregateSelectedItems(findViewById(R.id.changesView), toChange);

		OsmManager osm = null;
		try {
			osm = new OsmManager(parent);
		} catch (OAuthException e) {
			DialogBuilder.showErrorDialog(parent, parent.getString(R.string.oauth_failed), null);
		}
		if (osm != null) {
			osm.pushData(toChange, this);
		} else {
			DialogBuilder.dismissLoadingDialogue();
		}
	}
}
