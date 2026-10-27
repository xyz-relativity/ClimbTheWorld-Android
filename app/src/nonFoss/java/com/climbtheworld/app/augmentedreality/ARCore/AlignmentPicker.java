package com.climbtheworld.app.augmentedreality.ARCore;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.placement.AlignmentChoices;
import com.climbtheworld.app.augmentedreality.placement.AlignmentOrder;
import com.climbtheworld.app.augmentedreality.placement.AlignmentSuggestions;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.PoiMarkerDrawable;
import com.climbtheworld.app.map.widget.climbing.ClimbingParent;
import com.climbtheworld.app.map.widget.climbing.ClimbingRouteCounter;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.storage.edit.RelationMembers;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.utils.views.MemberIconCard;
import com.climbtheworld.app.utils.views.TextWatchers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import needle.UiRelatedTask;

/**
 * Asks which mapped route to align on, from the routes around the user, side by side as their
 * crags list them, see AlignmentOrder, which is how they stand along the wall, each crag's
 * outlined in the crag pins' colour under its name, after its pin as the info dialogs show it.
 * Each route is on its card as the crag dialog shows its routes, see MemberIconCard, its name
 * after its number in its crag, as the map's pins show it, see ClimbingRouteCounter.numberRoutes,
 * and its distance from the user under it, with a quick filter on their names over them, see
 * AlignmentChoices. The list opens with the closest route in its middle, its neighbours along
 * the wall around it, and so it does again for what the user types in the filter. The routes
 * already aligned on are listed wherever they are, outlined, each with a button to delete its
 * alignment, and one under the strip deletes them all, the dialog staying open.
 */
class AlignmentPicker {
	// How far around where the user seems to be routes are looked for, as GPS can be that far
	// off.
	private static final double SEARCH_METERS = 150;
	// How many routes are listed, around the closest in order; the filter looks through all of
	// them.
	private static final int MAX_ROUTES = 40;

	private final AppCompatActivity activity;
	private AlertDialog dialog;

	AlignmentPicker(AppCompatActivity activity) {
		this.activity = activity;
	}

	/**
	 * The routes around, as loadRoutes finds them.
	 */
	private static final class Around {
		// In the order to list them, see AlignmentOrder.
		final List<GeoNode> routes = new ArrayList<>();
		// The crag each route is listed with, by OSM ID, see AlignmentOrder.Group.crag.
		final Map<Long, Integer> cragOf = new HashMap<>();
		// The names of the crags around, by their index.
		final List<String> cragNames = new ArrayList<>();
		// The crags' pins, as the info dialogs show them by their names, by their index.
		final List<Drawable> cragIcons = new ArrayList<>();
		// Each route's number in its crag, by OSM ID.
		final Map<Long, Integer> routeNumbers = new HashMap<>();
	}

	/**
	 * Where a node is from a point, in meters east and north of it.
	 */
	private static double[] eastNorthMeters(GeoNode node, double latitude, double longitude) {
		return AlignmentSuggestions.eastNorthMeters(node.decimalLatitude, node.decimalLongitude,
				latitude, longitude);
	}

	private static double distanceMeters(GeoNode node, double latitude, double longitude) {
		double[] eastNorth = eastNorthMeters(node, latitude, longitude);
		return Math.hypot(eastNorth[0], eastNorth[1]);
	}

	/**
	 * The routes around a point, by crag, see AlignmentOrder: only nodes, which are at one spot,
	 * and with a pin of their own, as the A.R. view shows them, all of them, for the filter to
	 * look through. The routes aligned on are among them, however far.
	 */
	private Around loadRoutes(double latitude, double longitude, long excludedId,
	                          Set<Long> aligned) {
		DataManager dataManager = new DataManager();
		Map<Long, DisplayableGeoNode> found = new HashMap<>();
		dataManager.loadAround(activity, new Vector4d(latitude, longitude, 0, 0), SEARCH_METERS,
				found);
		List<GeoNode> routes = new ArrayList<>();
		for (DisplayableGeoNode poi : found.values()) {
			GeoNode node = poi.getGeoNode();
			if (node.osmID != excludedId && node.getNodeType() == OsmEntity.EntityClimbingType.route) {
				routes.add(node);
			}
		}

		// The routes aligned on farther away, for their alignments to be deleted too.
		Set<Long> missing = new HashSet<>(aligned);
		for (GeoNode route : routes) {
			missing.remove(route.osmID);
		}
		if (!missing.isEmpty()) {
			for (OsmNode node : dataManager.loadNodeData(activity, new ArrayList<>(missing))
					.values()) {
				routes.add(DataManager.toDisplayableNode(node).getGeoNode());
			}
		}
		routes.sort(Comparator.comparingDouble(node -> distanceMeters(node, latitude, longitude)));
		Around around = new Around();
		for (AlignmentOrder.Group<GeoNode> group : AlignmentOrder.grouped(routes,
				loadCragOrders(dataManager, latitude, longitude, around), node -> node.osmID)) {
			for (GeoNode route : group.routes) {
				around.routes.add(route);
				around.cragOf.put(route.osmID, group.crag);
			}
		}
		return around;
	}

	/**
	 * Per crag relation around a point, the OSM IDs of its member nodes in the relation's order.
	 * Any crag with a route around reaches the point, as its bounding box holds the route.
	 *
	 * @param around filled with the crags' names and pins, and the routes' numbers in these
	 *               crags, the first crag's for a route of several
	 */
	private List<List<Long>> loadCragOrders(DataManager dataManager, double latitude,
	                                        double longitude, Around around) {
		ClimbingRouteCounter routeCounter = ClimbingRouteCounter.forDatabase(activity);
		List<List<Long>> crags = new ArrayList<>();
		for (OsmCollectionEntity relation : dataManager.loadRelationsBBox(activity,
				DataManager.computeBoundingBox(new Vector4d(latitude, longitude, 0, 0),
						SEARCH_METERS))) {
			if (relation.entityClimbingType == OsmEntity.EntityClimbingType.crag) {
				crags.add(RelationMembers.nodeRefs(RelationMembers.list(relation.getMembers())));
				around.cragNames.add(ClimbingParent.nameOf(activity, relation));
				// With the route count, as the map shows it.
				around.cragIcons.add(new PoiMarkerDrawable(activity,
						DataManager.toDisplayableNode(relation, routeCounter)).getDrawable());
				routeCounter.numberRoutes(relation).forEach(around.routeNumbers::putIfAbsent);
			}
		}
		return crags;
	}

	/**
	 * @param latitude   where the user seems to be
	 * @param longitude  where the user seems to be
	 * @param excludedId the node being edited, which is not to align on
	 * @param aligned    the OSM IDs of the routes aligned on
	 */
	void show(double latitude, double longitude, long excludedId, Set<Long> aligned,
	          Listener listener) {
		// Kept up to date as the user deletes alignments in the dialog.
		Set<Long> alignedLeft = new HashSet<>(aligned);
		Constants.DB_EXECUTOR.execute(new UiRelatedTask<Around>() {
			@Override
			protected Around doWork() {
				return loadRoutes(latitude, longitude, excludedId, alignedLeft);
			}

			@Override
			protected void thenDoUiRelatedWork(Around around) {
				if (activity.isFinishing() || activity.isDestroyed()) {
					return;
				}
				showDialog(around, latitude, longitude, alignedLeft, listener);
			}
		});
	}

	void dismiss() {
		if (dialog != null) {
			dialog.dismiss();
		}
	}

	/**
	 * @param aligned the OSM IDs of the routes aligned on, which deleting an alignment updates
	 */
	private void showDialog(Around around, double latitude, double longitude, Set<Long> aligned,
	                        Listener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.ar_placement_align_title)
				// The buttons' slots for their places: help on the left, set once shown, see
				// openHelpFromButton, and close on the right.
				.setNeutralButton(R.string.ar_align_help_title, null)
				.setPositiveButton(R.string.close,
						(closed, which) -> listener.onCancelled())
				.setOnCancelListener(cancelled -> listener.onCancelled());
		View content = activity.getLayoutInflater()
				.inflate(R.layout.fragment_dialog_align_route, null);
		builder.setView(content);
		dialog = builder.create();
		// In the dialog rather than among its buttons, which would close it.
		View clear = content.findViewById(R.id.buttonClearAlignments);
		if (around.routes.isEmpty()) {
			content.findViewById(R.id.filterContainer).setVisibility(View.GONE);
			content.findViewById(R.id.scrollAlignRoutes).setVisibility(View.GONE);
			TextView none = content.findViewById(R.id.textNoMatchingRoutes);
			none.setText(R.string.ar_placement_align_none);
			none.setVisibility(View.VISIBLE);
			clear.setVisibility(aligned.isEmpty() ? View.GONE : View.VISIBLE);
			clear.setOnClickListener(clicked -> {
				aligned.clear();
				listener.onCleared();
				clear.setVisibility(View.GONE);
			});
			dialog.show();
			openHelpFromButton();
			return;
		}

		EditText filter = content.findViewById(R.id.editFind);
		RouteStrip strip = new RouteStrip(content, around, latitude, longitude, aligned, filter,
				listener);
		clear.setOnClickListener(clicked -> {
			aligned.clear();
			listener.onCleared();
			// For the delete buttons, and this one, to go.
			strip.refresh();
		});
		filter.addTextChangedListener(TextWatchers.afterTextChanged(typed -> {
			strip.refresh();
			strip.centreOnClosest();
		}));
		strip.refresh();
		// The filter field takes the focus, but most of the time the route is the closest or one
		// of its neighbours, in the strip's middle.
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		dialog.show();
		openHelpFromButton();
		strip.centreOnClosest();
	}

	/**
	 * Has the help button show how to align, see AlignmentHelp, over the dialog, which stays
	 * open under it for the user to pick a route once they have read it.
	 */
	private void openHelpFromButton() {
		dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
				.setOnClickListener(clicked -> AlignmentHelp.show(activity));
	}

	/**
	 * The strip of routes: the routes the filter lets through, see AlignmentChoices, under their
	 * crag's name, see AlignmentOrder.regrouped.
	 */
	private class RouteStrip {
		private final HorizontalScrollView scroll;
		private final LinearLayout groups;
		private final View noMatch;
		// Shown while any route is aligned on.
		private final View clear;
		private final Around around;
		private final double latitude;
		private final double longitude;
		// Where the user is, for the distances on the cards.
		private final GeoNode user;
		// The OSM IDs of the routes aligned on, which deleting an alignment updates.
		private final Set<Long> aligned;
		// Listed wherever they are as the list opens, even once their alignment is deleted.
		private final Set<Long> alignedAtOpen;
		private final EditText filter;
		private final Listener listener;
		// Each route's card, by OSM ID, made once, as the filter shows them again.
		private final Map<Long, View> cards = new HashMap<>();
		// The routes on the strip now, in its order.
		private final List<GeoNode> shown = new ArrayList<>();
		// Waiting for the strip's next layout to centre it, see centreOnClosest; null for none.
		private ViewTreeObserver.OnGlobalLayoutListener pendingCentre;

		RouteStrip(View content, Around around, double latitude, double longitude,
		           Set<Long> aligned, EditText filter, Listener listener) {
			this.scroll = content.findViewById(R.id.scrollAlignRoutes);
			this.groups = content.findViewById(R.id.listAlignRoutes);
			this.noMatch = content.findViewById(R.id.textNoMatchingRoutes);
			this.clear = content.findViewById(R.id.buttonClearAlignments);
			this.around = around;
			this.latitude = latitude;
			this.longitude = longitude;
			this.user = new GeoNode(latitude, longitude, 0);
			this.aligned = aligned;
			this.alignedAtOpen = new HashSet<>(aligned);
			this.filter = filter;
			this.listener = listener;
		}

		/**
		 * Lays out the strip again, for what the filter holds and the routes aligned on now.
		 */
		void refresh() {
			String text = filter.getText().toString();
			List<GeoNode> listed = AlignmentChoices.listed(around.routes, text, GeoNode::getName,
					route -> alignedAtOpen.contains(route.osmID), this::distance, MAX_ROUTES);

			groups.removeAllViews();
			shown.clear();
			for (AlignmentOrder.Group<GeoNode> group : AlignmentOrder.regrouped(listed,
					route -> around.cragOf.getOrDefault(route.osmID, AlignmentOrder.NO_CRAG))) {
				addGroup(group.crag, group.routes);
			}
			noMatch.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
			clear.setVisibility(aligned.isEmpty() ? View.GONE : View.VISIBLE);
		}

		/**
		 * Scrolls the strip for the closest route on it to be in its middle, or as near as the
		 * strip's ends let it, once the strip is laid out with it: a post would come before the
		 * layout that places the cards a refresh just put on the strip.
		 */
		void centreOnClosest() {
			ViewTreeObserver observer = scroll.getViewTreeObserver();
			if (pendingCentre != null) {
				observer.removeOnGlobalLayoutListener(pendingCentre);
				pendingCentre = null;
			}
			int closest = AlignmentChoices.closest(shown, this::distance);
			if (closest < 0) {
				return;
			}
			View card = cards.get(shown.get(closest).osmID);
			pendingCentre = new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (scroll.getWidth() <= 0 || card.getWidth() <= 0) {
						return;
					}
					scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					pendingCentre = null;
					int left = 0;
					for (View view = card; view != groups; view = (View) view.getParent()) {
						if (!(view.getParent() instanceof View)) {
							// Taken off the strip by a refresh since.
							return;
						}
						left += view.getLeft();
					}
					// Clamped to the strip's ends.
					scroll.scrollTo(left + (card.getWidth() - scroll.getWidth()) / 2, 0);
				}
			};
			observer.addOnGlobalLayoutListener(pendingCentre);
		}

		/**
		 * @param crag the routes' crag, see AlignmentOrder.Group.crag: its name, after its pin,
		 *             over them, outlined in the crag pins' colour; for the routes of no crag,
		 *             only a title
		 */
		private void addGroup(int crag, List<GeoNode> routes) {
			View group = activity.getLayoutInflater()
					.inflate(R.layout.list_item_alignment_group, groups, false);
			TextView title = group.findViewById(R.id.alignGroupTitle);
			if (crag == AlignmentOrder.NO_CRAG) {
				title.setText(R.string.ar_placement_align_no_crag);
			} else {
				title.setText(around.cragNames.get(crag));
				ImageView icon = group.findViewById(R.id.alignGroupIcon);
				icon.setImageDrawable(around.cragIcons.get(crag));
				icon.setVisibility(View.VISIBLE);
				int color = DisplayableGeoNode.getHullPinColor(OsmEntity.EntityClimbingType.crag);
				group.setBackgroundResource(R.drawable.bg_align_crag_group);
				group.setBackgroundTintList(ColorStateList.valueOf(color));
				group.findViewById(R.id.alignGroupDivider).setBackgroundColor(color);
			}
			ViewGroup routeCards = group.findViewById(R.id.alignGroupRoutes);
			for (GeoNode route : routes) {
				routeCards.addView(card(route, routeCards));
				shown.add(route);
			}
			groups.addView(group);
		}

		/**
		 * @param container the group the card goes in, which it is taken off its last one for
		 */
		private View card(GeoNode route, ViewGroup container) {
			View view = cards.get(route.osmID);
			if (view == null) {
				view = activity.getLayoutInflater()
						.inflate(R.layout.list_item_alignment_route, container, false);
				View card = view.findViewById(R.id.alignRouteCard);
				// The number by the name rather than on the pin, as the relation editor's
				// members show it.
				MemberIconCard.show(activity, card, new DisplayableGeoNode(route),
						route.getName(), around.routeNumbers.getOrDefault(route.osmID, 0));
				// As the lists of climbing POIs show it, see DialogueUtils.setDistance.
				((TextView) view.findViewById(R.id.alignRouteDistance)).setText(
						Globals.getDistanceString(GeoUtils.calculateDistance(user, route)));
				card.setOnClickListener(clicked -> {
					dialog.dismiss();
					listener.onPicked(route);
				});
				view.findViewById(R.id.buttonDeleteAlignment).setOnClickListener(clicked -> {
					aligned.remove(route.osmID);
					listener.onDeleted(route);
					// For its delete button to go, and the clear button with the last one.
					refresh();
				});
				cards.put(route.osmID, view);
			} else if (view.getParent() != null) {
				((ViewGroup) view.getParent()).removeView(view);
			}
			boolean isAligned = aligned.contains(route.osmID);
			view.findViewById(R.id.buttonDeleteAlignment)
					.setVisibility(isAligned ? View.VISIBLE : View.GONE);
			view.setBackgroundResource(isAligned ? R.drawable.bg_align_route_aligned : 0);
			return view;
		}

		private double distance(GeoNode route) {
			return distanceMeters(route, latitude, longitude);
		}
	}

	interface Listener {
		void onPicked(GeoNode route);

		/**
		 * The user deleted the alignment on a route; the dialog stays open.
		 */
		void onDeleted(GeoNode route);

		/**
		 * The user deleted all the alignments; the dialog stays open.
		 */
		void onCleared();

		/**
		 * The user closed the list without picking a route.
		 */
		void onCancelled();
	}
}
