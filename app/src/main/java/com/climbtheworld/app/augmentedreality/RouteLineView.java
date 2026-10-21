package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.utils.Vector2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Draws {@link RouteLines} on the terrain as the virtual camera sees them, each in the colour of
 * its grade, styled as the outlines of the {@link ClimbingHullView} under it. It sits in the AR
 * container, which is centred on the camera view and turned with the device roll.
 */
public class RouteLineView extends View {
	private final HullProjection projection = new HullProjection();
	private final Paint linePaint;
	private final Paint casingPaint;
	private final List<RouteLine> shown = new ArrayList<>();
	private RouteLines routeLines;
	private VirtualCamera camera;
	private double eyeElevation;
	private double eyeHeight;
	private Vector2d viewSize;

	public RouteLineView(Context context, AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		linePaint = ClimbingHullView.linePaint(0, DisplayableGeoNode.LINE_WIDTH_DP * density);
		casingPaint = ClimbingHullView.linePaint(DisplayableGeoNode.LINE_CASING_COLOR,
				DisplayableGeoNode.LINE_CASING_WIDTH_DP * density);
	}

	/**
	 * Draws the routes as the camera sees them, from then on.
	 *
	 * @param shown        the routes to draw, out of the routeLines
	 * @param camera       where the observer is and looks
	 * @param eyeElevation elevation of the observer's eye above sea level, NaN when unknown
	 * @param eyeHeight    height of the observer's eye above the ground, for where the
	 *                     elevations are unknown
	 * @param viewSize     size in pixel of the camera view, which the angle of view spans
	 */
	public void show(RouteLines routeLines, Collection<RouteLine> shown, VirtualCamera camera,
	                 double eyeElevation, double eyeHeight, Vector2d viewSize) {
		this.routeLines = routeLines;
		this.camera = camera;
		this.shown.clear();
		this.shown.addAll(shown);
		this.eyeElevation = eyeElevation;
		this.eyeHeight = eyeHeight;
		this.viewSize = viewSize;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (shown.isEmpty() || viewSize.x == 0 || viewSize.y == 0) {
			return;
		}

		projection.setCamera(camera.degAzimuth, -camera.degPitch,
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.x, viewSize.x),
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.y, viewSize.y),
				getWidth() / 2.0, getHeight() / 2.0);
		double observerEast = routeLines.getEastOfOrigin(camera.decimalLongitude);
		double observerNorth = routeLines.getNorthOfOrigin(camera.decimalLatitude);

		for (RouteLine route : shown) {
			ClimbingHull.GroundLine line = route.getLine();
			if (line == null) {
				continue;
			}
			projection.clearLines();
			projection.projectLine(line, observerEast, observerNorth, eyeElevation, eyeHeight);
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(), casingPaint);
			linePaint.setColor(ColorUtils.setAlphaComponent(route.color,
					DisplayableGeoNode.LINE_ALPHA));
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(), linePaint);
		}
	}
}
