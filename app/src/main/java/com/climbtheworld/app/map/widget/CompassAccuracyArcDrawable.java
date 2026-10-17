package com.climbtheworld.app.map.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An arc around the rim of the compass button, either side of where its needle points, as wide
 * as the heading may be off, with its middle marked and a tick across it every 15 degrees from
 * there. The arc turns from green at its middle to red a right angle away, so the further it
 * reaches, the redder its ends; it stops there, so a heading that may be off by more shows as a
 * red half circle. The foreground of the map's compass button, so that it turns with the
 * button, and over the arrow on the environment compass showing which way the user faces.
 */
public class CompassAccuracyArcDrawable extends Drawable {
	// As fractions of the button's radius: in the ring between the compass icon and the edge.
	private static final float ARC_RADIUS = 0.71f;
	private static final float ARC_WIDTH = 0.14f;
	// The dark edge either side of the arc, so it shows on any button colour, like the black
	// outline of the compass icon.
	private static final float CASING_WIDTH = 0.04f;
	// How far either side of the middle the arc reaches at most.
	private static final int MAX_HALF_WIDTH_DEGREES = 90;
	// The ticks, dark lines across the arc that show as it grows over them: every 15 degrees
	// across its outer half, and every 45 degrees across all of it.
	private static final int TICK_STEP_DEGREES = 15;
	private static final int MAJOR_TICK_STEP_DEGREES = 45;
	private static final float TICK_INNER = ARC_RADIUS;
	private static final float MAJOR_TICK_INNER = ARC_RADIUS - ARC_WIDTH / 2;
	private static final float TICK_OUTER = ARC_RADIUS + ARC_WIDTH / 2;
	private static final float TICK_WIDTH = 0.025f;
	// The middle, across the whole arc like the longer ticks, but white.
	private static final float MIDDLE_INNER = ARC_RADIUS - ARC_WIDTH / 2;
	private static final float MIDDLE_OUTER = ARC_RADIUS + ARC_WIDTH / 2;
	private static final float MIDDLE_WIDTH = 0.05f;
	private static final int MARK_COLOR = 0xffffffff;
	private static final int CASING_COLOR = 0xcc000000;

	// Clockwise from the middle of the arc, and back the same way on the other side: green while
	// the heading is good to a few degrees, then yellow and orange, and red once it may be off by
	// a right angle or more.
	private static final float[] GRADIENT_DEGREES = {0, 10, 30, 60, 90, 270, 300, 330, 350, 360};
	private static final int[] GRADIENT_COLORS = {0xff4caf50, 0xff4caf50, 0xffffeb3b, 0xffff9800,
			0xfff44336, 0xfff44336, 0xffff9800, 0xffffeb3b, 0xff4caf50, 0xff4caf50};

	private final Paint arcPaint = createPaint(MARK_COLOR);
	private final Paint casingPaint = createPaint(CASING_COLOR);
	private final Paint markPaint = createPaint(MARK_COLOR);
	private final Paint tickPaint = createPaint(CASING_COLOR);
	private final RectF oval = new RectF();
	private float centerX;
	private float centerY;
	private int halfWidthDegrees;

	public CompassAccuracyArcDrawable() {
		// Square ends, so the ticks stay within the arc.
		tickPaint.setStrokeCap(Paint.Cap.BUTT);
	}

	private static Paint createPaint(int color) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setColor(color);
		return paint;
	}

	/**
	 * @param halfWidthDegrees how far either side of the needle the arc reaches, 0 for no arc
	 *                         and at most a right angle
	 */
	public void setHalfWidthDegrees(int halfWidthDegrees) {
		if (this.halfWidthDegrees != halfWidthDegrees) {
			this.halfWidthDegrees = halfWidthDegrees;
			invalidateSelf();
		}
	}

	int getHalfWidthDegrees() {
		return halfWidthDegrees;
	}

	@Override
	protected void onBoundsChange(@NonNull Rect bounds) {
		float[] positions = new float[GRADIENT_DEGREES.length];
		for (int stop = 0; stop < positions.length; stop++) {
			positions[stop] = GRADIENT_DEGREES[stop] / 360;
		}
		SweepGradient gradient = new SweepGradient(bounds.exactCenterX(), bounds.exactCenterY(),
				GRADIENT_COLORS, positions);
		// A sweep starts from the right, so it is turned back a quarter to start from the top.
		Matrix fromTop = new Matrix();
		fromTop.setRotate(-90, bounds.exactCenterX(), bounds.exactCenterY());
		gradient.setLocalMatrix(fromTop);
		arcPaint.setShader(gradient);
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (halfWidthDegrees <= 0) {
			return;
		}
		Rect bounds = getBounds();
		float radius = Math.min(bounds.width(), bounds.height()) / 2f;
		centerX = bounds.exactCenterX();
		centerY = bounds.exactCenterY();
		float arcRadius = radius * ARC_RADIUS;
		oval.set(centerX - arcRadius, centerY - arcRadius, centerX + arcRadius,
				centerY + arcRadius);
		arcPaint.setStrokeWidth(radius * ARC_WIDTH);
		casingPaint.setStrokeWidth(radius * (ARC_WIDTH + 2 * CASING_WIDTH));
		// Arcs are measured clockwise from the right, so up is at -90 degrees.
		int reach = Math.min(MAX_HALF_WIDTH_DEGREES, halfWidthDegrees);
		int sweep = 2 * reach;
		float start = -90 - sweep / 2f;
		canvas.drawArc(oval, start, sweep, false, casingPaint);
		canvas.drawArc(oval, start, sweep, false, arcPaint);

		tickPaint.setStrokeWidth(radius * TICK_WIDTH);
		for (int degrees = TICK_STEP_DEGREES; degrees <= reach; degrees += TICK_STEP_DEGREES) {
			float inner = degrees % MAJOR_TICK_STEP_DEGREES == 0 ? MAJOR_TICK_INNER : TICK_INNER;
			drawRadius(canvas, degrees, radius * inner, radius * TICK_OUTER, tickPaint);
			drawRadius(canvas, -degrees, radius * inner, radius * TICK_OUTER, tickPaint);
		}

		// The middle, a white line with a dark edge all round.
		casingPaint.setStrokeWidth(radius * (MIDDLE_WIDTH + 2 * CASING_WIDTH));
		drawRadius(canvas, 0, radius * MIDDLE_INNER, radius * MIDDLE_OUTER, casingPaint);
		markPaint.setStrokeWidth(radius * MIDDLE_WIDTH);
		drawRadius(canvas, 0, radius * MIDDLE_INNER, radius * MIDDLE_OUTER, markPaint);
	}

	/**
	 * A line along a radius, between two distances from the centre.
	 *
	 * @param degrees clockwise from the middle of the arc, negative for anticlockwise
	 */
	private void drawRadius(Canvas canvas, int degrees, float from, float to, Paint paint) {
		float sin = (float) Math.sin(Math.toRadians(degrees));
		float cos = (float) Math.cos(Math.toRadians(degrees));
		// The y axis points down, so up is minus the cosine.
		canvas.drawLine(centerX + from * sin, centerY - from * cos, centerX + to * sin,
				centerY - to * cos, paint);
	}

	@Override
	public void setAlpha(int alpha) {
		arcPaint.setAlpha(alpha);
		markPaint.setAlpha(alpha);
		casingPaint.setAlpha(Color.alpha(CASING_COLOR) * alpha / 255);
		tickPaint.setAlpha(Color.alpha(CASING_COLOR) * alpha / 255);
		invalidateSelf();
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter) {
		arcPaint.setColorFilter(colorFilter);
		markPaint.setColorFilter(colorFilter);
		casingPaint.setColorFilter(colorFilter);
		tickPaint.setColorFilter(colorFilter);
		invalidateSelf();
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
