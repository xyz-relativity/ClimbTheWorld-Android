package com.climbtheworld.app.storage.views;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.OAuthActivity;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.oauth.OAuthHelper;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.OsmEditStore;
import com.climbtheworld.app.storage.upload.ChangeSummary;
import com.climbtheworld.app.storage.upload.OsmApi;
import com.climbtheworld.app.storage.upload.OsmUploader;
import com.climbtheworld.app.storage.upload.PendingChange;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.IPagerViewFragment;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.dialogs.DialogBuilder;
import com.climbtheworld.app.utils.views.dialogs.NodeDialogBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import needle.Needle;
import needle.UiRelatedTask;

/**
 * The changes made in the app, to upload to OpenStreetMap or revert. An upload logs in first if
 * needed, asks what the changes are about, and settles conflicts with OSM with the user.
 */
public class UploadPagerFragment extends DataFragment
		implements IPagerViewFragment, View.OnClickListener {

	/** Separates the type from the id in the key a list row keeps in its hidden itemID view. */
	private static final String KEY_SEPARATOR = "|";
	private static final int DIALOG_PADDING = Globals.convertDpToPixel(20).intValue();

	/** The changes to upload once the login started for them is done. */
	private List<String> waitingForLogin;

	public UploadPagerFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		super(parent, viewID, new HashMap<>());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Constants.OPEN_OAUTH_ACTIVITY) {
			List<String> selection = waitingForLogin;
			waitingForLogin = null;
			// A failed login has told the user already.
			if (selection != null && !OAuthHelper.needsAuthentication(configs)) {
				askForComment(selection);
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
							final String description = describe(entity);
							Needle.onMainThread().execute(new Runnable() {
								@Override
								public void run() {
									final View newViewElement =
											ListViewItemBuilder.getPaddedBuilder(parent.get(),
															null,
															true)
													.setTitle(node.getGeoNode().getName())
													.setDescription(description)
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

	/** New, deleted, or what changed: tags, position, members. */
	private String describe(OsmEntity entity) {
		AppCompatActivity activity = parent.get();
		if (entity.localUpdateState == OsmEntity.EntityState.toDelete) {
			return activity.getString(R.string.change_deleted);
		}
		if (entity.isNew()) {
			return activity.getString(R.string.change_new);
		}
		List<String> parts = new ArrayList<>();
		if (entity.baseJson != null) {
			ChangeSummary summary = ChangeSummary.of(entity.baseJson, entity.jsonNodeInfo);
			if (!summary.tags.isEmpty()) {
				parts.add(activity.getString(R.string.change_tags, TextUtils.join(", ", summary.tags)));
			}
			if (summary.moved) {
				parts.add(activity.getString(R.string.change_moved));
			}
			if (summary.members) {
				parts.add(activity.getString(R.string.change_members));
			}
		}
		return parts.isEmpty() ? activity.getString(R.string.change_updated) : TextUtils.join("; ", parts);
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
				waitingForLogin = toChange;
				Intent intent = new Intent(parent.get(), OAuthActivity.class);
				parent.get().startActivityForResult(intent, Constants.OPEN_OAUTH_ACTIVITY);
			} else {
				askForComment(toChange);
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

	/** OSM wants every changeset to say what it is about. */
	private void askForComment(List<String> selection) {
		AppCompatActivity activity = parent.get();
		EditText input = new EditText(activity);
		input.setHint(R.string.changeset_comment_hint);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
				| InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		FrameLayout frame = new FrameLayout(activity);
		frame.setPadding(DIALOG_PADDING, DIALOG_PADDING / 2, DIALOG_PADDING, 0);
		frame.addView(input);

		AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.changeset_comment_title)
				.setView(frame)
				.setPositiveButton(R.string.upload, null)
				.setNegativeButton(android.R.string.cancel, null)
				.create();
		dialog.setOnShowListener(shown -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
				.setOnClickListener(button -> {
					String comment = input.getText().toString().trim();
					if (comment.isEmpty()) {
						input.setError(activity.getString(R.string.changeset_comment_required));
						return;
					}
					dialog.dismiss();
					prepareUpload(selection, comment);
				}));
		dialog.show();
	}

	private void prepareUpload(List<String> selection, String comment) {
		AppCompatActivity activity = parent.get();
		DialogBuilder.showLoadingDialogue(activity, activity.getString(R.string.osm_permission_check), null);
		OsmUploader uploader = new OsmUploader(new OsmEditStore(activity),
				new OsmApi(OAuthHelper.API.apiUrl, OAuthHelper.getToken(configs),
						"ClimbTheWorld/" + Globals.versionName));
		Constants.WEB_EXECUTOR.execute(new UiRelatedTask<OsmUploader.Prepared>() {
			private IOException error;
			private boolean cannotWrite;

			@Override
			protected OsmUploader.Prepared doWork() {
				try {
					if (!uploader.canWrite()) {
						cannotWrite = true;
						return null;
					}
					DialogBuilder.updateLoadingStatus(R.string.osm_comparing);
					return uploader.prepare(new HashSet<>(selection));
				} catch (IOException e) {
					error = e;
					return null;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(OsmUploader.Prepared prepared) {
				if (prepared == null) {
					DialogBuilder.dismissLoadingDialogue();
					if (cannotWrite) {
						OAuthHelper.resetOauth(configs);
						DialogBuilder.showErrorDialog(activity,
								activity.getString(R.string.osm_permission_failed_message), null);
					} else {
						showUploadError(error);
					}
					return;
				}
				if (prepared.conflicts.isEmpty()) {
					send(uploader, prepared, comment);
					return;
				}
				DialogBuilder.dismissLoadingDialogue();
				askAboutConflicts(uploader, prepared, comment);
			}
		});
	}

	private void askAboutConflicts(OsmUploader uploader, OsmUploader.Prepared prepared,
	                               String comment) {
		AppCompatActivity activity = parent.get();
		List<String> names = new ArrayList<>();
		for (PendingChange change : prepared.plan) {
			if (prepared.conflicts.containsKey(change.key())) {
				String name = change.local.optJSONObject(ClimbingTags.KEY_TAGS) != null
						? change.local.optJSONObject(ClimbingTags.KEY_TAGS).optString(ClimbingTags.KEY_NAME) : "";
				names.add(name.isEmpty() ? change.type + " " + change.id : name);
			}
		}
		new AlertDialog.Builder(activity)
				.setTitle(R.string.upload_conflicts_title)
				.setMessage(activity.getString(R.string.upload_conflicts_message,
						TextUtils.join(", ", names)))
				.setPositiveButton(R.string.upload_keep_mine,
						(dialog, which) -> settleAndSend(uploader, prepared, comment, true))
				.setNeutralButton(R.string.upload_use_theirs,
						(dialog, which) -> settleAndSend(uploader, prepared, comment, false))
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void settleAndSend(OsmUploader uploader, OsmUploader.Prepared prepared,
	                           String comment, boolean keepLocal) {
		AppCompatActivity activity = parent.get();
		DialogBuilder.showLoadingDialogue(activity, activity.getString(R.string.osm_preparing_data), null);
		Constants.WEB_EXECUTOR.execute(new UiRelatedTask<Boolean>() {
			@Override
			protected Boolean doWork() {
				if (keepLocal) {
					uploader.keepLocal(prepared);
				} else {
					uploader.useServer(prepared);
				}
				return true;
			}

			@Override
			protected void thenDoUiRelatedWork(Boolean result) {
				send(uploader, prepared, comment);
			}
		});
	}

	private void send(OsmUploader uploader, OsmUploader.Prepared prepared, String comment) {
		AppCompatActivity activity = parent.get();
		String createdBy = activity.getString(R.string.app_name) + " " + Globals.versionName;
		Constants.WEB_EXECUTOR.execute(new UiRelatedTask<Integer>() {
			private IOException error;

			@Override
			protected Integer doWork() {
				try {
					return uploader.upload(prepared, comment, createdBy,
							DialogBuilder::updateLoadingStatus);
				} catch (IOException e) {
					error = e;
					return null;
				}
			}

			@Override
			protected void thenDoUiRelatedWork(Integer uploaded) {
				DialogBuilder.dismissLoadingDialogue();
				pushTab();
				Globals.showNotifications(activity);
				if (uploaded == null) {
					showUploadError(error);
				} else if (uploaded == 0) {
					Toast.makeText(activity, R.string.upload_nothing, Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(activity, activity.getString(R.string.upload_done, uploaded),
							Toast.LENGTH_LONG).show();
				}
			}
		});
	}

	private void showUploadError(IOException error) {
		AppCompatActivity activity = parent.get();
		if (error instanceof OsmApi.OsmApiException
				&& ((OsmApi.OsmApiException) error).code == HttpURLConnection.HTTP_UNAUTHORIZED) {
			OAuthHelper.resetOauth(configs);
			DialogBuilder.showErrorDialog(activity, activity.getString(R.string.osm_login_expired), null);
			return;
		}
		DialogBuilder.showErrorDialog(activity, activity.getString(R.string.upload_failed,
				TextUtils.htmlEncode(String.valueOf(error.getMessage()))), null);
	}
}
