package com.climbtheworld.app.navigate.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;

/**
 * How to calibrate the compass, after the animation on Google Maps' help: a phone drawing an
 * infinity sign in the air and turning every way as it goes, see CompassCalibrationMotion, with
 * an arrow ahead of it along the dotted sign. Above them, the user's arrow from the map, its beam
 * narrowing as the beam on the map does once the compass is surer.
 */
public class CompassCalibrationView extends View {
	private static final long LOOP_MILLIS = 3200;
	// The beam narrows over this many loops, then starts again.
	private static final int LOOPS_PER_CYCLE = 2;
	private static final float HEIGHT_PER_WIDTH = 0.85f;
	private static final float MAX_HEIGHT_DP = 280;
	// So that the dialog keeps room for its text and button in landscape.
	private static final float MAX_SCREEN_HEIGHT = 0.4f;

	// Sizes and places, as fractions of the view's height unless said otherwise.
	// The user's arrow, its beam as large around it as on the map.
	private static final float OBSERVER_SIZE = 0.15f;
	private static final float OBSERVER_TOP_MARGIN = 0.03f;
	// Half the width of the beam at the start of a cycle and at its end. It fades in over the
	// start of a cycle, so as not to jump back wide.
	private static final float BEAM_WIDEST_DEGREES = 90;
	private static final float BEAM_NARROWEST_DEGREES = 8;
	private static final float BEAM_FADE_IN = 0.08f;
	private static final float SIGN_CENTER_Y = 0.66f;
	private static final float SIGN_HALF_WIDTH = 0.42f;
	private static final int SIGN_SAMPLES = 360;
	private static final float SIGN_DOT_DP = 2.2f;
	private static final float SIGN_DOT_SPACING_DP = 6;
	// How far ahead of the phone the arrow reaches, as a part of a loop.
	private static final float ARROW_AHEAD = 0.2f;
	private static final float ARROW_WIDTH_DP = 3;
	private static final float ARROW_HEAD = 0.05f;
	private static final float PHONE_HEIGHT = 0.34f;

	// The phone, in halves of its height, x to its right, y to its top and z out of its screen.
	private static final double HALF_WIDTH = 0.5;
	private static final double HALF_THICKNESS = 0.09;
	private static final double CORNER_RADIUS = 0.12;
	private static final double SCREEN_HALF_WIDTH = 0.43;
	private static final double SCREEN_HALF_HEIGHT = 0.9;
	// Far enough that the phone hardly distorts, close enough that its near side shows nearer.
	private static final double EYE_DISTANCE = 8;
	// From the top left, in front, like the light of the dialog's own shadows.
	private static final double[] LIGHT = normalized(-0.35, 0.55, 0.75);
	private static final double AMBIENT = 0.45;
	private static final double DIFFUSE = 0.65;
	// The glare across the screen, as positions along its diagonal, brightest at the middle one.
	private static final float[] GLARE_POSITIONS = {0, 0.3f, 0.42f, 0.56f, 1};
	private static final double GLARE = 2.4;

	private static final double[][] CORNERS = new double[8][];
	private static final double[][][] SIDES = {
			{{HALF_WIDTH, -1, -HALF_THICKNESS}, {HALF_WIDTH, 1, -HALF_THICKNESS},
					{HALF_WIDTH, 1, HALF_THICKNESS}, {HALF_WIDTH, -1, HALF_THICKNESS}},
			{{-HALF_WIDTH, -1, -HALF_THICKNESS}, {-HALF_WIDTH, 1, -HALF_THICKNESS},
					{-HALF_WIDTH, 1, HALF_THICKNESS}, {-HALF_WIDTH, -1, HALF_THICKNESS}},
			{{-HALF_WIDTH, 1, -HALF_THICKNESS}, {HALF_WIDTH, 1, -HALF_THICKNESS},
					{HALF_WIDTH, 1, HALF_THICKNESS}, {-HALF_WIDTH, 1, HALF_THICKNESS}},
			{{-HALF_WIDTH, -1, -HALF_THICKNESS}, {HALF_WIDTH, -1, -HALF_THICKNESS},
					{HALF_WIDTH, -1, HALF_THICKNESS}, {-HALF_WIDTH, -1, HALF_THICKNESS}}};
	private static final double[][] SIDE_NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
	private static final double[] FRONT_NORMAL = {0, 0, 1};
	private static final double[] BACK_NORMAL = {0, 0, -1};
	private static final double[][] FRONT =
			roundedRectangle(HALF_WIDTH, 1, CORNER_RADIUS, HALF_THICKNESS);
	private static final double[][] SCREEN =
			roundedRectangle(SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, CORNER_RADIUS, HALF_THICKNESS);
	private static final double[][] FRONT_CAMERA = circle(0, 0.82, 0.035, HALF_THICKNESS);
	private static final double[][] BACK =
			roundedRectangle(HALF_WIDTH, 1, CORNER_RADIUS, -HALF_THICKNESS);
	// At the top left seen from behind.
	private static final double[][] LENS = circle(HALF_WIDTH * 0.45, 0.72, 0.11, -HALF_THICKNESS);
	private static final double[] GLARE_FROM = {-HALF_WIDTH, 1, HALF_THICKNESS};
	private static final double[] GLARE_TO = {HALF_WIDTH, -1, HALF_THICKNESS};

	private static final int SIGN_COLOR = 0xff64b5f6;
	private static final int ARROW_COLOR = 0xff1e88e5;
	private static final int BODY_COLOR = 0xff546e7a;
	private static final int SCREEN_COLOR = 0xff1a2329;
	// A light edge, so that the dark phone shows on a dark dialog too.
	private static final int OUTLINE_COLOR = 0xffb0bec5;

	static {
		int corner = 0;
		for (int x = -1; x <= 1; x += 2) {
			for (int y = -1; y <= 1; y += 2) {
				for (int z = -1; z <= 1; z += 2) {
					CORNERS[corner++] = new double[]{x * HALF_WIDTH, y, z * HALF_THICKNESS};
				}
			}
		}
	}

	private final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
	private final Drawable observer;
	private final float density;
	private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint arrowHeadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint hullPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF beamOval = new RectF();
	private final Path signPath = new Path();
	private final PathMeasure signMeasure = new PathMeasure();
	private final Path path = new Path();
	private final double[] point = new double[2];
	private final double[] turns = new double[3];
	private final double[] rotation = new double[9];
	private final float[] position = new float[2];
	private final float[] tangent = new float[2];
	private final float[] projected = new float[2 * 20];
	private final float[] cornersProjected = new float[2 * CORNERS.length];
	private final int[] hullOrder = new int[CORNERS.length];
	private final int[] hull = new int[2 * CORNERS.length];
	private final int[] glareColors = new int[GLARE_POSITIONS.length];
	private float[] signDots = new float[0];
	private float signLength;
	private float beamRadius;
	private float signCenterX;
	private float signHalfWidth;
	// The phone's place and half its height, in pixels.
	private float phoneX;
	private float phoneY;
	private float phoneScale;

	public CompassCalibrationView(Context context) {
		super(context);
		density = getResources().getDisplayMetrics().density;
		observer = ContextCompat.getDrawable(context, R.drawable.ic_my_location);
		// The dialog's text says what it shows.
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

		signPaint.setStyle(Paint.Style.STROKE);
		signPaint.setStrokeCap(Paint.Cap.ROUND);
		signPaint.setStrokeWidth(SIGN_DOT_DP * density);
		signPaint.setColor(SIGN_COLOR);
		arrowPaint.setStyle(Paint.Style.STROKE);
		arrowPaint.setStrokeCap(Paint.Cap.ROUND);
		arrowPaint.setStrokeJoin(Paint.Join.ROUND);
		arrowPaint.setStrokeWidth(ARROW_WIDTH_DP * density);
		arrowPaint.setColor(ARROW_COLOR);
		arrowHeadPaint.setColor(ARROW_COLOR);
		outlinePaint.setStyle(Paint.Style.STROKE);
		outlinePaint.setStrokeWidth(density);
		outlinePaint.setColor(OUTLINE_COLOR);

		animator.setDuration(LOOP_MILLIS * LOOPS_PER_CYCLE);
		animator.setRepeatCount(ValueAnimator.INFINITE);
		animator.setInterpolator(new LinearInterpolator());
		animator.addUpdateListener(animation -> invalidate());
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
				? Math.round(MAX_HEIGHT_DP * density / HEIGHT_PER_WIDTH)
				: MeasureSpec.getSize(widthMeasureSpec);
		float height = Math.min(width * HEIGHT_PER_WIDTH,
				Math.min(MAX_HEIGHT_DP * density, MAX_SCREEN_HEIGHT * metrics.heightPixels));
		setMeasuredDimension(width, resolveSize(Math.round(height), heightMeasureSpec));
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		if (width <= 0 || height <= 0) {
			signLength = 0;
			return;
		}
		int observerSize = Math.round(OBSERVER_SIZE * height);
		beamRadius = observerSize * MapLibreMapWidget.HEADING_ACCURACY_RADIUS_DP * density
				/ observer.getIntrinsicHeight();
		float observerX = width / 2f;
		float observerY = beamRadius + OBSERVER_TOP_MARGIN * height;
		observer.setBounds(Math.round(observerX - observerSize / 2f),
				Math.round(observerY - observerSize / 2f),
				Math.round(observerX + observerSize / 2f),
				Math.round(observerY + observerSize / 2f));
		beamOval.set(observerX - beamRadius, observerY - beamRadius, observerX + beamRadius,
				observerY + beamRadius);
		int color = MapLibreMapWidget.HEADING_ACCURACY_COLOR;
		beamPaint.setShader(new RadialGradient(observerX, observerY, beamRadius, color,
				color & 0x00ffffff, Shader.TileMode.CLAMP));

		signCenterX = width / 2f;
		signHalfWidth = SIGN_HALF_WIDTH * height;
		float signCenterY = SIGN_CENTER_Y * height;
		signPath.reset();
		for (int sample = 0; sample < SIGN_SAMPLES; sample++) {
			CompassCalibrationMotion.signPoint((double) sample / SIGN_SAMPLES, point);
			float x = signCenterX + (float) point[0] * signHalfWidth;
			float y = signCenterY + (float) point[1] * signHalfWidth;
			if (sample == 0) {
				signPath.moveTo(x, y);
			} else {
				signPath.lineTo(x, y);
			}
		}
		signPath.close();
		signMeasure.setPath(signPath, false);
		signLength = signMeasure.getLength();

		// Dots rather than a dashed line, which does not reliably draw dashes of no length.
		float spacing = SIGN_DOT_SPACING_DP * density;
		signDots = new float[2 * (int) (signLength / spacing)];
		for (int dot = 0; dot < signDots.length / 2; dot++) {
			signMeasure.getPosTan(dot * spacing, position, null);
			signDots[2 * dot] = position[0];
			signDots[2 * dot + 1] = position[1];
		}

		phoneScale = PHONE_HEIGHT * height / 2;
		CornerPathEffect rounded = new CornerPathEffect((float) CORNER_RADIUS * phoneScale);
		hullPaint.setPathEffect(rounded);
		outlinePaint.setPathEffect(rounded);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		animator.start();
	}

	@Override
	protected void onDetachedFromWindow() {
		animator.cancel();
		super.onDetachedFromWindow();
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		if (signLength <= 0) {
			return;
		}
		float cycle = animator.getAnimatedFraction();
		float loop = (cycle * LOOPS_PER_CYCLE) % 1;

		drawObserver(canvas, cycle);
		canvas.drawPoints(signDots, signPaint);

		signMeasure.getPosTan(loop * signLength, position, tangent);
		phoneX = position[0];
		phoneY = position[1];
		CompassCalibrationMotion.phoneTurns(loop, turns);
		CompassCalibrationMotion.rotation(turns, rotation);
		drawPhone(canvas);

		drawArrow(canvas, loop * signLength);
	}

	private void drawObserver(Canvas canvas, float cycle) {
		// Narrowing fast at first and slower towards the end, as the compass gets surer.
		float narrowed = 1 - (1 - cycle) * (1 - cycle);
		float halfWidth = BEAM_WIDEST_DEGREES
				+ (BEAM_NARROWEST_DEGREES - BEAM_WIDEST_DEGREES) * narrowed;
		beamPaint.setAlpha(Math.round(255 * Math.min(1, cycle / BEAM_FADE_IN)));
		// Arcs are measured clockwise from the right, so up is at -90 degrees.
		canvas.drawArc(beamOval, -90 - halfWidth, 2 * halfWidth, true, beamPaint);
		observer.draw(canvas);
	}

	/**
	 * The arrow along the sign from the phone, its head where the phone goes next.
	 *
	 * @param start where the phone is along the sign, in pixels
	 */
	private void drawArrow(Canvas canvas, float start) {
		float head = ARROW_HEAD * getHeight();
		float end = start + ARROW_AHEAD * signLength;
		// The line stops inside the head, so that its round end does not show past the point.
		path.reset();
		addSignSegment(start, end - 0.6f * head, path);
		canvas.drawPath(path, arrowPaint);

		signMeasure.getPosTan(end % signLength, position, tangent);
		float x = position[0];
		float y = position[1];
		float alongX = tangent[0] * head;
		float alongY = tangent[1] * head;
		path.reset();
		path.moveTo(x + 0.4f * alongX, y + 0.4f * alongY);
		path.lineTo(x - 0.6f * alongX - 0.45f * alongY, y - 0.6f * alongY + 0.45f * alongX);
		path.lineTo(x - 0.6f * alongX + 0.45f * alongY, y - 0.6f * alongY - 0.45f * alongX);
		path.close();
		canvas.drawPath(path, arrowHeadPaint);
	}

	/**
	 * The part of the sign between two distances along it, round past its start.
	 */
	private void addSignSegment(float from, float to, Path segment) {
		if (to <= signLength) {
			signMeasure.getSegment(from, to, segment, true);
		} else {
			signMeasure.getSegment(from, signLength, segment, true);
			signMeasure.getSegment(0, to - signLength, segment, true);
		}
	}

	/**
	 * The phone as a box, each side lit by how much it faces the light: its outline first, so
	 * that no gap shows past the rounded corners of its screen side, then the sides that face the
	 * viewer, and the screen or the back with what is on it.
	 */
	private void drawPhone(Canvas canvas) {
		for (int corner = 0; corner < CORNERS.length; corner++) {
			project(CORNERS[corner], cornersProjected, corner);
		}
		hullPath(cornersProjected, path);
		hullPaint.setColor(shade(BODY_COLOR, 0.8));
		canvas.drawPath(path, hullPaint);

		for (int side = 0; side < SIDES.length; side++) {
			if (facing(SIDE_NORMALS[side]) > 0) {
				fill(canvas, SIDES[side], shade(BODY_COLOR, lit(SIDE_NORMALS[side])));
			}
		}

		if (facing(FRONT_NORMAL) > 0) {
			double light = lit(FRONT_NORMAL);
			fill(canvas, FRONT, shade(BODY_COLOR, 0.7 * light));
			project(GLARE_FROM, projected, 0);
			project(GLARE_TO, projected, 1);
			int base = shade(SCREEN_COLOR, light);
			int shine = shade(SCREEN_COLOR, GLARE * light);
			for (int stop = 0; stop < glareColors.length; stop++) {
				glareColors[stop] = stop == 2 ? shine : base;
			}
			facePaint.setShader(new LinearGradient(projected[0], projected[1], projected[2],
					projected[3], glareColors, GLARE_POSITIONS, Shader.TileMode.CLAMP));
			fill(canvas, SCREEN, Color.BLACK);
			facePaint.setShader(null);
			fill(canvas, FRONT_CAMERA, shade(BODY_COLOR, 0.6));
		} else {
			fill(canvas, BACK, shade(BODY_COLOR, lit(BACK_NORMAL)));
			fill(canvas, LENS, SCREEN_COLOR);
		}

		hullPath(cornersProjected, path);
		canvas.drawPath(path, outlinePaint);
	}

	private void fill(Canvas canvas, double[][] polygon, int color) {
		path.reset();
		for (int index = 0; index < polygon.length; index++) {
			project(polygon[index], projected, index);
			if (index == 0) {
				path.moveTo(projected[0], projected[1]);
			} else {
				path.lineTo(projected[2 * index], projected[2 * index + 1]);
			}
		}
		path.close();
		// A shader, when set, paints instead of the colour.
		facePaint.setColor(color);
		canvas.drawPath(path, facePaint);
	}

	/**
	 * Where a point of the phone shows on the view, as the phone is turned now, seen from in
	 * front a little way off.
	 *
	 * @param into  set to x and y at the index
	 */
	private void project(double[] phonePoint, float[] into, int index) {
		double x = rotation[0] * phonePoint[0] + rotation[1] * phonePoint[1]
				+ rotation[2] * phonePoint[2];
		double y = rotation[3] * phonePoint[0] + rotation[4] * phonePoint[1]
				+ rotation[5] * phonePoint[2];
		double z = rotation[6] * phonePoint[0] + rotation[7] * phonePoint[1]
				+ rotation[8] * phonePoint[2];
		double perspective = phoneScale * EYE_DISTANCE / (EYE_DISTANCE - z);
		into[2 * index] = (float) (phoneX + x * perspective);
		// The y axis of the phone points up and the view's down.
		into[2 * index + 1] = (float) (phoneY - y * perspective);
	}

	/**
	 * How much a side of the phone faces the viewer, as it is turned now: above 0 when it does.
	 */
	private double facing(double[] normal) {
		return rotation[6] * normal[0] + rotation[7] * normal[1] + rotation[8] * normal[2];
	}

	private double lit(double[] normal) {
		double light = 0;
		for (int axis = 0; axis < 3; axis++) {
			light += LIGHT[axis] * (rotation[3 * axis] * normal[0]
					+ rotation[3 * axis + 1] * normal[1] + rotation[3 * axis + 2] * normal[2]);
		}
		return AMBIENT + DIFFUSE * Math.max(0, light);
	}

	/**
	 * The outline of points, by Andrew's monotone chain.
	 */
	private void hullPath(float[] points, Path outline) {
		int count = points.length / 2;
		for (int index = 0; index < count; index++) {
			int next = index;
			int sorted = index - 1;
			while (sorted >= 0 && isLeftOf(points, next, hullOrder[sorted])) {
				hullOrder[sorted + 1] = hullOrder[sorted];
				sorted--;
			}
			hullOrder[sorted + 1] = next;
		}
		int size = 0;
		for (int index = 0; index < count; index++) {
			while (size >= 2 && turn(points, hull[size - 2], hull[size - 1],
					hullOrder[index]) <= 0) {
				size--;
			}
			hull[size++] = hullOrder[index];
		}
		for (int index = count - 2, lowerSize = size + 1; index >= 0; index--) {
			while (size >= lowerSize && turn(points, hull[size - 2], hull[size - 1],
					hullOrder[index]) <= 0) {
				size--;
			}
			hull[size++] = hullOrder[index];
		}
		outline.reset();
		// The last point is the first again.
		for (int index = 0; index < size - 1; index++) {
			float x = points[2 * hull[index]];
			float y = points[2 * hull[index] + 1];
			if (index == 0) {
				outline.moveTo(x, y);
			} else {
				outline.lineTo(x, y);
			}
		}
		outline.close();
	}

	private static boolean isLeftOf(float[] points, int first, int second) {
		return points[2 * first] < points[2 * second] || (points[2 * first] == points[2 * second]
				&& points[2 * first + 1] < points[2 * second + 1]);
	}

	/**
	 * Which way the path from a to b turns to reach c: above 0 one way, below 0 the other.
	 */
	private static float turn(float[] points, int a, int b, int c) {
		return (points[2 * b] - points[2 * a]) * (points[2 * c + 1] - points[2 * a + 1])
				- (points[2 * b + 1] - points[2 * a + 1]) * (points[2 * c] - points[2 * a]);
	}

	private static int shade(int color, double light) {
		return Color.argb(Color.alpha(color), shadeChannel(Color.red(color), light),
				shadeChannel(Color.green(color), light), shadeChannel(Color.blue(color), light));
	}

	private static int shadeChannel(int channel, double light) {
		return (int) Math.min(255, Math.round(channel * light));
	}

	private static double[] normalized(double x, double y, double z) {
		double length = Math.sqrt(x * x + y * y + z * z);
		return new double[]{x / length, y / length, z / length};
	}

	/**
	 * A rectangle with rounded corners on a side of the phone, as 20 points.
	 */
	private static double[][] roundedRectangle(double halfWidth, double halfHeight,
	                                           double radius, double z) {
		double[][] points = new double[20][];
		// Anticlockwise from the top right, each corner's arc a quarter turn on from the last.
		double[][] centres = {{halfWidth - radius, halfHeight - radius},
				{radius - halfWidth, halfHeight - radius},
				{radius - halfWidth, radius - halfHeight},
				{halfWidth - radius, radius - halfHeight}};
		int index = 0;
		for (int corner = 0; corner < centres.length; corner++) {
			for (int step = 0; step <= 4; step++) {
				double angle = Math.toRadians(90 * corner + 90 * step / 4.0);
				points[index++] = new double[]{centres[corner][0] + radius * Math.cos(angle),
						centres[corner][1] + radius * Math.sin(angle), z};
			}
		}
		return points;
	}

	private static double[][] circle(double x, double y, double radius, double z) {
		double[][] points = new double[16][];
		for (int index = 0; index < points.length; index++) {
			double angle = 2 * Math.PI * index / points.length;
			points[index] = new double[]{x + radius * Math.cos(angle), y + radius * Math.sin(angle),
					z};
		}
		return points;
	}
}
