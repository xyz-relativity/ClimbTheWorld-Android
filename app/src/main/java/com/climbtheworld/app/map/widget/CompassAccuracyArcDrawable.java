package com.climbtheworld.app.map.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An arc around the rim of the compass button, either side of where its needle points, as wide
 * as the heading may be off, its ends squared off by a line from each in almost to the compass,
 * with its middle marked and a tick across it every 15 degrees from there. The arc turns from
 * green at its middle to red a right angle away, so the further it reaches, the redder its ends;
 * it stops there, so a heading that may be off by more shows as a red half circle. The
 * foreground of the map's compass button, so that it turns with the button, and over the arrow
 * on the environment compass showing which way the user faces.
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
	// The ends, |===|: a line from each, as wide as the middle, its far side along the radius
	// where the arc ends, in to just short of the compass icon at 43% of the radius, its dark
	// edge included.
	private static final float END_INNER = 0.49f;
	private static final float END_WIDTH = MIDDLE_WIDTH;
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
	private final RectF outerOval = new RectF();
	private final RectF innerOval = new RectF();
	private final Path outline = new Path();
	private float centerX;
	private float centerY;
	private int halfWidthDegrees;

	public CompassAccuracyArcDrawable() {
		// The arc is filled in, and its casing filled in and grown by its stroke on all sides; a
		// line, like the middle, always shows as a stroke.
		arcPaint.setStyle(Paint.Style.FILL);
		casingPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		// Sharp corners on the casing, so it keeps the shape of the ends.
		casingPaint.setStrokeJoin(Paint.Join.MITER);
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
		float outerRadius = radius * (ARC_RADIUS + ARC_WIDTH / 2);
		float innerRadius = radius * (ARC_RADIUS - ARC_WIDTH / 2);
		outerOval.set(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius,
				centerY + outerRadius);
		innerOval.set(centerX - innerRadius, centerY - innerRadius, centerX + innerRadius,
				centerY + innerRadius);
		int reach = Math.min(MAX_HALF_WIDTH_DEGREES, halfWidthDegrees);
		// Clockwise along the outer edge and back along the inner edge, then the line at either
		// end. Arcs are measured clockwise from the right, so up is at -90 degrees.
		outline.reset();
		outline.arcTo(outerOval, -90 - reach, 2 * reach, true);
		outline.arcTo(innerOval, -90 + reach, -2 * reach, false);
		outline.close();
		addEnd(outline, reach, radius);
		addEnd(outline, -reach, radius);
		casingPaint.setStrokeWidth(radius * 2 * CASING_WIDTH);
		canvas.drawPath(outline, casingPaint);
		canvas.drawPath(outline, arcPaint);

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

	/**
	 * The line at an end of the arc, from its middle in towards the compass. Clockwise like the
	 * arc, so that the two fill as one.
	 *
	 * @param degrees where the arc ends, clockwise from its middle, negative for anticlockwise
	 */
	void addEnd(Path path, int degrees, float radius) {
		float sin = (float) Math.sin(Math.toRadians(degrees));
		float cos = (float) Math.cos(Math.toRadians(degrees));
		float inner = radius * END_INNER;
		float outer = radius * ARC_RADIUS;
		// How far across from the radius where the arc ends its anticlockwise and its clockwise
		// sides are: that radius is its side away from the middle of the arc.
		float from = degrees > 0 ? -radius * END_WIDTH : 0;
		float to = from + radius * END_WIDTH;
		path.moveTo(centerX + inner * sin + from * cos, centerY - inner * cos + from * sin);
		path.lineTo(centerX + outer * sin + from * cos, centerY - outer * cos + from * sin);
		path.lineTo(centerX + outer * sin + to * cos, centerY - outer * cos + to * sin);
		path.lineTo(centerX + inner * sin + to * cos, centerY - inner * cos + to * sin);
		path.close();
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
