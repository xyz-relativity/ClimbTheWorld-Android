package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Vector2d;

/**
 * Draws the {@link ClimbingHulls} on the ground as the virtual camera sees them, in the colours of
 * the map. It sits in the AR container, which is centred on the camera view and turned
 * with the device roll.
 */
public class ClimbingHullView extends View {
	private final HullProjection projection = new HullProjection();
	private final Path fillPath = new Path();
	private final Paint areaFillPaint;
	private final Paint cragFillPaint;
	private final Paint areaLinePaint;
	private final Paint cragLinePaint;
	private final Paint casingPaint;
	private ClimbingHulls hulls;
	private VirtualCamera camera;
	private double eyeElevation;
	private double eyeHeight;
	private Vector2d viewSize;

	public ClimbingHullView(Context context, AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		areaFillPaint = fillPaint(DisplayableGeoNode.AREA_HULL_COLOR);
		cragFillPaint = fillPaint(DisplayableGeoNode.CRAG_HULL_COLOR);
		areaLinePaint = linePaint(ColorUtils.setAlphaComponent(DisplayableGeoNode.AREA_HULL_COLOR,
				DisplayableGeoNode.LINE_ALPHA), DisplayableGeoNode.LINE_WIDTH_DP * density);
		cragLinePaint = linePaint(ColorUtils.setAlphaComponent(DisplayableGeoNode.CRAG_HULL_COLOR,
				DisplayableGeoNode.LINE_ALPHA), DisplayableGeoNode.LINE_WIDTH_DP * density);
		casingPaint = linePaint(DisplayableGeoNode.LINE_CASING_COLOR,
				DisplayableGeoNode.LINE_CASING_WIDTH_DP * density);
	}

	private static Paint fillPaint(int color) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(color);
		paint.setStyle(Paint.Style.FILL);
		return paint;
	}

	static Paint linePaint(int color, float width) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(color);
		paint.setStrokeWidth(width);
		// Round ends join the segments of a line smoothly.
		paint.setStrokeCap(Paint.Cap.ROUND);
		return paint;
	}

	/**
	 * Draws the hulls as the camera sees them, from then on.
	 *
	 * @param camera       where the observer is and looks
	 * @param eyeElevation elevation of the observer's eye above sea level, NaN when unknown
	 * @param eyeHeight    height of the observer's eye above the ground, for where the
	 *                     elevations are unknown
	 * @param viewSize     size in pixel of the camera view, which the angle of view spans
	 */
	public void show(ClimbingHulls hulls, VirtualCamera camera, double eyeElevation,
	                 double eyeHeight, Vector2d viewSize) {
		this.hulls = hulls;
		this.camera = camera;
		this.eyeElevation = eyeElevation;
		this.eyeHeight = eyeHeight;
		this.viewSize = viewSize;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (hulls == null || hulls.getHulls().isEmpty() || viewSize.x == 0
				|| viewSize.y == 0) {
			return;
		}

		projection.setCamera(camera.degAzimuth, -camera.degPitch,
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.x, viewSize.x),
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.y, viewSize.y),
				getWidth() / 2.0, getHeight() / 2.0);
		double observerEast = hulls.getEastOfOrigin(camera.decimalLongitude);
		double observerNorth = hulls.getNorthOfOrigin(camera.decimalLatitude);

		for (ClimbingHull hull : hulls.getHulls()) {
			boolean crag = hull.type == GeoNode.NodeTypes.crag;
			ClimbingHull.GroundLine fill = hull.getFill();
			if (fill != null) {
				int corners = projection.projectPolygon(fill, observerEast, observerNorth,
						eyeElevation, eyeHeight);
				if (corners > 2) {
					float[] polygon = projection.getPolygon();
					fillPath.rewind();
					fillPath.moveTo(polygon[0], polygon[1]);
					for (int corner = 1; corner < corners; corner++) {
						fillPath.lineTo(polygon[corner * 2], polygon[corner * 2 + 1]);
					}
					fillPath.close();
					canvas.drawPath(fillPath, crag ? cragFillPaint : areaFillPaint);
				}
			}

			projection.clearLines();
			for (ClimbingHull.GroundLine line : hull.getOutline()) {
				projection.projectLine(line, observerEast, observerNorth, eyeElevation, eyeHeight);
			}
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(), casingPaint);
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(),
					crag ? cragLinePaint : areaLinePaint);
		}
	}
}
