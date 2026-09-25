package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import needle.UiRelatedTask;

public class NodeDialogBuilder {
	private static final int INFO_DIALOG_STYLE_ICON_SIZE = Globals.convertDpToPixel(10).intValue();

	private NodeDialogBuilder() {
		//hide constructor
	}

	private static final class CollectionMember {
		private final GeoNode poi;
		private final OsmCollectionEntity collection;
		private final MapCoordinate coordinate;

		private CollectionMember(GeoNode poi, OsmCollectionEntity collection,
		                         MapCoordinate coordinate) {
			this.poi = poi;
			this.collection = collection;
			this.coordinate = coordinate;
		}

		private void showInfo(AppCompatActivity parent) {
			if (collection == null) {
				showNodeInfoDialog(parent, poi);
			} else if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				showCollectionInfoDialog(parent, collection, coordinate);
			} else {
				showNodeInfoDialog(parent, poi, collection.osmType.name(), false);
			}
		}
	}

	private static View buildCollectionDialog(AppCompatActivity activity, ViewGroup container,
	                                          GeoNode relation,
	                                          List<CollectionMember> members) {
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_collection, container, false);
		LinearLayout elements = result.findViewById(R.id.relationElementsContainer);
		int margin = Globals.convertDpToPixel(4).intValue();
		for (CollectionMember member : members) {
			Drawable icon = new PoiMarkerDrawable(
					activity, new DisplayableGeoNode(member.poi)).getDrawable();
			ImageView element = new ImageView(activity, null, android.R.attr.imageButtonStyle);
			element.setImageDrawable(icon);
			element.setScaleType(ImageView.ScaleType.FIT_CENTER);
			element.setAdjustViewBounds(true);
			element.setClickable(true);
			element.setFocusable(true);
			element.setContentDescription(!member.poi.getName().isEmpty()
					? member.poi.getName() : Long.toString(member.poi.osmID));
			element.setOnClickListener(view -> member.showInfo(activity));
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
					Math.max(icon.getIntrinsicWidth() * 2, 1),
					Math.max(icon.getIntrinsicHeight() * 2, 1));
			params.setMargins(margin, margin, margin, margin);
			elements.addView(element, params);
		}
		setContactData(activity, result, relation);
		DialogueUtils.setLocation(activity, result, relation);
		return result;
	}

	private static List<CollectionMember> loadCollectionMembers(AppCompatActivity activity,
	                                                            OsmCollectionEntity collection)
			throws JSONException {
		JSONArray memberData = collection.jsonNodeInfo.optJSONArray(ClimbingTags.KEY_MEMBERS);
		if (memberData == null) {
			return Collections.emptyList();
		}

		List<Long> nodeIds = new ArrayList<>();
		List<Long> collectionIds = new ArrayList<>();
		for (int index = 0; index < memberData.length(); index++) {
			JSONObject member = memberData.optJSONObject(index);
			if (member == null) {
				continue;
			}
			long id = member.optLong(ClimbingTags.KEY_REF);
			if ("node".equals(member.optString(ClimbingTags.KEY_TYPE))) {
				nodeIds.add(id);
			} else {
				collectionIds.add(id);
			}
		}

		DataManagerNew dataManager = new DataManagerNew();
		Map<Long, OsmNode> nodes = nodeIds.isEmpty()
				? Collections.emptyMap() : dataManager.loadNodeData(activity, nodeIds);
		Map<Long, OsmCollectionEntity> collections = collectionIds.isEmpty()
				? Collections.emptyMap() : dataManager.loadCollectionData(activity, collectionIds);
		List<CollectionMember> result = new ArrayList<>();
		for (int index = 0; index < memberData.length(); index++) {
			JSONObject member = memberData.optJSONObject(index);
			if (member == null) {
				continue;
			}
			long id = member.optLong(ClimbingTags.KEY_REF);
			if ("node".equals(member.optString(ClimbingTags.KEY_TYPE))) {
				OsmNode node = nodes.get(id);
				if (node != null) {
					GeoNode poi = toGeoNode(node);
					result.add(new CollectionMember(poi, null,
							new MapCoordinate(node.decimalLatitude, node.decimalLongitude,
									node.elevationMeters)));
				}
			} else {
				OsmCollectionEntity child = collections.get(id);
				if (child != null) {
					MapCoordinate coordinate = collectionCenter(child);
					result.add(new CollectionMember(toGeoNode(child, coordinate), child, coordinate));
				}
			}
		}
		return result;
	}

	private static GeoNode toGeoNode(OsmNode node) throws JSONException {
		JSONObject tags = new JSONObject(node.getTags().toString());
		GeoNode result = new GeoNode(new JSONObject(node.jsonNodeInfo.toString()));
		result.setTags(tags);
		result.countryIso = node.countryIso;
		return result;
	}

	private static GeoNode toGeoNode(OsmCollectionEntity collection, MapCoordinate coordinate)
			throws JSONException {
		JSONObject tags = new JSONObject(collection.getTags().toString());
		GeoNode result = new GeoNode(new JSONObject(collection.jsonNodeInfo.toString()));
		result.setTags(tags);
		result.updatePOILocation(coordinate.getLatitude(), coordinate.getLongitude(),
				coordinate.getAltitudeMeters());
		return result;
	}

	private static MapCoordinate collectionCenter(OsmCollectionEntity collection) {
		double longitude;
		if (collection.bBoxWest <= collection.bBoxEast) {
			longitude = (collection.bBoxWest + collection.bBoxEast) / 2;
		} else {
			longitude = (collection.bBoxWest + collection.bBoxEast + 360) / 2;
			if (longitude > 180) {
				longitude -= 360;
			}
		}
		return new MapCoordinate((collection.bBoxNorth + collection.bBoxSouth) / 2, longitude);
	}


	private static void setContactData(AppCompatActivity activity, View result, GeoNode poi) {
		StringBuilder website = new StringBuilder();
		try {
			URL url = new URL(poi.getWebsite());
			website.append("<a href=").append(url).append(">").append(url).append("</a>");
		} catch (MalformedURLException ignored) {
			website.append(poi.getWebsite());
		}
		((TextView) result.findViewById(R.id.editWebsite)).setText(Html.fromHtml(website.toString()));
		((TextView) result.findViewById(R.id.editWebsite)).setMovementMethod(LinkMovementMethod.getInstance()); //activate links

		((TextView) result.findViewById(R.id.editPhone)).setText(poi.getPhone());
		((TextView) result.findViewById(R.id.editNo)).setText(poi.getKey(ClimbingTags.KEY_ADDR_STREETNO));
		((TextView) result.findViewById(R.id.editStreet)).setText(poi.getKey(ClimbingTags.KEY_ADDR_STREET));
		((TextView) result.findViewById(R.id.editUnit)).setText(poi.getKey(ClimbingTags.KEY_ADDR_UNIT));
		((TextView) result.findViewById(R.id.editCity)).setText(poi.getKey(ClimbingTags.KEY_ADDR_CITY));
		((TextView) result.findViewById(R.id.editProvince)).setText(poi.getKey(ClimbingTags.KEY_ADDR_PROVINCE));
		((TextView) result.findViewById(R.id.editPostcode)).setText(poi.getKey(ClimbingTags.KEY_ADDR_POSTCODE));
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

		((TextView) result.findViewById(R.id.editLength)).setText(Globals.getDistanceString(poi.getKey(ClimbingTags.KEY_LENGTH)));
		((TextView) result.findViewById(R.id.editPitches)).setText(poi.getKey(ClimbingTags.KEY_PITCHES));
		((TextView) result.findViewById(R.id.editBolts)).setText(poi.getKey(ClimbingTags.KEY_BOLTS));

		((TextView) result.findViewById(R.id.gradingTitle)).setText(activity.getResources().getString(R.string.grade_system,
				activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.gradeTextView)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG)));

		result.findViewById(R.id.gradeTextView).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG)).getDefaultColor());

		setClimbingStyle(activity, result, poi);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(ClimbingTags.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		DialogueUtils.setLocation(activity, result, poi);

		return result;
	}

	private static View buildArtificialDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_artificial, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(ClimbingTags.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		DialogueUtils.setLocation(activity, result, poi);

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
		((TextView) result.findViewById(R.id.editNumRoutes)).setText(poi.getKey(ClimbingTags.KEY_ROUTES));
		((TextView) result.findViewById(R.id.editMinLength)).setText(poi.getKey(ClimbingTags.KEY_MIN_LENGTH));
		((TextView) result.findViewById(R.id.editMaxLength)).setText(poi.getKey(ClimbingTags.KEY_MAX_LENGTH));

		((TextView) result.findViewById(R.id.minGrading)).setText(
				activity.getResources().getString(R.string.min_grade,
						activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.minGradeValueText)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MIN)));

		result.findViewById(R.id.minGradeValueText).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MIN)).getDefaultColor());

		((TextView) result.findViewById(R.id.maxGrading)).setText(
				activity.getResources().getString(R.string.max_grade,
						activity.getResources().getString(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.maxGradeValueText)).setText(GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MAX)));

		result.findViewById(R.id.maxGradeValueText).setBackgroundColor(Globals.gradeToColorState(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MAX)).getDefaultColor());

		setClimbingStyle(activity, result, poi);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(ClimbingTags.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		DialogueUtils.setLocation(activity, result, poi);

		return result;
	}

	private static View buildUnknownDialog(AppCompatActivity activity, ViewGroup container, GeoNode poi) {
		View result = activity.getLayoutInflater().inflate(R.layout.fragment_dialog_unknown, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(poi.getKey(ClimbingTags.KEY_DESCRIPTION));
		DialogueUtils.setLocation(activity, result, poi);

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

	public static void showCollectionInfoDialog(AppCompatActivity parent,
	                                            OsmCollectionEntity collection,
	                                            MapCoordinate labelCoordinate) {
		showCollectionInfoDialog(parent, collection, labelCoordinate, -1);
	}

	public static void showCollectionInfoDialog(AppCompatActivity parent,
	                                            OsmCollectionEntity collection,
	                                            MapCoordinate labelCoordinate,
	                                            int routeCount) {
		DialogBuilder.showLoadingDialogue(parent,
				parent.getResources().getString(R.string.loading_message), null);
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(parent, true);
		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Void>() {
			private String errorMessage;

			@Override
			protected Void doWork() {
				try {
					GeoNode relation = toGeoNode(collection, labelCoordinate);
					if (routeCount >= 0) {
						relation.setKey(ClimbingTags.KEY_ROUTES, Integer.toString(routeCount));
					}
					List<CollectionMember> members = loadCollectionMembers(parent, collection);
					View dialogueView = buildCollectionDialog(
							parent, alertDialog.getListView(), relation, members);
					Drawable relationIcon = new PoiMarkerDrawable(
							parent, new DisplayableGeoNode(relation)).getDrawable();
					DialogueUtils.buildTitle(parent, dialogueView, relation.osmID,
							!relation.getName().isEmpty() ? relation.getName() : " ",
							relationIcon, relation, collection.osmType.name(), false);
					alertDialog.setCancelable(true);
					alertDialog.setCanceledOnTouchOutside(true);
					alertDialog.setView(dialogueView);
				} catch (JSONException exception) {
					errorMessage = parent.getString(
							R.string.exception_message, exception.getMessage());
				}
				return null;
			}

			@Override
			protected void thenDoUiRelatedWork(Void flag) {
				DialogBuilder.dismissLoadingDialogue();
				if (errorMessage != null) {
					DialogBuilder.showErrorDialog(parent, errorMessage, null);
					return;
				}
				alertDialog.create();
				alertDialog.show();
			}
		});
	}

	public static void showNodeInfoDialog(final AppCompatActivity parent, final GeoNode poi) {
		showNodeInfoDialog(parent, poi, "node", true);
	}

	private static void showNodeInfoDialog(final AppCompatActivity parent, final GeoNode poi,
	                                       final String osmEntityType, final boolean editable) {
		DialogBuilder.showLoadingDialogue(parent, parent.getResources().getString(R.string.loading_message), null);
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(parent, true);

		Constants.ASYNC_TASK_EXECUTOR.execute(new UiRelatedTask<Void>() {
			@Override
			protected Void doWork() {
				alertDialog.setCancelable(true);
				alertDialog.setCanceledOnTouchOutside(true);
				View dialogueView;

				switch (poi.getNodeType()) {
					case route:
						dialogueView = buildRouteDialog(parent, alertDialog.getListView(), poi);
						break;
					case crag:
						dialogueView = buildCragDialog(parent, alertDialog.getListView(), poi);
						break;
					case artificial:
						dialogueView = buildArtificialDialog(parent, alertDialog.getListView(), poi);
						break;
					case unknown:
					default:
						dialogueView = buildUnknownDialog(parent, alertDialog.getListView(), poi);
						break;
				}

				Drawable nodeIcon = (new PoiMarkerDrawable(parent, new DisplayableGeoNode(poi))).getDrawable();
				DialogueUtils.buildTitle(parent, dialogueView, poi.osmID,
						!poi.getName().isEmpty() ? poi.getName() : " ", nodeIcon, poi,
						osmEntityType, editable);

				alertDialog.setView(dialogueView);
				return null;
			}

			@Override
			protected void thenDoUiRelatedWork(Void flag) {
				alertDialog.create();
				alertDialog.show();
				DialogBuilder.dismissLoadingDialogue();
			}
		});
	}
}