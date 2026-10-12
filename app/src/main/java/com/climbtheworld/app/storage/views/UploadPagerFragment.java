package com.climbtheworld.app.storage.views;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.OAuthActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.OsmManager;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import needle.Needle;
import needle.UiRelatedTask;
import oauth.signpost.exception.OAuthException;

public class UploadPagerFragment extends DataFragment
		implements IPagerViewFragment, View.OnClickListener {

	/** Separates the type from the id in the key a list row keeps in its hidden itemID view. */
	private static final String KEY_SEPARATOR = "|";

	public UploadPagerFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		super(parent, viewID, new HashMap<>());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
			if (OAuthHelper.needsAuthentication(Configs.instance(parent))) {
				DialogBuilder.showErrorDialog(parent.get(),
						parent.get().getString(R.string.oauth_failed), null);
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
						List<OsmEntity> updates = new OsmEditStore(parent.get()).loadChanges();

						for (final OsmEntity entity : updates) {
							final DisplayableGeoNode node = entity instanceof OsmNode
									? DataManagerNew.toDisplayableNode((OsmNode) entity)
									: DataManagerNew.toDisplayableNode((OsmCollectionEntity) entity);
							Needle.onMainThread().execute(new Runnable() {
								@Override
								public void run() {
									final View newViewElement =
											ListViewItemBuilder.getPaddedBuilder(parent.get(),
															null,
															true)
													.setTitle(node.getGeoNode().getName())
													// The array lists the states in EntityState's order.
													.setDescription(getResources().getStringArray(
															R.array.route_update_status)[entity.localUpdateState.ordinal()])
													.setSwitchChecked(true)
													.setIcon(new PoiMarkerDrawable(parent.get(), node))
													.build();

									((TextView) newViewElement.findViewById(R.id.itemID)).setText(
											entity.osmType.name() + KEY_SEPARATOR + entity.osmID);
									newViewElement.setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View view) {
											NodeDialogBuilder.showNodeInfoDialog(parent.get(),
													node.getGeoNode());
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
		final List<String> toChange = new ArrayList<>();

		int id = v.getId();
		if (id == R.id.ButtonRevert) {
			aggregateSelectedItems(findViewById(R.id.changesView), toChange);

			if (toChange.size() == 0) {
				return;
			}

			AlertDialog alertDialog = new android.app.AlertDialog.Builder(parent.get())
					.setTitle(R.string.revert_confirmation)
					.setMessage(R.string.revert_confirmation_message)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int whichButton) {
							Constants.DB_EXECUTOR
									.execute(new UiRelatedTask<Boolean>() {
										@Override
										protected Boolean doWork() {
											OsmEditStore store = new OsmEditStore(parent.get());
											for (String key : toChange) {
												store.revert(typeOf(key), idOf(key));
											}
											Globals.showNotifications(parent.get());
											return true;
										}

										@Override
										protected void thenDoUiRelatedWork(Boolean result) {
											pushTab();
										}
									});
						}
					})
					.setNegativeButton(android.R.string.no, null).create();

			Drawable icon =
					AppCompatResources.getDrawable(parent.get(),
									android.R.drawable.ic_dialog_alert)
							.mutate();
			icon.setTint(parent.get().getResources().getColor(android.R.color.holo_orange_light));

			alertDialog.setIcon(icon);
			alertDialog.create();
			alertDialog.show();
		} else if (id == R.id.ButtonPush) {
			aggregateSelectedItems(findViewById(R.id.changesView), toChange);

			if (toChange.size() == 0) {
				return;
			}

			if (OAuthHelper.needsAuthentication(configs)) {
				DialogBuilder.showLoadingDialogue(parent.get(),
						getResources().getString(R.string.loading_message), null);

				Intent intent = new Intent(parent.get(), OAuthActivity.class);
				parent.get().startActivityForResult(intent, Constants.OPEN_OAUTH_ACTIVITY);
			} else {
				pushToOsm();
			}
		}
	}

	private void aggregateSelectedItems(ViewGroup listView, List<String> selectedList) {
		for (int i = 0; i < listView.getChildCount(); i++) {
			View child = listView.getChildAt(i);
			SwitchCompat checkBox = child.findViewById(R.id.switchTypeEnabled);
			if (checkBox.isChecked()) {
				TextView nodeID = child.findViewById(R.id.itemID);
				selectedList.add(nodeID.getText().toString());
			}
		}
	}

	private static OsmEntity.EntityOsmType typeOf(String key) {
		return OsmEntity.EntityOsmType.valueOf(key.substring(0, key.indexOf(KEY_SEPARATOR)));
	}

	private static long idOf(String key) {
		return Long.parseLong(key.substring(key.indexOf(KEY_SEPARATOR) + 1));
	}

	public void pushToOsm() {
		DialogBuilder.showLoadingDialogue(parent.get(),
				parent.get().getString(R.string.osm_preparing_data), null);

		final List<String> selected = new ArrayList<>();
		aggregateSelectedItems(findViewById(R.id.changesView), selected);
		// This uploader sends nodes only.
		final List<Long> toChange = new ArrayList<>();
		for (String key : selected) {
			if (typeOf(key) == OsmEntity.EntityOsmType.node) {
				toChange.add(idOf(key));
			}
		}

		OsmManager osm = null;
		try {
			osm = new OsmManager(parent.get());
		} catch (OAuthException e) {
			DialogBuilder.showErrorDialog(parent.get(),
					parent.get().getString(R.string.oauth_failed), null);
		}
		if (osm != null) {
			osm.pushData(toChange, this);
		} else {
			DialogBuilder.dismissLoadingDialogue();
		}
	}
}
