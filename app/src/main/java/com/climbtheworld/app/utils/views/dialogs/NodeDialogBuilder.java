package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
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
import com.climbtheworld.app.map.marker.MarkerUtils;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.GradeViewUtils;
import com.climbtheworld.app.utils.views.ListViewItemBuilder;
import com.climbtheworld.app.utils.views.Sorters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

import needle.UiRelatedTask;

public class NodeDialogBuilder {
	private static final int INFO_DIALOG_STYLE_ICON_SIZE = Globals.convertDpToPixel(10).intValue();
	private static final int MEMBER_CARD_STYLE_ICON_SIZE = Globals.convertDpToPixel(16).intValue();
	private static final int MEMBER_ICON_NAME_MIN_WIDTH = Globals.convertDpToPixel(80).intValue();

	private NodeDialogBuilder() {
		//hide constructor
	}

	private static View buildCollectionDialog(AppCompatActivity activity, ViewGroup container,
	                                          CollectionMember self,
	                                          List<CollectionMember> members) {
		GeoNode relation = self.poi;
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_collection, container, false);
		LinearLayout elements = result.findViewById(R.id.relationElementsContainer);
		GradeSystem gradeSystem = GradeSystem.fromString(
				Configs.instance(activity).getString(Configs.ConfigKey.usedGradeSystem));
		if (relation.getNodeType() == GeoNode.NodeTypes.area) {
			int padding = Globals.convertDpToPixel(4).intValue();
			elements.setGravity(Gravity.TOP);
			elements.setPadding(padding, padding, padding, padding);
			// The cards share one scale, so their grade bars compare the members.
			int cardGradeScale = 0;
			for (CollectionMember member : members) {
				cardGradeScale = Math.max(cardGradeScale, largestGradeRowCount(gradeSystem,
						member.getStyleCounts().keySet(), member.routes));
			}
			for (CollectionMember member : members) {
				elements.addView(buildMemberCard(activity, elements, member, gradeSystem,
						cardGradeScale));
			}
		} else {
			// Routes are numbered by their order among the relation's route members.
			int routeNumber = 0;
			for (CollectionMember member : members) {
				boolean isRoute = member.poi.getNodeType() == GeoNode.NodeTypes.route;
				elements.addView(
						buildMemberIcon(activity, elements, member, isRoute ? ++routeNumber : 0));
			}
		}
		GeoNode.NodeTypes nodeType = relation.getNodeType();
		if (nodeType == GeoNode.NodeTypes.area) {
			((TextView) result.findViewById(R.id.textElementsTitle)).setText(R.string.crags);
		} else if (nodeType == GeoNode.NodeTypes.crag) {
			((TextView) result.findViewById(R.id.textElementsTitle)).setText(R.string.routes);
		}
		if (nodeType == GeoNode.NodeTypes.area || nodeType == GeoNode.NodeTypes.crag) {
			setCragDetails(activity, result, ClimbingInfo.fromRoutes(self.routes),
					relation.getKey(ClimbingTags.KEY_DESCRIPTION));
			Map<GeoNode.ClimbingStyle, Integer> styleCounts = self.routes.getStyleCounts();
			addStyleRows(activity, result.findViewById(R.id.containerClimbingStylesView),
					gradeSystem, styleCounts, self.routes,
					largestGradeRowCount(gradeSystem, styleCounts.keySet(), self.routes));
		} else {
			result.findViewById(R.id.climbingInfoContainer).setVisibility(View.GONE);
		}
		DialogueUtils.setLocation(activity, result, relation);
		return result;
	}

	/**
	 * Member icon with its name underneath, routes prefixed by their order number.
	 */
	private static View buildMemberIcon(AppCompatActivity activity, ViewGroup container,
	                                    CollectionMember member, int routeNumber) {
		View card = activity.getLayoutInflater()
				.inflate(R.layout.list_item_climbing_member_icon, container, false);
		Drawable icon = new PoiMarkerDrawable(
				activity, new DisplayableGeoNode(member.poi)).getDrawable();
		int iconWidth = Math.max(icon.getIntrinsicWidth() * 2, 1);
		ImageView image = card.findViewById(R.id.memberIconImage);
		image.setImageDrawable(icon);
		image.getLayoutParams().width = iconWidth;
		image.getLayoutParams().height = Math.max(icon.getIntrinsicHeight() * 2, 1);

		String name = !member.poi.getName().isEmpty()
				? member.poi.getName() : Long.toString(member.poi.osmID);
		TextView nameView = card.findViewById(R.id.memberIconName);
		nameView.setText(name);
		card.findViewById(R.id.memberIconLabel).getLayoutParams().width =
				Math.max(iconWidth, MEMBER_ICON_NAME_MIN_WIDTH);
		if (routeNumber > 0) {
			TextView numberView = card.findViewById(R.id.memberIconNumber);
			numberView.setText(routeNumber + ".");
			numberView.setVisibility(View.VISIBLE);
			nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
			name = routeNumber + ". " + name;
		}

		card.setContentDescription(name);
		card.setOnClickListener(view -> member.showInfo(activity));
		return card;
	}

	/**
	 * @param gradeScale route count of a full length grade bar
	 */
	private static View buildMemberCard(AppCompatActivity activity, ViewGroup container,
	                                    CollectionMember member, GradeSystem gradeSystem,
	                                    int gradeScale) {
		View card = activity.getLayoutInflater()
				.inflate(R.layout.list_item_climbing_member_card, container, false);
		String name = !member.poi.getName().isEmpty()
				? member.poi.getName() : Long.toString(member.poi.osmID);

		((ImageView) card.findViewById(R.id.memberCardIcon)).setImageDrawable(
				new PoiMarkerDrawable(activity, new DisplayableGeoNode(member.poi)).getDrawable());
		((TextView) card.findViewById(R.id.memberCardName)).setText(name);

		LinearLayout styles = card.findViewById(R.id.memberCardStyles);
		if (!addStyleRows(activity, styles, gradeSystem, member.getStyleCounts(), member.routes,
				gradeScale)) {
			card.findViewById(R.id.memberCardDivider).setVisibility(View.GONE);
			styles.setVisibility(View.GONE);
		}

		card.setContentDescription(name);
		card.setOnClickListener(view -> member.showInfo(activity));
		return card;
	}

	/**
	 * One row per climbing style with its route count, each followed by its grade rows.
	 *
	 * @param gradeScale route count of a full length grade bar
	 * @return false when there are no counted styles and nothing was added.
	 */
	private static boolean addStyleRows(AppCompatActivity activity, LinearLayout container,
	                                    GradeSystem gradeSystem,
	                                    Map<GeoNode.ClimbingStyle, Integer> styleCounts,
	                                    ClimbingRouteCounter.RouteSummary routes,
	                                    int gradeScale) {
		for (GeoNode.ClimbingStyle style : Sorters.sortStyles(activity,
				new ArrayList<>(styleCounts.keySet()))) {
			View row = activity.getLayoutInflater()
					.inflate(R.layout.list_item_climbing_member_style, container, false);
			((ImageView) row.findViewById(R.id.memberStyleIcon)).setImageDrawable(
					MarkerUtils.getStyleIcon(activity, Collections.singletonList(style),
							MEMBER_CARD_STYLE_ICON_SIZE));
			((TextView) row.findViewById(R.id.memberStyleName)).setText(style.getNameId());
			((TextView) row.findViewById(R.id.memberStyleCount)).setText(
					String.valueOf(styleCounts.get(style)));
			container.addView(row);
			for (GradeRow gradeRow : getGradeRows(gradeSystem, routes.getGradeCounts(style))) {
				container.addView(buildGradeRow(activity, container, gradeRow, gradeScale));
			}
		}
		return !styleCounts.isEmpty();
	}

	/**
	 * One row per grade, easiest first. Adjacent grade indexes that show the same text in the
	 * grade system are merged into one row.
	 */
	private static List<GradeRow> getGradeRows(GradeSystem gradeSystem,
	                                           SortedMap<Integer, Integer> gradeCounts) {
		List<GradeRow> result = new ArrayList<>();
		String groupName = null;
		int groupGrade = ClimbingRouteCounter.UNKNOWN_GRADE;
		int groupMaxGrade = ClimbingRouteCounter.UNKNOWN_GRADE;
		int groupCount = 0;
		for (Map.Entry<Integer, Integer> entry : gradeCounts.entrySet()) {
			String name = gradeSystem.getGrade(entry.getKey());
			if (name.equals(groupName)) {
				groupMaxGrade = entry.getKey();
				groupCount += entry.getValue();
				continue;
			}
			if (groupName != null) {
				result.add(new GradeRow(groupName, groupGrade, groupMaxGrade, groupCount));
			}
			groupName = name;
			groupGrade = entry.getKey();
			groupMaxGrade = entry.getKey();
			groupCount = entry.getValue();
		}
		if (groupName != null) {
			result.add(new GradeRow(groupName, groupGrade, groupMaxGrade, groupCount));
		}
		return result;
	}

	/**
	 * Colour-coded grade with its route count, and a bar in the grade colour as long as the
	 * count is against the scale, growing from the grade towards the count.
	 *
	 * @param scale route count of a full length bar, at least the row's
	 */
	private static View buildGradeRow(AppCompatActivity activity, ViewGroup container,
	                                  GradeRow gradeRow, int scale) {
		View row = activity.getLayoutInflater()
				.inflate(R.layout.list_item_climbing_member_grade, container, false);
		// A merged row is coloured by the easiest of its grades.
		int color = gradeRow.grade() == ClimbingRouteCounter.UNKNOWN_GRADE
				? Color.LTGRAY : Globals.gradeToColorState(gradeRow.grade()).getDefaultColor();
		TextView gradeView = row.findViewById(R.id.memberGradeName);
		gradeView.setText(gradeRow.name());
		GradeViewUtils.styleGradeLabel(gradeView, color);
		GradeConversionDialogBuilder.makeClickable(activity, gradeView, gradeRow.grade(),
				gradeRow.maxGrade());
		((TextView) row.findViewById(R.id.memberGradeCount)).setText(
				String.valueOf(gradeRow.count()));

		// The bar's weight is its share of the track.
		View bar = row.findViewById(R.id.memberGradeBar);
		((LinearLayout.LayoutParams) bar.getLayoutParams()).weight =
				(float) gradeRow.count() / scale;
		bar.setBackgroundTintList(ColorStateList.valueOf(color));
		return row;
	}

	/**
	 * @return the largest route count among the grade rows of the styles, for the bars compared
	 * with each other to share one scale
	 */
	private static int largestGradeRowCount(GradeSystem gradeSystem,
	                                        Collection<GeoNode.ClimbingStyle> styles,
	                                        ClimbingRouteCounter.RouteSummary routes) {
		int result = 0;
		for (GeoNode.ClimbingStyle style : styles) {
			for (GradeRow gradeRow : getGradeRows(gradeSystem, routes.getGradeCounts(style))) {
				result = Math.max(result, gradeRow.count());
			}
		}
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
		Map<String, OsmCollectionEntity> collections = collectionIds.isEmpty()
				? Collections.emptyMap() : dataManager.loadCollectionData(activity, collectionIds);
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(activity);
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
									node.elevationMeters),
							routeCounter.summarize(node)));
				}
			} else {
				OsmEntity.EntityOsmType memberType;
				try {
					memberType = OsmEntity.EntityOsmType.valueOf(
							member.optString(ClimbingTags.KEY_TYPE));
				} catch (IllegalArgumentException ignored) {
					continue;
				}
				OsmCollectionEntity child = collections.get(
						DataManagerNew.collectionKey(memberType, id));
				if (child != null) {
					MapCoordinate coordinate = collectionCenter(child);
					ClimbingRouteCounter.RouteSummary routes = routeCounter.summarize(child);
					GeoNode poi = toGeoNode(child, coordinate);
					if (routes.getRouteCount() > 0) {
						poi.setKey(ClimbingTags.KEY_ROUTES,
								Integer.toString(routes.getRouteCount()));
					}
					result.add(new CollectionMember(poi, child, coordinate, routes));
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
		((TextView) result.findViewById(R.id.editWebsite)).setText(
				Html.fromHtml(website.toString()));
		((TextView) result.findViewById(R.id.editWebsite)).setMovementMethod(
				LinkMovementMethod.getInstance()); //activate links

		((TextView) result.findViewById(R.id.editPhone)).setText(poi.getPhone());
		((TextView) result.findViewById(R.id.editNo)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_STREETNO));
		((TextView) result.findViewById(R.id.editStreet)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_STREET));
		((TextView) result.findViewById(R.id.editUnit)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_UNIT));
		((TextView) result.findViewById(R.id.editCity)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_CITY));
		((TextView) result.findViewById(R.id.editProvince)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_PROVINCE));
		((TextView) result.findViewById(R.id.editPostcode)).setText(
				poi.getKey(ClimbingTags.KEY_ADDR_POSTCODE));
	}

	private static void setClimbingStyle(AppCompatActivity parent, View result, GeoNode poi) {
		ViewGroup styles = result.findViewById(R.id.containerClimbingStylesView);

		for (GeoNode.ClimbingStyle styleName : Sorters.sortStyles(parent,
				poi.getClimbingStyles())) {
			View customView = ListViewItemBuilder.getNonPaddedBuilder(parent)
					.setDescription(parent.getResources().getString(styleName.getNameId()))
					.setIcon(MarkerUtils.getStyleIcon(parent, Collections.singletonList(styleName),
							INFO_DIALOG_STYLE_ICON_SIZE))
					.build();

			styles.addView(customView);
		}
	}

	private static View buildRouteDialog(AppCompatActivity activity, ViewGroup container,
	                                     GeoNode poi) {
		Configs configs = Configs.instance(activity);
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_route, container, false);

		((TextView) result.findViewById(R.id.editLength)).setText(
				Globals.getDistanceString(poi.getKey(ClimbingTags.KEY_LENGTH)));
		((TextView) result.findViewById(R.id.editPitches)).setText(
				poi.getKey(ClimbingTags.KEY_PITCHES));
		((TextView) result.findViewById(R.id.editBolts)).setText(
				poi.getKey(ClimbingTags.KEY_BOLTS));

		((TextView) result.findViewById(R.id.gradingTitle)).setText(
				activity.getResources().getString(R.string.grade_system,
						activity.getResources().getString(GradeSystem.fromString(
								configs.getString(Configs.ConfigKey.usedGradeSystem)).shortName)));
		((TextView) result.findViewById(R.id.gradeTextView)).setText(
				GradeSystem.fromString(configs.getString(Configs.ConfigKey.usedGradeSystem))
						.getGrade(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG)));

		GradeViewUtils.styleGradeLabel(result.findViewById(R.id.gradeTextView),
				Globals.gradeToColorState(poi.getLevelId(ClimbingTags.KEY_GRADE_TAG))
						.getDefaultColor());
		GradeConversionDialogBuilder.makeClickable(activity,
				result.findViewById(R.id.gradeTextView),
				poi.getLevelId(ClimbingTags.KEY_GRADE_TAG));

		setClimbingStyle(activity, result, poi);

		((TextView) result.findViewById(R.id.editDescription)).setText(
				poi.getKey(ClimbingTags.KEY_DESCRIPTION));

		DialogueUtils.setLocation(activity, result, poi);

		return result;
	}

	private static View buildArtificialDialog(AppCompatActivity activity, ViewGroup container,
	                                          GeoNode poi) {
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_artificial, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(
				poi.getKey(ClimbingTags.KEY_DESCRIPTION));

		setContactData(activity, result, poi);
		DialogueUtils.setLocation(activity, result, poi);

		if (poi.isArtificialTower()) {
			((TextView) result.findViewById(R.id.editCentreType)).setText(
					R.string.artificial_tower);
		} else {
			((TextView) result.findViewById(R.id.editCentreType)).setText(R.string.climbing_gym);
		}

		return result;
	}

	private static View buildCragDialog(AppCompatActivity activity, ViewGroup container,
	                                    GeoNode poi) {
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_crag, container, false);
		setCragDetails(activity, result, ClimbingInfo.fromTags(poi),
				poi.getKey(ClimbingTags.KEY_DESCRIPTION));
		setClimbingStyle(activity, result, poi);
		DialogueUtils.setLocation(activity, result, poi);

		return result;
	}

	private static void setCragDetails(AppCompatActivity activity, View result,
	                                   ClimbingInfo info, String description) {
		GradeSystem gradeSystem = GradeSystem.fromString(
				Configs.instance(activity).getString(Configs.ConfigKey.usedGradeSystem));
		String gradeSystemName = activity.getResources().getString(gradeSystem.shortName);

		((TextView) result.findViewById(R.id.editNumRoutes)).setText(info.routes);
		((TextView) result.findViewById(R.id.editMinLength)).setText(
				Globals.getDistanceString(info.minLength));
		((TextView) result.findViewById(R.id.editMaxLength)).setText(
				Globals.getDistanceString(info.maxLength));

		((TextView) result.findViewById(R.id.minGrading)).setText(
				activity.getResources().getString(R.string.min_grade, gradeSystemName));
		((TextView) result.findViewById(R.id.minGradeValueText)).setText(
				gradeSystem.getGrade(info.minGrade));
		GradeViewUtils.styleGradeLabel(result.findViewById(R.id.minGradeValueText),
				Globals.gradeToColorState(info.minGrade).getDefaultColor());
		GradeConversionDialogBuilder.makeClickable(activity,
				result.findViewById(R.id.minGradeValueText), info.minGrade);

		((TextView) result.findViewById(R.id.maxGrading)).setText(
				activity.getResources().getString(R.string.max_grade, gradeSystemName));
		((TextView) result.findViewById(R.id.maxGradeValueText)).setText(
				gradeSystem.getGrade(info.maxGrade));
		GradeViewUtils.styleGradeLabel(result.findViewById(R.id.maxGradeValueText),
				Globals.gradeToColorState(info.maxGrade).getDefaultColor());
		GradeConversionDialogBuilder.makeClickable(activity,
				result.findViewById(R.id.maxGradeValueText), info.maxGrade);

		((TextView) result.findViewById(R.id.editDescription)).setText(description);
	}

	private static View buildUnknownDialog(AppCompatActivity activity, ViewGroup container,
	                                       GeoNode poi) {
		View result = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_unknown, container, false);

		((TextView) result.findViewById(R.id.editDescription)).setText(
				poi.getKey(ClimbingTags.KEY_DESCRIPTION));
		DialogueUtils.setLocation(activity, result, poi);

		TableLayout table = result.findViewById(R.id.tableAllTags);

		int padding = Globals.convertPixelsToDp(5).intValue();
		TableRow.LayoutParams params =
				new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
						TableRow.LayoutParams.MATCH_PARENT, 1f);

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

	/**
	 * Names what the climbing feature is part of, on the right of the climbing info title: the
	 * smallest named climbing relation listing it, so the next level up, with its icon, which
	 * opens it. At the top, such as for an area, it is the country instead. Runs off the UI
	 * thread.
	 *
	 * @param location a point of the feature
	 */
	private static void setClimbingParent(AppCompatActivity activity, View result,
	                                      OsmEntity.EntityOsmType osmType, long osmId,
	                                      GeoNode location) {
		TextView parentText = result.findViewById(R.id.textClimbingParent);
		ImageView parentIcon = result.findViewById(R.id.imageClimbingParent);
		TextView countryText = result.findViewById(R.id.textClimbingCountry);
		if (parentText == null || parentIcon == null || countryText == null) {
			return;
		}

		DataManagerNew dataManager = new DataManagerNew();
		OsmCollectionEntity parent = null;
		for (OsmCollectionEntity relation : dataManager.loadParentRelations(activity, osmType,
				osmId, location.decimalLatitude, location.decimalLongitude)) {
			if (parent == null || isCloserParent(relation, parent)) {
				parent = relation;
			}
		}

		if (parent != null) {
			String name = parent.getTags().optString(ClimbingTags.KEY_NAME, "").trim();
			if (name.isEmpty()) {
				name = activity.getString(parent.entityClimbingType.getNameId());
			}
			parentText.setText(name);
			parentText.setVisibility(View.VISIBLE);

			GeoNode center = DataManagerNew.toDisplayableNode(parent).getGeoNode();
			MapCoordinate coordinate = new MapCoordinate(center.decimalLatitude,
					center.decimalLongitude, 0);
			try {
				// The same pin as on the map, with the route count.
				GeoNode parentPoi = toGeoNode(parent, coordinate);
				int routeCount = ClimbingRouteCounter.forDatabase(activity).summarize(parent)
						.getRouteCount();
				parentPoi.setKey(ClimbingTags.KEY_ROUTES, Integer.toString(routeCount));
				parentIcon.setImageDrawable(new PoiMarkerDrawable(activity,
						new DisplayableGeoNode(parentPoi)).getDrawable());
			} catch (JSONException e) {
				return;
			}
			parentIcon.setContentDescription(name);
			parentIcon.setVisibility(View.VISIBLE);
			final OsmCollectionEntity shown = parent;
			parentIcon.setOnClickListener(
					view -> showCollectionInfoDialog(activity, shown, coordinate));
			return;
		}

		List<String> countries = new ArrayList<>(dataManager.loadCountries(activity, osmType, osmId));
		if (countries.isEmpty() && location.countryIso != null && !location.countryIso.isEmpty()) {
			countries.add(location.countryIso);
		}
		if (countries.isEmpty()) {
			return;
		}
		// Along a border, the feature can be in more than one.
		StringBuilder names = new StringBuilder();
		for (String countryIso : countries) {
			String name = new Locale("", countryIso).getDisplayCountry();
			if (names.length() > 0) {
				names.append(" / ");
			}
			names.append(!name.isEmpty() ? name : countryIso);
		}
		countryText.setText(names);
		countryText.setVisibility(View.VISIBLE);
	}

	/**
	 * Named relations come first, then the smaller one, as it is the closest level up: the crag
	 * of a route rather than the area holding the crag.
	 */
	private static boolean isCloserParent(OsmCollectionEntity relation,
	                                      OsmCollectionEntity other) {
		boolean named = !relation.getTags().optString(ClimbingTags.KEY_NAME, "").trim().isEmpty();
		boolean otherNamed = !other.getTags().optString(ClimbingTags.KEY_NAME, "").trim().isEmpty();
		if (named != otherNamed) {
			return named;
		}
		return boundingBoxSize(relation) < boundingBoxSize(other);
	}

	private static double boundingBoxSize(OsmCollectionEntity collection) {
		return (collection.bBoxNorth - collection.bBoxSouth)
				* (collection.bBoxEast - collection.bBoxWest);
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
					ClimbingRouteCounter.RouteSummary routes =
							ClimbingRouteCounter.forDatabase(parent).summarize(collection);
					if (routeCount >= 0) {
						relation.setKey(ClimbingTags.KEY_ROUTES, Integer.toString(routeCount));
					}
					List<CollectionMember> members = loadCollectionMembers(parent, collection);
					View dialogueView = buildCollectionDialog(parent, alertDialog.getListView(),
							new CollectionMember(relation, collection, labelCoordinate, routes),
							members);
					setClimbingParent(parent, dialogueView, collection.osmType,
							collection.osmID, relation);
					Drawable relationIcon = new PoiMarkerDrawable(
							parent, new DisplayableGeoNode(relation)).getDrawable();
					DialogueUtils.buildTitle(parent, dialogueView, relation.osmID,
							!relation.getName().isEmpty() ? relation.getName() : " ",
							relationIcon, relation, collection.osmType.name(), false);
					LogBookDialogBuilder.buildSection(parent, dialogueView,
							collection.osmType.name(), relation, relationIcon);
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
		// Ways (e.g. a gym mapped as a building) are shown through a GeoNode too; keep their real
		// OSM type for the links, and don't offer the node editor for them.
		String osmEntityType = poi.jsonNodeInfo.optString(ClimbingTags.KEY_TYPE, "node");
		if (OsmEntity.EntityOsmType.relation.name().equals(osmEntityType)) {
			// Relations (crags/areas, e.g. from search) get the same dialog as their map label.
			showRelationInfoDialog(parent, poi);
			return;
		}
		showNodeInfoDialog(parent, poi, osmEntityType, "node".equals(osmEntityType));
	}

	private static void showRelationInfoDialog(final AppCompatActivity parent, final GeoNode poi) {
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<OsmCollectionEntity>() {
			@Override
			protected OsmCollectionEntity doWork() {
				return new DataManagerNew().loadCollection(parent, poi);
			}

			@Override
			protected void thenDoUiRelatedWork(OsmCollectionEntity collection) {
				if (collection == null) {
					showNodeInfoDialog(parent, poi, OsmEntity.EntityOsmType.relation.name(),
							false);
					return;
				}
				showCollectionInfoDialog(parent, collection, new MapCoordinate(
						poi.decimalLatitude, poi.decimalLongitude, poi.elevationMeters));
			}
		});
	}

	private static void showNodeInfoDialog(final AppCompatActivity parent, final GeoNode poi,
	                                       final String osmEntityType, final boolean editable) {
		DialogBuilder.showLoadingDialogue(parent,
				parent.getResources().getString(R.string.loading_message), null);
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
					case area:
						dialogueView = buildCragDialog(parent, alertDialog.getListView(), poi);
						break;
					case artificial:
						dialogueView =
								buildArtificialDialog(parent, alertDialog.getListView(), poi);
						break;
					case unknown:
					default:
						dialogueView = buildUnknownDialog(parent, alertDialog.getListView(), poi);
						break;
				}
				try {
					setClimbingParent(parent, dialogueView,
							OsmEntity.EntityOsmType.valueOf(osmEntityType), poi.osmID, poi);
				} catch (IllegalArgumentException ignore) {
					// Not an OSM entity type, so not a member of anything.
				}

				Drawable nodeIcon =
						(new PoiMarkerDrawable(parent, new DisplayableGeoNode(poi))).getDrawable();
				DialogueUtils.buildTitle(parent, dialogueView, poi.osmID,
						!poi.getName().isEmpty() ? poi.getName() : " ", nodeIcon, poi,
						osmEntityType, editable);
				LogBookDialogBuilder.buildSection(parent, dialogueView, osmEntityType, poi,
						nodeIcon);

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

	/**
	 * The values shown in the climbing info section: read from the tags of a POI, or
	 * calculated from the routes of a relation.
	 */
	private record ClimbingInfo(String routes, String minLength, String maxLength, int minGrade,
	                            int maxGrade) {

		private static ClimbingInfo fromTags(GeoNode poi) {
			return new ClimbingInfo(poi.getKey(ClimbingTags.KEY_ROUTES),
					poi.getKey(ClimbingTags.KEY_MIN_LENGTH),
					poi.getKey(ClimbingTags.KEY_MAX_LENGTH),
					poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MIN),
					poi.getLevelId(ClimbingTags.KEY_GRADE_TAG_MAX));
		}

		private static ClimbingInfo fromRoutes(ClimbingRouteCounter.RouteSummary routes) {
			return new ClimbingInfo(Integer.toString(routes.getRouteCount()),
					lengthToString(routes.getMinLength()), lengthToString(routes.getMaxLength()),
					routes.getMinGrade(), routes.getMaxGrade());
		}

		private static String lengthToString(double length) {
			return Double.isNaN(length) ? "" : Double.toString(length);
		}
	}

	/**
	 * @param grade    easiest grade index of the row, or the unknown grade
	 * @param maxGrade hardest grade index of the row
	 */
	private record GradeRow(String name, int grade, int maxGrade, int count) {
	}

	private record CollectionMember(GeoNode poi, OsmCollectionEntity collection,
	                                MapCoordinate coordinate,
	                                ClimbingRouteCounter.RouteSummary routes) {

		private void showInfo(AppCompatActivity parent) {
			if (collection == null) {
				showNodeInfoDialog(parent, poi);
			} else if (collection.osmType == OsmEntity.EntityOsmType.relation) {
				showCollectionInfoDialog(parent, collection, coordinate,
						routes.getRouteCount() > 0 ? routes.getRouteCount() : -1);
			} else {
				showNodeInfoDialog(parent, poi, collection.osmType.name(), false);
			}
		}

		/**
		 * Route count per climbing style, falling back to numeric climbing:&lt;style&gt; tags
		 * (for example climbing:sport=12) when the member contains no mapped routes.
		 */
		private Map<GeoNode.ClimbingStyle, Integer> getStyleCounts() {
			if (routes.getRouteCount() > 0) {
				return routes.getStyleCounts();
			}
			Map<GeoNode.ClimbingStyle, Integer> result =
					new EnumMap<>(GeoNode.ClimbingStyle.class);
			for (GeoNode.ClimbingStyle style : poi.getClimbingStyles()) {
				try {
					int count = Integer.parseInt(poi.getKey(
									ClimbingTags.KEY_CLIMBING + ClimbingTags.KEY_SEPARATOR + style.name())
							.trim());
					if (count > 0) {
						result.put(style, count);
					}
				} catch (NumberFormatException ignored) {
					// "yes" and other non-numeric values carry no count.
				}
			}
			return result;
		}
	}
}