package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;

import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;

import needle.UiRelatedTask;

public class NodeDialogBuilder {
	private static final int INFO_DIALOG_STYLE_ICON_SIZE = Globals.convertDpToPixel(10).intValue();

	private NodeDialogBuilder() {
		//hide constructor
	}

	private static void setContactData(AppCompatActivity activity, View result, GeoNode poi) {
		StringBuilder website = new StringBuilder();
		try {
			URL url = new URL(poi.getWebsite());
			website.append("<a href=").append(url.toString()).append(">").append(url.toString()).append("</a>");
		} catch (MalformedURLException ignored) {
			website.append(poi.getWebsite());
		}
		((TextView) result.findViewById(R.id.editWebsite)).setText(Html.fromHtml(website.toString()));
		((TextView) result.findViewById(R.id.editWebsite)).setMovementMethod(LinkMovementMethod.getInstance()); //activate links

		((TextView) result.findViewById(R.id.editPhone)).setText(poi.getPhone());
		((TextView) result.findViewById(R.id.editNo)).setText(poi.getKey(GeoNode.KEY_ADDR_STREETNO));
		((TextView) result.findViewById(R.id.editStreet)).setText(poi.getKey(GeoNode.KEY_ADDR_STREET));
		((TextView) result.findViewById(R.id.editUnit)).setText(poi.getKey(GeoNode.KEY_ADDR_UNIT));
		((TextView) result.findViewById(R.id.editCity)).setText(poi.getKey(GeoNode.KEY_ADDR_CITY));
		((TextView) result.findViewById(R.id.editProvince)).setText(poi.getKey(GeoNode.KEY_ADDR_PROVINCE));
		((TextView) result.findViewById(R.id.editPostcode)).setText(poi.getKey(GeoNode.KEY_ADDR_POSTCODE));
	}

	private static void setLocationData(AppCompatActivity activity, View result, GeoNode poi) {
		double distance = poi.distanceMeters;

		if (Globals.virtualCamera != null) {
			distance = AugmentedRealityUtils.calculateDistance(Globals.virtualCamera, poi);
		}

		((TextView) result.findViewById(R.id.editDistance)).setText(Globals.getDistanceString(distance));

		((TextView) result.findViewById(R.id.editLatitude)).setText(String.valueOf(poi.decimalLatitude));
		((TextView) result.findViewById(R.id.editLongitude)).setText(String.valueOf(poi.decimalLongitude));
		((TextView) result.findViewById(R.id.editElevation)).setText(Globals.getDistanceString(poi.getKey(GeoNode.KEY_ELEVATION), "m"));
	}

	private static void setClimbingStyle(AppCompatActivity parent, View result, GeoNode poi) {
		ViewGroup styles = result.findViewById(R.id.containerClimbingStylesView);

		for (GeoNode.ClimbingStyle styleName : Sorters.sortStyles(parent, poi.getClimbingStyles())) {
			View customView = ListViewItemBuilder.getNonPaddedBuilder(parent)
					.setDescription(parent.getResources().getString(styleName.getNameId()))
					.setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(styleName), INFO_DIALOG_STYLE_ICON_SIZE))
					.build();

			styles.addView(customView);
		}
	}

	private static View buildRouteDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		Configs configs = Configs.instance(activity);
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_route, container, false);

		((TextView) result.findViewById(R.id.editLength)).setText(Globals.getDistanceString(poi.getKey(GeoNode.KEY_LENGTH)));
		((TextView) result.findViewById(R.id.editPitches)).setText(poi.getKey(GeoNode.KEY_PITCHES));
		((TextView) result.findViewById(R.id.editBolts)).setText(poi.getKey(GeoNode.KEY_BOLTS));

		((TextView) result.findViewById(R.id.gradingTitle)).setText(activity.getResources().getString(R.string.grade_system,
				activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.gradeTextView)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG)));

		result.findViewById(R.id.gradeTextView).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG)).getDefaultColor());

		setClimbingStyle(activity, result, poi);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		setLocationData(activity, result, poi);

		return result;
	}

	private static View buildArtificialDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_artificial, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		setLocationData(activity, result, poi);

		if (poi.isArtificialTower()) {
			((TextView) result.findViewById(R.id.editCentreType)).setText(R.string.artificial_tower);
		} else {
			((TextView) result.findViewById(R.id.editCentreType)).setText(R.string.climbing_gym);
		}

		return result;
	}

	private static View buildCragDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		Configs configs = Configs.instance(activity);
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_crag, container, false);
		((TextView) result.findViewById(R.id.editNumRoutes)).setText(poi.getKey(GeoNode.KEY_ROUTES));
		((TextView) result.findViewById(R.id.editMinLength)).setText(poi.getKey(GeoNode.KEY_MIN_LENGTH));
		((TextView) result.findViewById(R.id.editMaxLength)).setText(poi.getKey(GeoNode.KEY_MAX_LENGTH));

		((TextView) result.findViewById(R.id.minGrading)).setText(
				activity.getResources().getString(R.string.min_grade,
						activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.minGradeSpinner)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)));

		result.findViewById(R.id.minGradeSpinner).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MIN)).getDefaultColor());

		((TextView) result.findViewById(R.id.maxGrading)).setText(
				activity.getResources().getString(R.string.max_grade,
						activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.maxGradeSpinner)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)));

		result.findViewById(R.id.maxGradeSpinner).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG_MAX)).getDefaultColor());

		setClimbingStyle(activity, result, poi);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		setLocationData(activity, result, poi);

		return result;
	}

	private static View buildUnknownDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_unknown, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(GeoNode.KEY_DESCRIPTION));
		setLocationData(activity, result, poi);

		TableLayout table = result.findViewById(R.id.tableAllTags);

		int padding = Globals.convertPixelsToDp(5).intValue();
		TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.MATCH_PARENT, 1f);

		JSONObject tags = poi.getTags();
		Iterator<String> keyIt = tags.keys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			TableRow row = new TableRow(activity);

			TextView item = new TextView(activity);
			item.setText(key);
			item.setBackground(ContextCompat.getDrawable(activity, R.drawable.cell_shape));
			item.setPadding(padding, padding, padding, padding);
			item.setLayoutParams(params);
			row.addView(item);
			item = new TextView(activity);
			item.setText(tags.optString(key));
			item.setBackground(ContextCompat.getDrawable(activity, R.drawable.cell_shape));
			item.setPadding(padding, padding, padding, padding);
			item.setLayoutParams(params);
			row.addView(item);

			table.addView(row);
		}

		return result;
	}

	public static void showNodeInfoDialog(final AppCompatActivity activity, final GeoNode poi) {
		DialogBuilder.showLoadingDialogue(activity, activity.getResources().getString(R.string.loading_message), null);
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);

		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Void>() {
			@Override
			protected Void doWork() {
				alertDialog.setCancelable(true);
				alertDialog.setCanceledOnTouchOutside(true);
				alertDialog.setTitle((!poi.getName().isEmpty() ? poi.getName() : " "));

				Drawable nodeIcon = new PoiMarkerDrawable(activity, null, new DisplayableGeoNode(poi), 0, 0);
				alertDialog.setIcon(nodeIcon.getCurrent());

				switch (poi.getNodeType()) {
					case route:
						alertDialog.setView(buildRouteDialog(activity, alertDialog.getListView(), poi));
						break;
					case crag:
						alertDialog.setView(buildCragDialog(activity, alertDialog.getListView(), poi));
						break;
					case artificial:
						alertDialog.setView(buildArtificialDialog(activity, alertDialog.getListView(), poi));
						break;
					case unknown:
					default:
						alertDialog.setView(buildUnknownDialog(activity, alertDialog.getListView(), poi));
						break;
				}

				DialogueUtils.addOkButton(activity, alertDialog);
				DialogueUtils.addEditButton(activity, alertDialog, poi.getID());
				DialogueUtils.addNavigateButton(activity, alertDialog, poi.osmID, poi.getName(), new GeoPoint(poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters));

				return null;
			}

			@Override
			protected void thenDoUiRelatedWork(Void flag) {
				alertDialog.create();
				alertDialog.show();
			}
		});
	}
}