package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.GeoUtils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A crag or area relation in the AR view: the convex hull the map draws for it, laid on the
 * ground. Up close, its routes show in it; once it is too small in the view to make them out, its
 * pin stands in for them.
 */
public class ClimbingHull {
	// The routes take over from the pin once the hull spans this much of the horizon, and give way
	// to it again below the smaller angle, so a hull on the threshold does not flicker.
	static final double ROUTES_MIN_DEGREES = 10;
	static final double PIN_MAX_DEGREES = 7;
	// The outline is cut in steps growing with the distance, so it follows the terrain about as
	// closely all over the view.
	static final double MIN_STEP_METERS = 5;
	static final double STEP_PER_METER = 0.02;
	static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

	public final String key;
	public final GeoNode.NodeTypes type;
	// The relation as a POI, in the middle of the hull.
	public final GeoNode pin;
	// The hull, as a closed ring.
	private final double[] latitudes;
	private final double[] longitudes;
	// The nodes of the relation and of the relations in it, such as the routes.
	private final long[] nodeIds;
	private boolean showingRoutes;
	// As of the last build, around its origin.
	private GroundLine fill;
	private final List<GroundLine> outline = new ArrayList<>();

	/**
	 * @param latitudes  the hull, as a closed ring
	 * @param longitudes the hull, as a closed ring
	 * @param nodeIds    the nodes of the relation and of the relations in it
	 */
	public ClimbingHull(String key, GeoNode.NodeTypes type, GeoNode pin, double[] latitudes,
	                    double[] longitudes, long[] nodeIds) {
		this.key = key;
		this.type = type;
		this.pin = pin;
		this.latitudes = latitudes;
		this.longitudes = longitudes;
		this.nodeIds = nodeIds;
	}

	/**
	 * Takes over whether the routes show, from the same hull loaded earlier.
	 */
	void keepStateOf(ClimbingHull earlier) {
		showingRoutes = earlier.showingRoutes;
	}

	/**
	 * @return whether the routes in the hull show, rather than its pin, as of the last build
	 */
	public boolean isShowingRoutes() {
		return showingRoutes;
	}

	long[] getNodeIds() {
		return nodeIds;
	}

	/**
	 * @return the inside of the hull within the range, null when there is none or the observer is
	 * inside
	 */
	GroundLine getFill() {
		return fill;
	}

	/**
	 * @return the edges of the hull within the range
	 */
	List<GroundLine> getOutline() {
		return outline;
	}

	/**
	 * Decides between the routes and the pin, seen from the origin, and lays the hull out around
	 * it, cut to the range.
	 *
	 * @param range      the area around the origin within which the hull is drawn, in meters east
	 *                   and north of it
	 * @param elevations source of the ground elevation, null to leave it unknown
	 */
	void build(double originLatitude, double originLongitude, Geometry range,
	           TerrainWireframe.ElevationSource elevations) {
		double metersPerDegreeLongitude =
				METERS_PER_DEGREE * Math.cos(Math.toRadians(originLatitude));
		double[] east = new double[latitudes.length];
		double[] north = new double[latitudes.length];
		Coordinate[] ring = new Coordinate[latitudes.length];
		for (int index = 0; index < latitudes.length; index++) {
			east[index] = (longitudes[index] - originLongitude) * metersPerDegreeLongitude;
			north[index] = (latitudes[index] - originLatitude) * METERS_PER_DEGREE;
			ring[index] = new Coordinate(east[index], north[index]);
		}

		showingRoutes = isShowingRoutes(east, north, showingRoutes);
		fill = null;
		outline.clear();

		// From inside, the fill would tint all the ground in view, so only the outline is drawn.
		boolean filled = !containsOrigin(east, north);
		Geometry fillShape = null;
		Geometry outlineShape;
		try {
			Polygon hull = GEOMETRY_FACTORY.createPolygon(ring);
			if (filled) {
				fillShape = hull.intersection(range);
			}
			outlineShape = hull.getExteriorRing().intersection(range);
		} catch (IllegalArgumentException | TopologyException e) {
			// A degenerate hull, left undrawn.
			return;
		}

		// Both the hull and the range are convex, so there is at most one piece of inside.
		if (fillShape instanceof Polygon && !fillShape.isEmpty()) {
			fill = GroundLine.along(((Polygon) fillShape).getExteriorRing().getCoordinates(),
					originLatitude, originLongitude, metersPerDegreeLongitude, elevations);
		}
		for (int index = 0; index < outlineShape.getNumGeometries(); index++) {
			Geometry part = outlineShape.getGeometryN(index);
			if (part instanceof LineString && !part.isEmpty()) {
				outline.add(GroundLine.along(part.getCoordinates(), originLatitude,
						originLongitude, metersPerDegreeLongitude, elevations));
			}
		}
	}

	/**
	 * Inside the hull, its routes always show. Outside, they show while the hull spans enough of
	 * the horizon to make them out, see ROUTES_MIN_DEGREES.
	 *
	 * @param east          the hull, as a ring, in meters east of the observer
	 * @param north         the hull, as a ring, in meters north of the observer
	 * @param showingRoutes whether the routes show so far
	 */
	static boolean isShowingRoutes(double[] east, double[] north, boolean showingRoutes) {
		if (containsOrigin(east, north)) {
			return true;
		}
		return horizontalSpreadDegrees(east, north)
				>= (showingRoutes ? PIN_MAX_DEGREES : ROUTES_MIN_DEGREES);
	}

	/**
	 * Casts a ray east from the origin and counts the edges it crosses.
	 */
	static boolean containsOrigin(double[] east, double[] north) {
		boolean inside = false;
		for (int index = 0, previous = east.length - 1; index < east.length;
		     previous = index++) {
			if ((north[index] > 0) != (north[previous] > 0)) {
				double crossing = east[previous] - north[previous]
						* (east[index] - east[previous]) / (north[index] - north[previous]);
				if (crossing > 0) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	/**
	 * Angle between the leftmost and the rightmost point of a convex hull, seen from the origin
	 * outside of it. It is under 180 degrees, so the angles to the first point do not wrap around.
	 */
	static double horizontalSpreadDegrees(double[] east, double[] north) {
		double reference = Math.atan2(east[0], north[0]);
		double min = 0;
		double max = 0;
		for (int index = 1; index < east.length; index++) {
			double angle = Math.atan2(east[index], north[index]) - reference;
			angle = Math.atan2(Math.sin(angle), Math.cos(angle));
			min = Math.min(min, angle);
			max = Math.max(max, angle);
		}
		return Math.toDegrees(max - min);
	}

	/**
	 * A line on the ground, in meters east and north of an origin, with the ground elevation above
	 * sea level, NaN where unknown.
	 */
	static final class GroundLine {
		double[] east = new double[16];
		double[] north = new double[16];
		double[] elevation = new double[16];
		int size;

		/**
		 * The line through the points, cut in steps growing with the distance from the origin,
		 * see STEP_PER_METER.
		 *
		 * @param elevations source of the ground elevation, null to leave it unknown
		 */
		static GroundLine along(Coordinate[] points, double originLatitude,
		                        double originLongitude, double metersPerDegreeLongitude,
		                        TerrainWireframe.ElevationSource elevations) {
			GroundLine line = new GroundLine();
			for (int index = 1; index < points.length; index++) {
				Coordinate from = points[index - 1];
				Coordinate to = points[index];
				double length = from.distance(to);
				double travelled = 0;
				while (travelled < length) {
					double share = travelled / length;
					double east = from.x + (to.x - from.x) * share;
					double north = from.y + (to.y - from.y) * share;
					line.add(east, north);
					travelled += Math.max(MIN_STEP_METERS, Math.hypot(east, north) * STEP_PER_METER);
				}
			}
			if (points.length > 0) {
				line.add(points[points.length - 1].x, points[points.length - 1].y);
			}

			for (int index = 0; index < line.size; index++) {
				line.elevation[index] = elevations == null ? Double.NaN
						: elevations.getElevation(
						originLatitude + line.north[index] / METERS_PER_DEGREE,
						originLongitude + line.east[index] / metersPerDegreeLongitude,
						Math.hypot(line.east[index], line.north[index]));
			}
			return line;
		}

		private void add(double east, double north) {
			if (size == this.east.length) {
				this.east = Arrays.copyOf(this.east, size * 2);
				this.north = Arrays.copyOf(this.north, size * 2);
				elevation = Arrays.copyOf(elevation, size * 2);
			}
			this.east[size] = east;
			this.north[size] = north;
			size++;
		}
	}
}
