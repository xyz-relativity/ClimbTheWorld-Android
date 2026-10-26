package com.climbtheworld.app.augmentedreality;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.GeoUtils;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.PointLocation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
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
 * ground, its outline and its fill both following the terrain. Up close, its routes show in it;
 * once it is too far to make them out, its pin stands in for them.
 */
public class ClimbingHull {
	// The routes take over from the pin once the hull comes this close, and give way to it again
	// beyond the farther distance, so a hull on the threshold does not flicker. Going by the
	// distance alone, neighbouring hulls switch together, whatever their shape and however they
	// are turned.
	static final double ROUTES_MAX_DISTANCE_METERS = 100;
	static final double PIN_MIN_DISTANCE_METERS = 150;
	// The outline is cut in steps growing with the distance, so it follows the terrain about as
	// closely all over the view.
	static final double MIN_STEP_METERS = 5;
	static final double STEP_PER_METER = 0.02;
	// The fill is a net of triangles draped over the terrain, see GroundMesh, cut in cells as
	// the outline is cut in steps, but at most this many cells, so that a wide area costs little
	// to draw.
	static final int MAX_FILL_CELLS = 1024;
	static final double METERS_PER_DEGREE = GeoUtils.EARTH_RADIUS_M * Math.PI / 180;
	static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

	public final String key;
	public final OsmEntity.EntityClimbingType type;
	// The relation as a POI, in the middle of the hull.
	public final GeoNode pin;
	// The hull, as a closed ring.
	private final double[] latitudes;
	private final double[] longitudes;
	// The nodes of the relation and of the relations in it, such as the routes.
	private final long[] nodeIds;
	private boolean showingRoutes;
	// As of the last build, around its origin.
	private GroundMesh fill;
	private final List<GroundLine> outline = new ArrayList<>();

	/**
	 * @param latitudes  the hull, as a closed ring
	 * @param longitudes the hull, as a closed ring
	 * @param nodeIds    the nodes of the relation and of the relations in it
	 */
	public ClimbingHull(String key, OsmEntity.EntityClimbingType type, GeoNode pin, double[] latitudes,
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
	 * @return the inside of the hull within the range, draped over the terrain, null when there
	 * is none
	 */
	GroundMesh getFill() {
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

		Geometry fillShape;
		Geometry outlineShape;
		try {
			Polygon hull = GEOMETRY_FACTORY.createPolygon(ring);
			fillShape = hull.intersection(range);
			outlineShape = hull.getExteriorRing().intersection(range);
		} catch (IllegalArgumentException | TopologyException e) {
			// A degenerate hull, left undrawn.
			return;
		}

		// Both the hull and the range are convex, so there is at most one piece of inside. From
		// inside, it is the ground around the observer, out to the hull's edges.
		if (fillShape instanceof Polygon && !fillShape.isEmpty()) {
			fill = GroundMesh.over((Polygon) fillShape, originLatitude, originLongitude,
					metersPerDegreeLongitude, elevations);
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
	 * Inside the hull, its routes always show. Outside, they show while the hull is close enough
	 * to make them out, see ROUTES_MAX_DISTANCE_METERS.
	 *
	 * @param east          the hull, as a ring, in meters east of the observer
	 * @param north         the hull, as a ring, in meters north of the observer
	 * @param showingRoutes whether the routes show so far
	 */
	static boolean isShowingRoutes(double[] east, double[] north, boolean showingRoutes) {
		if (containsOrigin(east, north)) {
			return true;
		}
		return distanceFromOrigin(east, north)
				<= (showingRoutes ? PIN_MIN_DISTANCE_METERS : ROUTES_MAX_DISTANCE_METERS);
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
	 * Distance from the origin to the nearest edge of a ring.
	 */
	static double distanceFromOrigin(double[] east, double[] north) {
		Coordinate origin = new Coordinate(0, 0);
		double nearest = Double.POSITIVE_INFINITY;
		for (int index = 1; index < east.length; index++) {
			nearest = Math.min(nearest, Distance.pointToSegment(origin,
					new Coordinate(east[index - 1], north[index - 1]),
					new Coordinate(east[index], north[index])));
		}
		return nearest;
	}

	/**
	 * The ground elevation at points in meters east and north of an origin, as a source gives it.
	 *
	 * @param elevations source of the ground elevation, null to leave it unknown
	 */
	private static void lookUpElevations(double[] east, double[] north, double[] elevation,
	                                     int size, double originLatitude, double originLongitude,
	                                     double metersPerDegreeLongitude,
	                                     TerrainWireframe.ElevationSource elevations) {
		for (int index = 0; index < size; index++) {
			elevation[index] = elevations == null ? Double.NaN
					: elevations.getElevation(originLatitude + north[index] / METERS_PER_DEGREE,
					originLongitude + east[index] / metersPerDegreeLongitude,
					Math.hypot(east[index], north[index]));
		}
	}

	/**
	 * A patch of ground, in meters east and north of an origin, with the ground elevation above
	 * sea level at each corner, NaN where unknown: a net of triangles that, drawn each where its
	 * corners are, drapes over the terrain.
	 */
	static final class GroundMesh {
		double[] east = new double[64];
		double[] north = new double[64];
		double[] elevation = new double[64];
		int size;
		// The corners of each triangle, three indices per triangle.
		int[] triangles = new int[192];
		int triangleCount;

		/**
		 * The net over a convex shape: square cells, cut to the shape along its edge, sized as
		 * the outline's steps are at the shape's distance, see STEP_PER_METER, but no more than
		 * MAX_FILL_CELLS of them.
		 *
		 * @param elevations source of the ground elevation, null to leave it unknown
		 * @return null when the shape is too thin to hold a triangle
		 */
		static GroundMesh over(Polygon shape, double originLatitude, double originLongitude,
		                       double metersPerDegreeLongitude,
		                       TerrainWireframe.ElevationSource elevations) {
			GroundMesh mesh = new GroundMesh();
			Coordinate[] ring = shape.getExteriorRing().getCoordinates();
			// Which side of the ring's edges the inside is on, see clipToRing.
			double inside = Orientation.isCCW(ring) ? 1 : -1;
			Envelope bounds = shape.getEnvelopeInternal();
			double cell = cellSize(bounds, ring);
			int columns = Math.max(1, (int) Math.ceil(bounds.getWidth() / cell));
			int rows = Math.max(1, (int) Math.ceil(bounds.getHeight() / cell));
			// The corners of the cells wholly inside the shape, as indices, -1 until added.
			int[] corners = new int[(columns + 1) * (rows + 1)];
			Arrays.fill(corners, -1);
			Coordinate[] cellRing = new Coordinate[5];
			for (int row = 0; row < rows; row++) {
				for (int column = 0; column < columns; column++) {
					double west = gridLine(bounds.getMinX(), bounds.getMaxX(), cell, column);
					double east = gridLine(bounds.getMinX(), bounds.getMaxX(), cell, column + 1);
					double south = gridLine(bounds.getMinY(), bounds.getMaxY(), cell, row);
					double north = gridLine(bounds.getMinY(), bounds.getMaxY(), cell, row + 1);
					cellRing[0] = new Coordinate(west, south);
					cellRing[1] = new Coordinate(west, north);
					cellRing[2] = new Coordinate(east, north);
					cellRing[3] = new Coordinate(east, south);
					cellRing[4] = cellRing[0];
					boolean whole = true;
					for (int corner = 0; corner < 4 && whole; corner++) {
						whole = PointLocation.isInRing(cellRing[corner], ring);
					}
					if (whole) {
						// The shape is convex, so the whole cell is in it.
						int southWest = mesh.corner(corners, columns, column, row, west, south);
						int northWest = mesh.corner(corners, columns, column, row + 1, west, north);
						int northEast = mesh.corner(corners, columns, column + 1, row + 1, east,
								north);
						int southEast = mesh.corner(corners, columns, column + 1, row, east, south);
						mesh.addTriangle(southWest, northWest, northEast);
						mesh.addTriangle(southWest, northEast, southEast);
						continue;
					}
					// Both the cell and the shape are convex, so their overlap is one convex
					// piece, or nothing, drawn as a fan from its first corner.
					List<Coordinate> piece = clipToRing(Arrays.asList(cellRing).subList(0, 4),
							ring, inside);
					if (piece.size() < 3) {
						continue;
					}
					int first = mesh.add(piece.get(0).x, piece.get(0).y);
					int previous = mesh.add(piece.get(1).x, piece.get(1).y);
					for (int index = 2; index < piece.size(); index++) {
						int next = mesh.add(piece.get(index).x, piece.get(index).y);
						mesh.addTriangle(first, previous, next);
						previous = next;
					}
				}
			}
			if (mesh.triangleCount == 0) {
				return null;
			}
			lookUpElevations(mesh.east, mesh.north, mesh.elevation, mesh.size, originLatitude,
					originLongitude, metersPerDegreeLongitude, elevations);
			return mesh;
		}

		/**
		 * How wide the cells are over a shape: as the outline's steps at its distance, or wider,
		 * so that there are at most MAX_FILL_CELLS of them.
		 */
		static double cellSize(Envelope bounds, Coordinate[] ring) {
			Coordinate origin = new Coordinate(0, 0);
			double nearest = 0;
			if (!PointLocation.isInRing(origin, ring)) {
				nearest = Double.POSITIVE_INFINITY;
				for (int index = 1; index < ring.length; index++) {
					nearest = Math.min(nearest,
							Distance.pointToSegment(origin, ring[index - 1], ring[index]));
				}
			}
			double step = Math.max(MIN_STEP_METERS, nearest * STEP_PER_METER);
			return Math.max(step, Math.sqrt(bounds.getWidth() * bounds.getHeight()
					/ MAX_FILL_CELLS));
		}

		/**
		 * Cuts a convex polygon to a convex ring, an edge of the ring at a time
		 * (Sutherland-Hodgman): the corners cut away are replaced by the points where the sides
		 * cross the edge.
		 *
		 * @param ring   closed, either way round
		 * @param inside 1 when the inside of the ring is on the left of its edges, going round
		 *               it, -1 when on the right
		 * @return the corners left, not closed; fewer than 3 when nothing is left
		 */
		static List<Coordinate> clipToRing(List<Coordinate> polygon, Coordinate[] ring,
		                                   double inside) {
			List<Coordinate> clipped = polygon;
			for (int edge = 1; edge < ring.length && clipped.size() >= 3; edge++) {
				Coordinate from = ring[edge - 1];
				Coordinate to = ring[edge];
				List<Coordinate> input = clipped;
				clipped = new ArrayList<>(input.size() + 2);
				for (int index = 0; index < input.size(); index++) {
					Coordinate previous = input.get((index + input.size() - 1) % input.size());
					Coordinate current = input.get(index);
					double previousSide = leftOf(from, to, previous) * inside;
					double currentSide = leftOf(from, to, current) * inside;
					if ((previousSide >= 0) != (currentSide >= 0)) {
						double share = previousSide / (previousSide - currentSide);
						clipped.add(new Coordinate(previous.x + (current.x - previous.x) * share,
								previous.y + (current.y - previous.y) * share));
					}
					if (currentSide >= 0) {
						clipped.add(current);
					}
				}
			}
			return clipped;
		}

		/**
		 * @return positive where the point is on the left of the line from one point to another,
		 * looking along it, negative on the right, zero on it
		 */
		private static double leftOf(Coordinate from, Coordinate to, Coordinate point) {
			return (to.x - from.x) * (point.y - from.y) - (to.y - from.y) * (point.x - from.x);
		}

		/**
		 * Where a grid line is, the last one on the far side of the bounds.
		 */
		private static double gridLine(double from, double to, double cell, int line) {
			return Math.min(from + line * cell, to);
		}

		/**
		 * The index of a corner of the grid, added the first time it is asked for.
		 */
		private int corner(int[] corners, int columns, int column, int row, double east,
		                   double north) {
			int key = row * (columns + 1) + column;
			if (corners[key] < 0) {
				corners[key] = add(east, north);
			}
			return corners[key];
		}

		private int add(double east, double north) {
			if (size == this.east.length) {
				this.east = Arrays.copyOf(this.east, size * 2);
				this.north = Arrays.copyOf(this.north, size * 2);
				elevation = Arrays.copyOf(elevation, size * 2);
			}
			this.east[size] = east;
			this.north[size] = north;
			return size++;
		}

		private void addTriangle(int a, int b, int c) {
			if (triangleCount * 3 + 3 > triangles.length) {
				triangles = Arrays.copyOf(triangles, triangles.length * 2);
			}
			triangles[triangleCount * 3] = a;
			triangles[triangleCount * 3 + 1] = b;
			triangles[triangleCount * 3 + 2] = c;
			triangleCount++;
		}
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
			lookUpElevations(line.east, line.north, line.elevation, line.size, originLatitude,
					originLongitude, metersPerDegreeLongitude, elevations);
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
