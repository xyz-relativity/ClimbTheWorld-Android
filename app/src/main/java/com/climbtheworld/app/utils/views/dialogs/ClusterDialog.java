package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.GeoNodeMapMarker;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.constants.UIConstants;
import com.climbtheworld.app.utils.views.FilteredListAdapter;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;

import org.osmdroid.bonuspack.clustering.StaticCluster;
import org.osmdroid.views.overlay.Marker;

import needle.UiRelatedTask;

public class ClusterDialog {
	private static View buildClusterDialog(final AppCompatActivity parent,
	                                       final ViewGroup container,
	                                       final StaticCluster cluster) {
		View result = parent.getLayoutInflater().inflate(R.layout.fragment_dialog_cluster, container, false);

		Drawable nodeIcon = cluster.getMarker().getIcon();
		DialogueUtils.buildTitle(parent, result, 0, parent.getResources().getString(R.string.points_of_interest_value, cluster.getSize()), nodeIcon, Globals.geoPointToGeoNode(cluster.getPosition()));

		DialogueUtils.setLocation(parent, result, new GeoNode(cluster.getPosition().getLatitude(), cluster.getPosition().getLongitude(), cluster.getPosition().getAltitude()));

		FilteredListAdapter<Marker> viewAdaptor = new FilteredListAdapter<Marker>(MarkerUtils.clusterToList(cluster)) {
			@Override
			protected boolean isVisible(int i, String filter) {
				GeoNodeMapMarker marker = (GeoNodeMapMarker) initialList.get(i);
				return marker.getGeoNode().getName().toLowerCase().contains(filter);
			}

			@Override
			public View getView(int i, View view, ViewGroup viewGroup) {
				final GeoNodeMapMarker marker = (GeoNodeMapMarker) visibleList.get(i);

				view = ListViewItemBuilder.getPaddedBuilder(parent, view, true)
						.setTitle(marker.getGeoNode().getName())
						.setDescription(DialogueUtils.buildDescription(parent, marker.getGeoNode()))
						.setIcon(marker.getIcon())
						.setIconSize(UIConstants.POI_TYPE_LIST_ICON_SIZE, UIConstants.POI_TYPE_LIST_ICON_SIZE)
						.build();

				view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						NodeDialogBuilder.showNodeInfoDialog(parent, (marker.getGeoNode()));
					}
				});

				((TextView) view.findViewById(R.id.itemID)).setText(String.valueOf(marker.getId()));
				return view;
			}
		};

		EditText filter = result.findViewById(R.id.editFind);
		viewAdaptor.applyFilter(filter.getText().toString());

		filter.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				viewAdaptor.applyFilter(s.toString());
			}
		});

		ListView itemsContainer = result.findViewById(R.id.listGroupItems);

		itemsContainer.setAdapter(viewAdaptor);
		return result;
	}

	public static void showClusterDialog(final AppCompatActivity parent, final StaticCluster cluster) {
		DialogBuilder.showLoadingDialogue(parent, parent.getResources().getString(R.string.loading_message), null);
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(parent, true);

		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Void>() {
			@Override
			protected Void doWork() {
				alertDialog.setCancelable(true);
				alertDialog.setCanceledOnTouchOutside(true);

				alertDialog.setView(buildClusterDialog(parent, alertDialog.getListView(), cluster));
				return null;
			}

			@Override
			protected void thenDoUiRelatedWork(Void flag) {
				alertDialog.create();

				//Hide soft-input.
				alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
					@Override
					public void onShow(DialogInterface dialog) {
						InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
					}
				});

				alertDialog.show();
				DialogBuilder.dismissLoadingDialogue();
			}
		});
	}
}
