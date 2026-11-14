package com.climbtheworld.app.augmentedreality;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.map.marker.NodeDisplayFilters;
import com.climbtheworld.app.map.model.MapBounds;
import com.climbtheworld.app.map.model.MapCoordinate;
import com.climbtheworld.app.map.widget.climbing.ClimbingGeometryBuilder;
import com.climbtheworld.app.map.widget.climbing.RouteNumbers;
import com.climbtheworld.app.map.widget.climbing.RouteRelation;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.storage.CachedDataManager;
import com.climbtheworld.app.storage.DataManager;
import com.climbtheworld.app.storage.TerrainElevation;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmCollectionEntity;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.storage.database.OsmNode;
import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector2d;
import com.climbtheworld.app.utils.Vector4d;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import needle.UiRelatedTask;

/**
 * The climbing data around the observer as the A.R. views draw it: the route, crag and area pins,
 * the lines of the routes mapped as a way, and the crag and area outlines. It loads them, works out
 * which show and where, as the camera it is given sees them, and draws them in its container.
 * <p>
 * The A.R. view gives it the camera of the phone's sensors, and the A.R. placement in the node
 * editor the camera ARCore tracks, so that both draw the routes alike.
 */
public class AugmentedRealityScene {
	// The phone is held above the ground the terrain model gives.
	public static final double EYE_HEIGHT_METERS = 1.5;
	private static final double POI_CACHE_EVICTION_MARGIN = 1.5;
	// Terrain detail only pays off close by, where an elevation error moves things the most.
	private static final double TERRAIN_DETAIL_DISTANCE_METERS = 1000;
	// The POIs are loaded again once the observer moves this far, see ReloadGuard, from
	// POI_LOAD_MARGIN_METERS beyond the view distance around them, so the ones in view are
	// loaded, also while the observer moves on during a load. A load only reads the POIs not
	// held yet, see DataManager.loadDisplayableNodesBBox.
	private static final double POI_RELOAD_METERS = 2;
	private static final double POI_LOAD_MARGIN_METERS = 50;
	// Crags and areas are loaded again once the observer moves this share of the view distance,
	// from POI_CACHE_EVICTION_MARGIN times the view distance around them, so the ones in view are
	// always loaded.
	private static final double HULL_RELOAD_SHARE = 0.25;

	private final AppCompatActivity activity;
	private final VirtualCamera camera;
	private final Configs configs;
	private final Runnable onLoaded;
	private final Map<Long, GeoNode> boundingBoxPOIs = new HashMap<>();
	//POIs around the camera.
	private final List<GeoNode> visible = new ArrayList<>();
	private final List<GeoNode> zOrderedDisplay = new ArrayList<>();
	private final ConcurrentHashMap<Long, DisplayableGeoNode> arPOIs = new ConcurrentHashMap<>();
	private final Map<Long, FootElevation> footElevations = new HashMap<>();
	private final Semaphore updatingView = new Semaphore(1);
	private final DataManager offlineDataManager = new DataManager();
	private final ClimbingGeometryBuilder hullGeometryBuilder = new ClimbingGeometryBuilder();
	private final ClimbingHulls climbingHulls = new ClimbingHulls();
	private final TerrainWireframe.ElevationSource hullElevations = this::getHullElevation;
	private final ClimbingHullView climbingHullView;
	private final RouteLines routeLines = new RouteLines();
	// The routes whose line shows, as of the last view update.
	private final List<RouteLine> shownRoutes = new ArrayList<>();
	private final TerrainWireframe.ElevationSource groundElevations = this::getGroundElevation;
	// Where the bottom pins stand, see getElevation.
	private final RouteLines.BottomElevation routeBottomElevations =
			(bottom, distance) -> getElevation(bottom, distance, true);
	private final RouteLineView routeLineView;
	private final TerrainElevation terrainElevation;
	private final AugmentedRealityViewManager arViewManager;
	// Shown as a pin at their start and, on the terrain, at their top, see setRouteRelations.
	private volatile List<RouteRelation> routeRelations = Collections.emptyList();
	// The nodes the route relations start and end at, whose own pins theirs take the place of.
	private volatile Set<Long> routeRelationEnds = Collections.emptySet();
	private final ReloadGuard poisLoaded = ReloadGuard.afterMeters(POI_RELOAD_METERS);
	private final ReloadGuard hullsLoaded = ReloadGuard.afterShare(HULL_RELOAD_SHARE);
	// Whether the POIs within the view distance are to be picked again, see updateBoundingBox.
	private final BoundingBoxGuard boundingBoxPicked = new BoundingBoxGuard();
	// Where refreshAround was last asked to load around, null before the first time.
	private Vector4d requestedCenter;
	// The display filters as of the last draw, and whether each POI tested against them passed.
	// Testing one takes its tags apart, too much for every POI on every frame, so it is done
	// once: until the filters change, see readDisplayFilters, or the data may have.
	private NodeDisplayFilters.Settings displayFilters;
	private final Map<GeoNode, Boolean> displayFilterResults = new IdentityHashMap<>();
	private double maxDistance;
	private boolean useElevation;
	private boolean showClimbingHulls;
	// While set, only the pins of these nodes show, see showOnly; null for all.
	private Set<Long> shownOnly;
	// Along with them, the pin of this node, see-through; null for none.
	private Long seeThroughNodeId;
	// A node whose pin is left out, null for none.
	private Long leftOutNodeId;
	// Whether the shared terrain was given back, see onDestroy.
	private boolean destroyed;

	/**
	 * Whoever builds a scene calls its {@link #onDestroy} when its screen is destroyed.
	 *
	 * @param container the view the scene draws in, holding a ClimbingHullView climbingHulls and a
	 *                  RouteLineView routeLines, in a RelativeLayout; it is grown to stay over the
	 *                  view as it turns with the camera's roll, see rotateWithCamera
	 * @param onLoaded  run when more of the data around the camera is loaded, to update the scene
	 */
	public AugmentedRealityScene(AppCompatActivity activity, VirtualCamera camera,
	                             ViewGroup container, Runnable onLoaded) {
		this.activity = activity;
		this.camera = camera;
		this.configs = Configs.instance(activity);
		this.onLoaded = onLoaded;
		// Shared with the other scenes alive, and with the next one, see onDestroy.
		this.terrainElevation = TerrainElevation.acquireShared(activity);
		this.arViewManager = new AugmentedRealityViewManager(container, camera);
		this.climbingHullView = container.findViewById(R.id.climbingHulls);
		this.routeLineView = container.findViewById(R.id.routeLines);
		// Sized to the view once laid out, and again whenever its size changes, as when the
		// view only shows later.
		((View) container.getParent()).addOnLayoutChangeListener((view, left, top, right, bottom,
		                                                         oldLeft, oldTop, oldRight,
		                                                         oldBottom) -> {
			if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
				container.post(arViewManager::postInit);
			}
		});
		container.post(arViewManager::postInit);
		loadSettings();
	}

	private static boolean isInBoundingBox(GeoNode poi, double centerLatitude,
	                                       double centerLongitude,
	                                       double deltaLatitude, double deltaLongitude) {
		return (poi.decimalLatitude > centerLatitude - deltaLatitude &&
				poi.decimalLatitude < centerLatitude + deltaLatitude)
				&& (poi.decimalLongitude > centerLongitude - deltaLongitude &&
				poi.decimalLongitude < centerLongitude + deltaLongitude);
	}

	/**
	 * The hull of a crag or area, with the relation as a POI in its middle, labelled with its
	 * route count like on the map.
	 */
	private static ClimbingHull toClimbingHull(ClimbingGeometryBuilder.GeometrySpec geometry) {
		OsmCollectionEntity collection = geometry.collection;
		// Unnamed relations have no label on the map to place it at.
		MapCoordinate centre = geometry.labelCoordinate != null
				? geometry.labelCoordinate : DataManager.collectionCenter(collection);
		GeoNode pin = geometry.pin(centre);

		double[] latitudes = new double[geometry.coordinates.size()];
		double[] longitudes = new double[geometry.coordinates.size()];
		for (int index = 0; index < latitudes.length; index++) {
			latitudes[index] = geometry.coordinates.get(index).getLatitude();
			longitudes[index] = geometry.coordinates.get(index).getLongitude();
		}
		long[] nodeIds = new long[collection.osmNodes.size()];
		int nodeIndex = 0;
		for (Long nodeId : collection.osmNodes) {
			nodeIds[nodeIndex++] = nodeId;
		}
		return new ClimbingHull(geometry.key,
				collection.entityClimbingType == OsmEntity.EntityClimbingType.crag
						? OsmEntity.EntityClimbingType.crag : OsmEntity.EntityClimbingType.area,
				pin, latitudes, longitudes, nodeIds);
	}

	/** @return whether two pins of the same crag or area draw alike */
	private static boolean drawsAlike(GeoNode earlier, GeoNode loaded) {
		return earlier.routeCountFiltered == loaded.routeCountFiltered
				&& earlier.toJSONString().equals(loaded.toJSONString());
	}

	/**
	 * Without both elevations, the POI is taken to be on level ground with the observer, so close
	 * POIs still sit below the horizon as on flat ground, and move little once the terrain loads.
	 *
	 * @return the angle above the horizontal at which the observer sees the POI, in degree
	 */
	private static double getElevationAngle(double observerElevation, double poiElevation,
	                                        double distance) {
		if (Double.isNaN(observerElevation) || Double.isNaN(poiElevation)) {
			return Math.toDegrees(Math.atan2(-EYE_HEIGHT_METERS, distance));
		}
		return GeoUtils.calculateElevationAngle(observerElevation, poiElevation, distance);
	}

	/**
	 * To be called when the screen of the scene is destroyed. The scenes share the terrain, whose
	 * tiles are only let go of once every scene gave it back, see TerrainElevation.acquireShared.
	 */
	public void onDestroy() {
		// Once only, as each scene took it once.
		if (!destroyed) {
			destroyed = true;
			TerrainElevation.releaseShared();
		}
	}

	public AugmentedRealityViewManager getViewManager() {
		return arViewManager;
	}

	public TerrainElevation getTerrainElevation() {
		return terrainElevation;
	}

	public double getMaxDistance() {
		return maxDistance;
	}

	/**
	 * @return how far the closest of the loaded nodes of a type is from the camera, in meters, NaN
	 * while none is loaded
	 */
	public double getNearestDistance(OsmEntity.EntityClimbingType type) {
		double nearest = Double.NaN;
		for (DisplayableGeoNode displayable : arPOIs.values()) {
			GeoNode node = displayable.getGeoNode();
			if (node.getNodeType() != type) {
				continue;
			}
			double distance = GeoUtils.calculateDistance(camera, node);
			if (Double.isNaN(nearest) || distance < nearest) {
				nearest = distance;
			}
		}
		return nearest;
	}

	/**
	 * @return whether the routes are placed at their elevation, rather than on level ground
	 */
	public boolean usesElevation() {
		return useElevation;
	}

	/**
	 * Takes the A.R. settings: the view distance, whether the routes follow the elevation, and
	 * whether the crag and area outlines show. Either way, far crags show their pin instead of
	 * their routes.
	 */
	public void loadSettings() {
		maxDistance = configs.getInt(Configs.ConfigKey.maxNodesShowDistanceLimit);
		useElevation = configs.isArElevation();
		showClimbingHulls = configs.getBoolean(Configs.ConfigKey.arClimbingHulls);
		climbingHullView.setVisibility(showClimbingHulls && shownOnly == null ? View.VISIBLE
				: View.INVISIBLE);
		readDisplayFilters();
	}

	/**
	 * Takes the display filters as they are set now. What was kept of the POIs passing the ones
	 * before goes when these filter differently.
	 */
	private void readDisplayFilters() {
		NodeDisplayFilters.Settings filters = NodeDisplayFilters.read(configs);
		if (!filters.equals(displayFilters)) {
			displayFilters = filters;
			displayFilterResults.clear();
		}
	}

	/**
	 * @return whether the POI passes the display filters as of the last draw
	 */
	private boolean passesDisplayFilters(GeoNode poi) {
		Boolean passes = displayFilterResults.get(poi);
		if (passes == null) {
			passes = displayFilters.matches(poi);
			displayFilterResults.put(poi, passes);
		}
		return passes;
	}

	/**
	 * Leaves out the pin of a node, which its owner draws some other way.
	 *
	 * @param nodeId null to leave out none
	 */
	public void leaveOut(Long nodeId) {
		leftOutNodeId = nodeId;
	}

	/**
	 * Has the next refreshAround load the POIs, crags, areas and route numbers again, however
	 * little the observer moved, for data changed since, like a route just saved.
	 */
	public void dataChanged() {
		poisLoaded.dataChanged();
		hullsLoaded.dataChanged();
		displayFilterResults.clear();
	}

	/**
	 * Takes all the pins off the view, to be put back by the next draw, as after a change of the
	 * display filters.
	 */
	public void clearPins() {
		removePinsExcept(Collections.emptySet());
		// The pins kept out of view are drawn as before the change too, so they all go as well,
		// whatever they are of.
		arViewManager.removeHiddenPOIs();
	}

	/**
	 * Takes the pins off the view, on display or kept out of view, but those of some nodes.
	 */
	private void removePinsExcept(Set<Long> kept) {
		for (Map.Entry<Long, GeoNode> entry : boundingBoxPOIs.entrySet()) {
			if (!kept.contains(entry.getKey())) {
				arViewManager.removePOIFromView(entry.getValue());
			}
		}
		for (RouteRelation relation : routeRelations) {
			if (!kept.contains(relation.start.getID())) {
				arViewManager.removePOIFromView(relation.start);
			}
			arViewManager.removePOIFromView(relation.top);
		}
		for (RouteLine route : routeLines.getRoutes()) {
			arViewManager.removePOIFromView(route.top);
		}
		for (ClimbingHull hull : climbingHulls.getHulls()) {
			arViewManager.removePOIFromView(hull.pin);
		}
	}

	/**
	 * Shows only the pins of some nodes, as those of the routes aligned on while the user aligns
	 * the A.R. on another, each where and when it shows the rest of the time, and along with them
	 * the pin of one more node see-through, letting taps through it, as that of the route being
	 * aligned on, where the A.R. has it until it is dropped, see getSeeThroughPin, laid out even
	 * out of view, for a line to it to show the way to it; the other pins, the route lines and
	 * the crag and area outlines hide. The terrain and the horizon stay.
	 *
	 * @param nodeIds          null to show everything again, from the next draw on
	 * @param seeThroughNodeId null for no see-through pin
	 */
	public void showOnly(Set<Long> nodeIds, Long seeThroughNodeId) {
		Long seeThrough = nodeIds == null ? null : seeThroughNodeId;
		if (Objects.equals(nodeIds, shownOnly)
				&& Objects.equals(seeThrough, this.seeThroughNodeId)) {
			return;
		}
		shownOnly = nodeIds == null ? null : new HashSet<>(nodeIds);
		this.seeThroughNodeId = seeThrough;
		arViewManager.setSeeThrough(activity, seeThrough);
		boolean all = shownOnly == null;
		climbingHullView.setVisibility(showClimbingHulls && all ? View.VISIBLE : View.INVISIBLE);
		routeLineView.setVisibility(all ? View.VISIBLE : View.INVISIBLE);
		if (!all) {
			Set<Long> kept = new HashSet<>(shownOnly);
			if (seeThrough != null) {
				kept.add(seeThrough);
			}
			removePinsExcept(kept);
		}
	}

	/**
	 * @return the view of the see-through pin, see showOnly, null while it is not on display;
	 * its tip is on the node, see AugmentedRealityViewManager.placePin, off the view when the
	 * node is out of it, see AugmentedRealityUtils.MAX_PROJECTED_DEGREES
	 */
	public View getSeeThroughPin() {
		return arViewManager.getSeeThroughPin();
	}

	/**
	 * @return whether a node's pin shows while only some do, see showOnly: as one of them, or
	 * see-through
	 */
	private static boolean isShownAmongOnly(Set<Long> only, Long seeThrough, long nodeId) {
		return only == null || only.contains(nodeId) || isSeeThrough(seeThrough, nodeId);
	}

	private static boolean isSeeThrough(Long seeThrough, long nodeId) {
		return seeThrough != null && seeThrough == nodeId;
	}

	/**
	 * @return how far from the camera axis a node's pin is laid out: the see-through pin wherever
	 * it is, even out of view, for the line to it, see showOnly; the others only in view
	 */
	private static double maxViewAngleFor(Long seeThrough, long nodeId, double maxViewAngle) {
		return isSeeThrough(seeThrough, nodeId) ? Double.POSITIVE_INFINITY : maxViewAngle;
	}

	/**
	 * Loads the POIs, crags, areas and routes around a point, calling onLoaded once more of
	 * them are. They are only loaded again once the observer moved away from where they were
	 * loaded, the view distance changed or the data did, see POI_RELOAD_METERS,
	 * HULL_RELOAD_SHARE and dataChanged.
	 */
	public void refreshAround(final Vector4d center) {
		requestedCenter = center;
		refreshPois(center);
		refreshHulls(center);
	}

	/**
	 * Loads the POIs around the observer, a little further than the view distance, see
	 * POI_RELOAD_METERS.
	 */
	private void refreshPois(final Vector4d center) {
		final double distance = maxDistance;
		if (!poisLoaded.request(center.x, center.y, distance)) {
			return;
		}

		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<Boolean>() {
					@Override
					protected Boolean doWork() {
						return offlineDataManager.loadAround(activity.getApplicationContext(),
								center, distance + POI_LOAD_MARGIN_METERS, arPOIs);
					}

					@Override
					protected void thenDoUiRelatedWork(Boolean result) {
						boolean again = poisLoaded.loaded(center.x, center.y, distance);
						if (result) {
							// More POIs are held, for onLoaded to pick the ones in reach from.
							boundingBoxPicked.poisChanged();
							onLoaded.run();
						}
						if (again) {
							refreshPois(requestedCenter);
						}
					}
				});
	}

	/**
	 * Loads the crag and area relations around the observer, as their hulls, and the numbers of
	 * the crags' routes, see HULL_RELOAD_SHARE.
	 */
	private void refreshHulls(final Vector4d center) {
		final double distance = maxDistance;
		if (!hullsLoaded.request(center.x, center.y, distance)) {
			return;
		}

		Constants.DB_EXECUTOR
				.execute(new UiRelatedTask<List<ClimbingHull>>() {
					private Map<Long, Integer> routeNumbers;
					private List<RouteRelation> relations;
					private List<RouteLine> routes;

					@Override
					protected List<ClimbingHull> doWork() {
						double reach = distance * POI_CACHE_EVICTION_MARGIN;
						double deltaLatitude = Math.toDegrees(reach / GeoUtils.EARTH_RADIUS_M);
						double deltaLongitude = Math.toDegrees(reach
								/ (Math.cos(Math.toRadians(center.x)) * GeoUtils.EARTH_RADIUS_M));
						MapBounds bounds = MapBounds.fromUnwrapped(center.x + deltaLatitude,
								center.y + deltaLongitude, center.x - deltaLatitude,
								center.y - deltaLongitude);

						// One load reads each element once, see CachedDataManager.
						CachedDataManager data = new CachedDataManager();
						List<ClimbingHull> hulls = new ArrayList<>();
						for (ClimbingGeometryBuilder.GeometrySpec geometry : hullGeometryBuilder.load(
								activity.getApplicationContext(), bounds, data,
								OsmEntity.EntityClimbingType.crag,
								OsmEntity.EntityClimbingType.area)) {
							// Hulls are those of relations; the OSM wiki has no crag mapped as a way.
							if (!geometry.polygon
									|| geometry.collection.osmType != OsmEntity.EntityOsmType.relation) {
								continue;
							}
							hulls.add(toClimbingHull(geometry));
						}
						routeNumbers = RouteNumbers.load(activity.getApplicationContext(), bounds,
								data);
						relations = RouteRelation.load(activity.getApplicationContext(), bounds,
								data);
						routes = loadRouteLines(data, bounds, RouteRelation.byPitch(relations));
						return hulls;
					}

					@Override
					protected void thenDoUiRelatedWork(List<ClimbingHull> hulls) {
						boolean again = hullsLoaded.loaded(center.x, center.y, distance);
						// The routes and route relations are read anew, and the ones before go.
						displayFilterResults.clear();
						Map<String, GeoNode> earlierPins = new HashMap<>();
						for (ClimbingHull earlier : climbingHulls.getHulls()) {
							earlierPins.put(earlier.key, earlier.pin);
						}
						for (ClimbingHull removed : climbingHulls.setHulls(hulls)) {
							arViewManager.removePOIFromView(removed.pin);
						}
						// A pin keeps its drawing, on view and out of it, so one that changed, as
						// its route count with the display filters, is put on the view again.
						for (ClimbingHull hull : hulls) {
							GeoNode earlier = earlierPins.get(hull.key);
							if (earlier != null && !drawsAlike(earlier, hull.pin)) {
								arViewManager.removePOIFromView(earlier);
							}
						}
						arViewManager.setRouteNumbers(activity, routeNumbers);
						for (RouteLine removed : routeLines.setRoutes(routes)) {
							arViewManager.removePOIFromView(removed.top);
						}
						setRouteRelations(relations);
						onLoaded.run();
						if (again) {
							refreshHulls(requestedCenter);
						}
					}
				});
	}

	/**
	 * Replaces the route relations. The pins on display for the nodes they start and end at, old
	 * and new, go, as the view of a pin is kept for any pin of the same node, so they come back
	 * as the right ones.
	 */
	private void setRouteRelations(List<RouteRelation> loaded) {
		Set<Long> ends = new HashSet<>();
		for (RouteRelation relation : loaded) {
			ends.add(relation.start.getID());
			ends.add(relation.top.getID());
			arViewManager.removePOIFromView(relation.start);
			arViewManager.removePOIFromView(relation.top);
		}
		for (RouteRelation relation : routeRelations) {
			arViewManager.removePOIFromView(relation.start);
			arViewManager.removePOIFromView(relation.top);
		}
		routeRelations = loaded;
		routeRelationEnds = ends;
	}

	/**
	 * The routes mapped as a way within the bounds, see RouteLine.
	 *
	 * @param pitchRoutes the route relations by the IDs of their pitches
	 */
	private List<RouteLine> loadRouteLines(DataManager dataManager, MapBounds bounds,
	                                       Map<Long, RouteRelation> pitchRoutes) {
		Map<String, OsmCollectionEntity> collections = dataManager.loadCollectionData(
				activity.getApplicationContext(), dataManager.loadCollectionBBox(
						activity.getApplicationContext(), bounds,
						OsmEntity.EntityClimbingType.route));
		List<OsmCollectionEntity> ways = new ArrayList<>();
		List<Long> nodeIds = new ArrayList<>();
		for (OsmCollectionEntity collection : collections.values()) {
			if (collection.osmType == OsmEntity.EntityOsmType.way) {
				ways.add(collection);
				nodeIds.addAll(collection.osmNodes);
			}
		}
		Map<Long, OsmNode> nodes = dataManager.loadNodeData(
				activity.getApplicationContext(), nodeIds);
		List<RouteLine> routes = new ArrayList<>();
		for (OsmCollectionEntity way : ways) {
			try {
				RouteLine route = RouteLine.fromWay(way, nodes, pitchRoutes.get(way.osmID));
				if (route != null) {
					routes.add(route);
				}
			} catch (JSONException ignore) {
			}
		}
		return routes;
	}

	/**
	 * Keeps the POIs within the view distance of the camera, and releases those well beyond it.
	 * To be called whenever the camera may have moved, as on every frame of ARCore's: they are
	 * only gone through once it moved far enough to matter, the view distance changed or more of
	 * them are loaded, see BoundingBoxGuard.
	 */
	public void updateBoundingBox() {
		double latitude = camera.decimalLatitude;
		double longitude = camera.decimalLongitude;
		if (!boundingBoxPicked.request(latitude, longitude, maxDistance)) {
			return;
		}
		double deltaLatitude = Math.toDegrees(maxDistance / GeoUtils.EARTH_RADIUS_M);
		double deltaLongitude = Math.toDegrees(
				maxDistance / (Math.cos(Math.toRadians(latitude)) * GeoUtils.EARTH_RADIUS_M));

		for (Long poiID : arPOIs.keySet()) {
			DisplayableGeoNode displayable = arPOIs.get(poiID);
			if (displayable == null) {
				continue;
			}

			GeoNode poi = displayable.getGeoNode();
			if (isInBoundingBox(poi, latitude, longitude, deltaLatitude, deltaLongitude)) {
				boundingBoxPOIs.put(poiID, poi);
				continue;
			}

			if (boundingBoxPOIs.containsKey(poiID)) {
				arViewManager.removePOIFromView(poi);
				boundingBoxPOIs.remove(poiID);
			}

			// Cached POIs are kept a margin beyond the loaded box so walking along its edge does
			// not drop and re-query the same nodes; past that they are released so the cache stays
			// bounded by the view distance instead of by how far the session has travelled.
			if (!isInBoundingBox(poi, latitude, longitude,
					deltaLatitude * POI_CACHE_EVICTION_MARGIN,
					deltaLongitude * POI_CACHE_EVICTION_MARGIN)) {
				arPOIs.remove(poiID);
				footElevations.remove(poiID);
				displayFilterResults.remove(poi);
			}
		}
	}

	/**
	 * Turns the pins, lines and outlines with the camera's roll, around the middle of the view.
	 */
	public void rotateWithCamera() {
		arViewManager.setRotation((float) (-camera.degRoll + camera.screenRotation));
	}

	/**
	 * Where the observer's eye is, as the routes are placed around it.
	 *
	 * @param lookUpTerrain whether to look the terrain up even when the routes are on level
	 *                      ground, for a terrain horizon
	 */
	public EyeElevation getEyeElevation(boolean lookUpTerrain) {
		// Without elevation, every POI is on level ground with the observer, see
		// getElevationAngle, and unless the terrain is drawn, none is looked up, so none is
		// downloaded.
		boolean useTerrain = false;
		double observerElevation = Double.NaN;
		if (useElevation || lookUpTerrain) {
			double observerGround = terrainElevation.getElevation(camera.decimalLatitude,
					camera.decimalLongitude, TerrainElevation.DETAIL_ZOOM);
			useTerrain = !Double.isNaN(observerGround);
			// Best first: the height aligning on routes tells, then the terrain under the
			// observer, then the altitude, from GPS and the barometer, until the terrain is
			// loaded.
			if (camera.alignedElevations != null) {
				observerElevation = camera.alignedElevations.eyeElevationMeters();
			} else {
				observerElevation = useTerrain
						? observerGround + EYE_HEIGHT_METERS : getGpsElevation();
			}
		}
		return new EyeElevation(useTerrain, observerElevation);
	}

	/**
	 * Lays the routes, crags and areas out as the camera sees them from the eye, and draws them.
	 * The pins that do not show in this frame are only hidden, see
	 * AugmentedRealityViewManager.hidePOIFromView, as most are back as the camera turns; what
	 * changes a pin's drawing takes it off the view, to be drawn again.
	 */
	public void draw(EyeElevation eye) {
		Set<Long> only = shownOnly;
		Long seeThrough = seeThroughNodeId;
		boolean useTerrain = useElevation && eye.onTerrain();
		double observerElevation = useElevation ? eye.meters() : Double.NaN;
		readDisplayFilters();
		updateHulls(useTerrain, observerElevation);

		if (updatingView.tryAcquire()) {
			try {
				double maxViewAngle = computeMaxViewAngle();
				visible.clear();
				//find elements in view and sort them by distance.

				Set<Long> relationEnds = routeRelationEnds;
				for (Map.Entry<Long, GeoNode> entry : boundingBoxPOIs.entrySet()) {
					GeoNode poi = entry.getValue();
					// The pins of a route relation take the place of those of the nodes it starts
					// and ends at, below. They share their view, so these are not taken off it.
					if (relationEnds.contains(entry.getKey())) {
						continue;
					}
					// The routes of a crag too far to make them out are left to the crag pin.
					// Route tops only show on the terrain, those of the routes mapped as a way
					// along with their line, below.
					if (!entry.getKey().equals(leftOutNodeId)
							&& isShownAmongOnly(only, seeThrough, entry.getKey())
							&& !climbingHulls.isHidden(entry.getKey())
							&& !(poi.isRouteTop()
							&& (!useTerrain || routeLines.isTop(entry.getKey())))
							&& placeInView(poi, true, useTerrain, observerElevation,
							maxViewAngleFor(seeThrough, entry.getKey(), maxViewAngle))) {
						visible.add(poi);
					} else if (!arViewManager.isDrawnFrom(routeLines.getTop(entry.getKey()))) {
						// The top of a route mapped as a way shares the view of its node, and is
						// shown or hidden along with the route, below. Its pin is left to that:
						// hidden here only to be shown again there, on every frame, it would let
						// go of every press, and a tap would never open the route. A pin drawn
						// from anything else still goes, for the top's to be drawn in its place.
						arViewManager.hidePOIFromView(poi);
					}
				}

				// Up close, the routes show in the crag and area hulls instead of their pin. As
				// on the map, the pins are not filtered.
				for (ClimbingHull hull : climbingHulls.getHulls()) {
					if (only == null && !hull.isShowingRoutes()
							&& placeInView(hull.pin, false, useTerrain, observerElevation,
							maxViewAngle)) {
						visible.add(hull.pin);
					} else {
						arViewManager.hidePOIFromView(hull.pin);
					}
				}

				// Route relations show as a route pin at their start, as the routes of their own
				// do, and on the terrain, along with their pitches below, up to a pin at their
				// top, as the routes mapped as a way do.
				for (RouteRelation relation : routeRelations) {
					boolean shown = isRouteShown(relation.start);
					if (shown && isShownAmongOnly(only, seeThrough, relation.start.getID())
							&& placeInView(relation.start, false, useTerrain, observerElevation,
							maxViewAngleFor(seeThrough, relation.start.getID(), maxViewAngle))) {
						visible.add(relation.start);
					} else {
						arViewManager.hidePOIFromView(relation.start);
					}
					if (shown && only == null && useTerrain
							&& placeInView(relation.top, false, true, observerElevation,
							maxViewAngle)) {
						visible.add(relation.top);
					} else {
						arViewManager.hidePOIFromView(relation.top);
					}
				}

				// On the terrain, the routes mapped as a way show it along with their bottom,
				// up to a pin at their top. The line and the top may well be in view while the
				// bottom is not, looking up the wall.
				shownRoutes.clear();
				for (RouteLine route : routeLines.getRoutes()) {
					// Neither the lines nor the tops show along with only some pins.
					if (only != null) {
						arViewManager.hidePOIFromView(route.top);
						continue;
					}
					// A pitch of a route relation shows along with the route, which has the top.
					// Its own top may be the route's, so it is left on the view.
					if (route.routeRelation != null) {
						if (useTerrain && isRouteShown(route.routeRelation.start)) {
							shownRoutes.add(route);
						}
						continue;
					}
					GeoNode bottom = useTerrain ? boundingBoxPOIs.get(route.bottom.getID()) : null;
					if (bottom != null && isRouteShown(bottom)) {
						shownRoutes.add(route);
						if (placeInView(route.top, false, true, observerElevation,
								maxViewAngle)) {
							visible.add(route.top);
							continue;
						}
					}
					arViewManager.hidePOIFromView(route.top);
				}
				if (only == null) {
					updateRouteLines(useTerrain, observerElevation);
				}

				Collections.sort(visible);

				//keep the closest elements, then stack them farthest first so the closest, largest
				//ones end up on top.
				int maxDisplayed = configs.getInt(Configs.ConfigKey.maxNodesShowCountLimit);
				int displayLimit = 0;
				zOrderedDisplay.clear();
				for (GeoNode poi : visible) {
					if (displayLimit < maxDisplayed) {
						displayLimit++;

						zOrderedDisplay.add(poi);
					} else {
						arViewManager.hidePOIFromView(poi);
					}
				}

				Collections.reverse(zOrderedDisplay);

				for (int i = 0; i < zOrderedDisplay.size(); i++) {
					arViewManager.addOrUpdatePOIToView(activity, zOrderedDisplay.get(i), i);
				}
			} finally {
				updatingView.release();
			}
		}
	}

	/**
	 * Roll can turn any direction towards the corners of the view, so the widest angle from the
	 * camera axis that can still be on screen is the one to the corners.
	 */
	private double computeMaxViewAngle() {
		Vector2d angleOfView = camera.angleOfViewDeg;
		return Math.toDegrees(Math.atan(Math.hypot(
				Math.tan(Math.toRadians(angleOfView.x / 2)),
				Math.tan(Math.toRadians(angleOfView.y / 2)))));
	}

	/**
	 * @return whether the route shows, in view or not: within the view distance, not left to its
	 * crag pin, and passing the display filters
	 */
	private boolean isRouteShown(GeoNode bottom) {
		return !climbingHulls.isHidden(bottom.getID())
				&& GeoUtils.calculateDistance(camera, bottom) < maxDistance
				&& passesDisplayFilters(bottom);
	}

	/**
	 * Lays the routes out on the terrain, and draws the lines of the routes shown. Off the
	 * terrain, none shows, so none is laid out.
	 */
	private void updateRouteLines(boolean useTerrain, double observerElevation) {
		if (useTerrain) {
			routeLines.update(camera.decimalLatitude, camera.decimalLongitude, groundElevations,
					routeBottomElevations, terrainElevation.getVersion(),
					SystemClock.elapsedRealtime());
		}
		routeLineView.show(routeLines, shownRoutes, camera, observerElevation, EYE_HEIGHT_METERS,
				arViewManager.getViewSize());
	}

	/**
	 * Places the POI around the camera, when it is within the view distance and in view.
	 *
	 * @param filter       whether the POI is subject to the display filters
	 * @param useTerrain   whether the observer elevation comes from the terrain model
	 * @param maxViewAngle the widest angle from the camera axis still in view
	 * @return whether the POI is in view
	 */
	private boolean placeInView(GeoNode poi, boolean filter, boolean useTerrain,
	                            double observerElevation, double maxViewAngle) {
		double distance = GeoUtils.calculateDistance(camera, poi);
		if (distance >= maxDistance || (filter && !passesDisplayFilters(poi))) {
			return false;
		}

		double deltaAzimuth = GeoUtils.calculateTheoreticalAzimuth(camera, poi);
		double difAngle = GeoUtils.diffAngle(deltaAzimuth, camera.degAzimuth);
		double elevationAngle = getElevationAngle(observerElevation,
				getElevation(poi, distance, useTerrain), distance);
		if (AugmentedRealityUtils.angleFromCameraAxis(difAngle, elevationAngle,
				-camera.degPitch) > maxViewAngle) {
			return false;
		}

		poi.distanceMeters = distance;
		poi.deltaDegAzimuth = deltaAzimuth;
		poi.difDegAngle = difAngle;
		poi.elevationDegAngle = elevationAngle;
		return true;
	}

	/**
	 * Lays the crag and area hulls out around the observer and draws them. Like the POIs, they
	 * only follow the terrain once it gives the observer elevation, so both elevations come from
	 * the same source.
	 *
	 * @param useTerrain whether the observer elevation comes from the terrain model
	 */
	private void updateHulls(boolean useTerrain, double observerElevation) {
		// Hidden, by the setting or while only some pins show, the outlines are not laid out, and
		// need no elevations, which would be looked up for nothing.
		boolean outlines = showClimbingHulls && shownOnly == null;
		climbingHulls.update(camera.decimalLatitude, camera.decimalLongitude, maxDistance,
				outlines, useTerrain && outlines ? hullElevations : null,
				terrainElevation.getVersion(),
				SystemClock.elapsedRealtime());
		climbingHullView.show(climbingHulls, camera, observerElevation, EYE_HEIGHT_METERS,
				arViewManager.getViewSize());
	}

	/**
	 * Ground elevation along the hulls, the way getElevation gives it for the POIs, so the
	 * outlines go around the routes: at the foot of the walls close by, see getFootElevation.
	 */
	private double getHullElevation(double latitude, double longitude, double distance) {
		return distance < TERRAIN_DETAIL_DISTANCE_METERS
				? terrainElevation.getFootElevation(latitude, longitude,
				TerrainElevation.DETAIL_ZOOM)
				: getGroundElevation(latitude, longitude, distance);
	}

	/**
	 * @return the GPS altitude above sea level of the observer, NaN when unknown
	 */
	private double getGpsElevation() {
		// An elevation of 0 means unknown, see GeoNode.updatePOILocation.
		return camera.elevationMeters != 0 ? camera.elevationMeters : Double.NaN;
	}

	/**
	 * Elevation of the POI from the same source as the observer elevation, so the offset between
	 * sources does not move the POI. OSM elevations mostly come from phone GPS altitudes, which
	 * can be tens of meters away from the terrain model: at a close crag that is enough to put
	 * the routes far below the horizon. On the terrain, the routes aligned on correct the model
	 * around them, see AlignedElevations.
	 *
	 * @param useTerrain whether the observer elevation comes from the terrain model rather than
	 *                   from the GPS altitude
	 * @return the elevation above sea level of the POI, NaN when unknown
	 */
	private double getElevation(GeoNode poi, double distance, boolean useTerrain) {
		double elevation = getModelElevation(poi, distance, useTerrain);
		if (useTerrain && camera.alignedElevations != null
				&& distance < TERRAIN_DETAIL_DISTANCE_METERS) {
			elevation += camera.alignedElevations.offsetMeters(poi.getID(), poi.decimalLatitude,
					poi.decimalLongitude);
		}
		return elevation;
	}

	/**
	 * Elevation of the POI as the terrain model, or else the POI, has it, see getElevation.
	 */
	private double getModelElevation(GeoNode poi, double distance, boolean useTerrain) {
		if (useTerrain) {
			// The top of a route is on top of the wall, which its foot would read far below.
			return distance < TERRAIN_DETAIL_DISTANCE_METERS && !poi.isRouteTop()
					? getFootElevation(poi)
					: getGroundElevation(poi.decimalLatitude, poi.decimalLongitude, distance);
		}
		// An elevation of 0 means unknown, see GeoNode.updatePOILocation.
		return poi.elevationMeters != 0 ? poi.elevationMeters : Double.NaN;
	}

	/**
	 * Routes start at the foot of their wall, which the terrain model smooths into a slope, see
	 * TerrainElevation.getFootElevation. Kept until more terrain loads, as it takes several
	 * lookups.
	 */
	private double getFootElevation(GeoNode poi) {
		int terrainVersion = terrainElevation.getVersion();
		FootElevation foot = footElevations.get(poi.getID());
		if (foot == null || foot.terrainVersion != terrainVersion) {
			foot = new FootElevation(terrainElevation.getFootElevation(poi.decimalLatitude,
					poi.decimalLongitude, TerrainElevation.DETAIL_ZOOM), terrainVersion);
			footElevations.put(poi.getID(), foot);
		}
		return foot.meters;
	}

	/**
	 * The elevation the terrain model stands a pin at, before the routes aligned on correct it,
	 * for what they tell to be worked out, see AlignedElevations.
	 *
	 * @param distance how far the pin is from the eye, in meters
	 * @return the elevation above sea level, NaN while unknown
	 */
	public double getPinElevation(GeoNode poi, double distance) {
		return getModelElevation(poi, distance, true);
	}

	/**
	 * Ground elevation from the terrain model, the same way for the terrain wireframe and the
	 * POIs too far for their foot to matter, so these sit on it.
	 *
	 * @return the elevation above sea level, NaN while unknown
	 */
	public double getGroundElevation(double latitude, double longitude, double distance) {
		return terrainElevation.getElevation(latitude, longitude,
				distance < TERRAIN_DETAIL_DISTANCE_METERS
						? TerrainElevation.DETAIL_ZOOM : TerrainElevation.BASE_ZOOM);
	}

	/**
	 * The elevation of the observer's eye above sea level, NaN when unknown.
	 *
	 * @param onTerrain whether it comes from the terrain model rather than from the GPS altitude
	 */
	public record EyeElevation(boolean onTerrain, double meters) {
	}

	/**
	 * Where the terrain model stands pins, see getPinElevation.
	 */
	public interface PinElevations {
		/**
		 * @param distance how far the pin is from the eye, in meters
		 * @return the elevation above sea level, NaN while unknown
		 */
		double getElevation(GeoNode poi, double distance);
	}

	private record FootElevation(double meters, int terrainVersion) {
	}
}
