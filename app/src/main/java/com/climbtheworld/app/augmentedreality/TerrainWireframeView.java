package com.climbtheworld.app.augmentedreality;

import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RAY_COUNT;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RING_COUNT;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.RING_LINE_STEP;
import static com.climbtheworld.app.augmentedreality.TerrainWireframe.sampleIndex;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;

import java.util.Arrays;

/**
 * Draws a {@link TerrainWireframe} as the virtual camera sees it. It sits in the AR container,
 * which is centred on the camera view and turned with the device roll.
 */
public class TerrainWireframeView extends View {
	// Line opacity by distance, from close to far, so far terrain stays in the background.
	private static final int[] BAND_ALPHAS = {0x90, 0x70, 0x50, 0x38};
	private static final int SKYLINE_ALPHA = 0xc0;
	private static final float LINE_WIDTH_DP = 1.5f;
	private static final float SKYLINE_WIDTH_DP = 3;
	private final Paint[] bandPaints = new Paint[BAND_ALPHAS.length];
	private final Paint skylinePaint;
	private final float[][] bandLines = new float[BAND_ALPHAS.length][];
	private final int[] bandLineCounts = new int[BAND_ALPHAS.length];
	private final float[] skylineLines = new float[RAY_COUNT * 4];
	private TerrainWireframe wireframe;
	private double eyeElevation;
	private Vector2d viewSize;

	public TerrainWireframeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;

		int[] bandSegments = new int[BAND_ALPHAS.length];
		for (int ring = 0; ring < RING_COUNT; ring++) {
			// The ray segments ending on the ring, and the ring line.
			if (ring > 0) {
				bandSegments[band(ring)] += RAY_COUNT;
			}
			if (ring % RING_LINE_STEP == 0) {
				bandSegments[band(ring)] += RAY_COUNT;
			}
		}
		for (int band = 0; band < BAND_ALPHAS.length; band++) {
			bandPaints[band] = linePaint(BAND_ALPHAS[band], LINE_WIDTH_DP * density);
			bandLines[band] = new float[bandSegments[band] * 4];
		}
		skylinePaint = linePaint(SKYLINE_ALPHA, SKYLINE_WIDTH_DP * density);
		skylinePaint.setStrokeCap(Paint.Cap.ROUND);
	}

	private static Paint linePaint(int alpha, float width) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(Color.argb(alpha, 0xff, 0xff, 0xff));
		paint.setStrokeWidth(width);
		return paint;
	}

	private static int band(int ring) {
		return ring * BAND_ALPHAS.length / RING_COUNT;
	}

	/**
	 * Draws the wireframe as {@link Globals#virtualCamera} sees it, from then on.
	 *
	 * @param eyeElevation elevation of the observer's eye above sea level, in meters
	 * @param viewSize     size in pixel of the camera view, which the angle of view spans
	 */
	public void show(TerrainWireframe wireframe, double eyeElevation, Vector2d viewSize) {
		this.wireframe = wireframe;
		this.eyeElevation = eyeElevation;
		this.viewSize = viewSize;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (wireframe == null || viewSize.x == 0 || viewSize.y == 0) {
			return;
		}

		VirtualCamera camera = Globals.virtualCamera;
		wireframe.project(camera.decimalLatitude, camera.decimalLongitude, eyeElevation,
				camera.degAzimuth, -camera.degPitch,
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.x, viewSize.x),
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.y, viewSize.y),
				getWidth() / 2.0, getHeight() / 2.0);

		Arrays.fill(bandLineCounts, 0);
		int skylineCount = 0;
		for (int ray = 0; ray < RAY_COUNT; ray++) {
			int nextRay = (ray + 1) % RAY_COUNT;
			for (int ring = 0; ring < RING_COUNT; ring++) {
				int band = band(ring);
				int index = sampleIndex(ray, ring);
				if (ring > 0) {
					bandLineCounts[band] = addSegment(bandLines[band], bandLineCounts[band],
							index - 1, index);
				}
				if (ring % RING_LINE_STEP == 0) {
					bandLineCounts[band] = addSegment(bandLines[band], bandLineCounts[band],
							index, sampleIndex(nextRay, ring));
				}
			}

			int skyline = wireframe.getSkyline(ray);
			int nextSkyline = wireframe.getSkyline(nextRay);
			if (skyline >= 0 && nextSkyline >= 0) {
				skylineCount = addSegment(skylineLines, skylineCount, skyline, nextSkyline);
			}
		}

		for (int band = 0; band < BAND_ALPHAS.length; band++) {
			canvas.drawLines(bandLines[band], 0, bandLineCounts[band], bandPaints[band]);
		}
		canvas.drawLines(skylineLines, 0, skylineCount, skylinePaint);
	}

	/**
	 * Adds the segment between two samples, when both can be drawn and it crosses the view.
	 *
	 * @return the new count of coordinates in the lines
	 */
	private int addSegment(float[] lines, int count, int from, int to) {
		if (!wireframe.isDrawable(from) || !wireframe.isDrawable(to)) {
			return count;
		}
		float fromX = wireframe.getScreenX(from);
		float fromY = wireframe.getScreenY(from);
		float toX = wireframe.getScreenX(to);
		float toY = wireframe.getScreenY(to);
		if ((fromX < 0 && toX < 0) || (fromX > getWidth() && toX > getWidth())
				|| (fromY < 0 && toY < 0) || (fromY > getHeight() && toY > getHeight())) {
			return count;
		}
		lines[count] = fromX;
		lines[count + 1] = fromY;
		lines[count + 2] = toX;
		lines[count + 3] = toY;
		return count + 4;
	}
}
