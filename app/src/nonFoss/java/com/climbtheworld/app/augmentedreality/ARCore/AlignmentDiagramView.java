package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;

/**
 * A step of aligning the A.R. on mapped routes, see AlignmentHelp, drawn as the A.R. shows it
 * over the rock: the routes' pins are the A.R.'s own, and Align and the crosshair their own
 * icons. Everything is placed in dp, on a view of WIDTH by HEIGHT.
 */
class AlignmentDiagramView extends View {
	enum Step {
		// Align is tapped, and the list of the routes around opens.
		PICK,
		// The route picked rides on the crosshair, aimed at where the route starts, and a tap on
		// Align drops it there.
		DROP,
		// A second route, away from the first, sets the heading.
		SECOND_ROUTE
	}

	private static final float WIDTH = 104;
	private static final float HEIGHT = 128;
	private static final float CORNER_RADIUS = 8;
	private static final int SKY_COLOR = 0xffc5d6e3;
	private static final int ROCK_COLOR = 0xff8e8478;
	private static final int CRACK_COLOR = 0xff6f6458;
	private static final int GROUND_COLOR = 0xff6f7d4f;
	private static final int BOLT_COLOR = 0xffeceff1;
	private static final int OUTLINE_COLOR = 0xff263238;
	private static final int TAP_RING_COLOR = 0xd9ffffff;
	private static final int TAP_FILL_COLOR = 0x4dffffff;
	private static final int LIST_PICKED_COLOR = 0xffe0f2f1;
	private static final int LIST_TITLE_COLOR = 0xff9e9e9e;
	private static final int LIST_NAME_COLOR = 0xffbdbdbd;
	private static final int LIST_SHADOW_COLOR = 0x59000000;
	// AppCompat's accent, should the theme not have one.
	private static final int DEFAULT_BUTTON_COLOR = 0xff009688;

	// The rock, below an edge of peaks, and the ground at its foot, as x and y pairs.
	private static final float[] ROCK = {0, 18, 14, 12, 30, 16, 46, 8, 62, 14, 80, 6, 104, 12,
			104, 116, 0, 120};
	private static final float[][] CRACKS = {{10, 40, 18, 58, 14, 80}, {66, 30, 72, 50, 70, 66},
			{30, 96, 40, 102, 56, 100}, {88, 84, 96, 100}};
	private static final float[] GROUND = {0, 118, 104, 112, 104, 128, 0, 128};
	private static final float CRACK_WIDTH = 1.2f;
	private static final float BOLT_RADIUS = 1.8f;
	private static final float BOLT_OUTLINE_WIDTH = 0.8f;
	// Align, at the right of the view as in the A.R., and the ring of a tap on it.
	private static final float BUTTON_X = 88;
	private static final float BUTTON_Y = 64;
	private static final float BUTTON_RADIUS = 12;
	private static final float BUTTON_ICON_SIZE = 21;
	private static final float TAP_RADIUS = 16.5f;
	private static final float TAP_WIDTH = 1.6f;
	private static final float CROSSHAIR_SIZE = 28;
	private static final float PIN_HEIGHT = 40;
	// The list of the routes, over the left of the view, its first row picked: the route the
	// next step drops.
	private static final RectF LIST = new RectF(6, 30, 70, 100);
	private static final float LIST_CORNER_RADIUS = 3;
	private static final float LIST_SHADOW_RADIUS = 4;
	private static final RectF LIST_TITLE = new RectF(12, 36, 46, 40);
	private static final RectF LIST_PICKED = new RectF(6, 46, 70, 64);
	private static final float LIST_FIRST_ROW = 49;
	private static final float LIST_ROW_HEIGHT = 17;
	private static final float LIST_PIN_HEIGHT = 13;
	private static final float[] LIST_NAME_WIDTHS = {38, 30, 34};
	private static final float ARROW_HEAD = 5;
	private static final float ARROW_OUTLINE_WIDTH = 3.4f;
	private static final float ARROW_WIDTH = 1.6f;

	private final Step step;
	// The pins of routes of different grades, as the A.R. draws them.
	private final Drawable[] pins;
	private final Drawable align;
	private final Drawable drop;
	private final Drawable crosshair;
	private final int buttonColor;
	private final float density;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();

	/**
	 * @param pins at least as many as the list of routes shows, see LIST_NAME_WIDTHS
	 */
	AlignmentDiagramView(Context context, Step step, Drawable[] pins) {
		super(context);
		this.step = step;
		this.pins = pins;
		density = getResources().getDisplayMetrics().density;
		align = ContextCompat.getDrawable(context, R.drawable.ic_ar_align);
		drop = ContextCompat.getDrawable(context, R.drawable.ic_ar_align_drop);
		crosshair = ContextCompat.getDrawable(context, R.drawable.ic_ar_crosshair);
		// The colour of the buttons, see Widget.Design.FloatingActionButton.
		TypedValue accent = new TypedValue();
		if (!context.getTheme().resolveAttribute(R.attr.colorAccent, accent, true)) {
			buttonColor = DEFAULT_BUTTON_COLOR;
		} else if (accent.resourceId != 0) {
			buttonColor = ContextCompat.getColor(context, accent.resourceId);
		} else {
			buttonColor = accent.data;
		}
		// The text says what the diagram shows.
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeCap(Paint.Cap.ROUND);
		stroke.setStrokeJoin(Paint.Join.ROUND);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(Math.round(WIDTH * density), Math.round(HEIGHT * density));
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		canvas.save();
		canvas.scale(density, density);
		drawScene(canvas);
		switch (step) {
			case PICK:
				drawBolts(canvas, 50, 100, 44, 20, 7);
				drawButton(canvas, align);
				drawList(canvas);
				break;
			case DROP:
				drawBolts(canvas, 50, 70, 44, 14, 6);
				drawIcon(canvas, crosshair, 50, 70, CROSSHAIR_SIZE);
				drawPin(canvas, pins[0], 50, 70, PIN_HEIGHT);
				drawButton(canvas, drop);
				break;
			case SECOND_ROUTE:
				drawBolts(canvas, 20, 98, 16, 22, 7);
				drawBolts(canvas, 82, 92, 86, 20, 7);
				drawPin(canvas, pins[0], 20, 98, PIN_HEIGHT);
				drawPin(canvas, pins[1], 82, 92, PIN_HEIGHT);
				drawArrow(canvas, 26, 106, 76, 101);
				break;
		}
		canvas.restore();
	}

	/**
	 * The sky, the rock and the ground, which clip the rest to the rounded corners.
	 */
	private void drawScene(Canvas canvas) {
		path.reset();
		path.addRoundRect(0, 0, WIDTH, HEIGHT, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW);
		canvas.clipPath(path);
		canvas.drawColor(SKY_COLOR);
		fill.setColor(ROCK_COLOR);
		drawPolygon(canvas, ROCK, fill);
		stroke.setColor(CRACK_COLOR);
		stroke.setStrokeWidth(CRACK_WIDTH);
		for (float[] crack : CRACKS) {
			setPolyline(crack);
			canvas.drawPath(path, stroke);
		}
		fill.setColor(GROUND_COLOR);
		drawPolygon(canvas, GROUND, fill);
	}

	/**
	 * The bolts of a route, evenly along a line from its start.
	 */
	private void drawBolts(Canvas canvas, float startX, float startY, float endX, float endY,
	                       int count) {
		fill.setColor(BOLT_COLOR);
		stroke.setColor(OUTLINE_COLOR);
		stroke.setStrokeWidth(BOLT_OUTLINE_WIDTH);
		for (int bolt = 0; bolt < count; bolt++) {
			float along = (bolt + 0.5f) / count;
			float x = startX + (endX - startX) * along;
			float y = startY + (endY - startY) * along;
			canvas.drawCircle(x, y, BOLT_RADIUS, fill);
			canvas.drawCircle(x, y, BOLT_RADIUS, stroke);
		}
	}

	/**
	 * Align, with the icon it has at this step, being tapped.
	 */
	private void drawButton(Canvas canvas, Drawable icon) {
		fill.setColor(buttonColor);
		canvas.drawCircle(BUTTON_X, BUTTON_Y, BUTTON_RADIUS, fill);
		drawIcon(canvas, icon, BUTTON_X, BUTTON_Y, BUTTON_ICON_SIZE);
		fill.setColor(TAP_FILL_COLOR);
		canvas.drawCircle(BUTTON_X, BUTTON_Y, BUTTON_RADIUS, fill);
		stroke.setColor(TAP_RING_COLOR);
		stroke.setStrokeWidth(TAP_WIDTH);
		canvas.drawCircle(BUTTON_X, BUTTON_Y, TAP_RADIUS, stroke);
	}

	/**
	 * The list of the routes around, as Align opens it: a title, then a row per route with its
	 * pin and name, the first one picked.
	 */
	private void drawList(Canvas canvas) {
		fill.setColor(Color.WHITE);
		fill.setShadowLayer(LIST_SHADOW_RADIUS, 0, 1, LIST_SHADOW_COLOR);
		canvas.drawRoundRect(LIST, LIST_CORNER_RADIUS, LIST_CORNER_RADIUS, fill);
		fill.clearShadowLayer();
		fill.setColor(LIST_PICKED_COLOR);
		canvas.drawRect(LIST_PICKED, fill);
		fill.setColor(LIST_TITLE_COLOR);
		canvas.drawRect(LIST_TITLE, fill);
		fill.setColor(LIST_NAME_COLOR);
		for (int row = 0; row < LIST_NAME_WIDTHS.length; row++) {
			float top = LIST_FIRST_ROW + row * LIST_ROW_HEIGHT;
			drawPin(canvas, pins[row], LIST.left + 9, top + 12, LIST_PIN_HEIGHT);
			canvas.drawRect(LIST.left + 16, top + 4, LIST.left + 16 + LIST_NAME_WIDTHS[row],
					top + 7, fill);
		}
	}

	/**
	 * A white arrow with a dark edge and a head at both ends, for how far apart two routes are.
	 */
	private void drawArrow(Canvas canvas, float fromX, float fromY, float toX, float toY) {
		double angle = Math.atan2(toY - fromY, toX - fromX);
		path.reset();
		path.moveTo(fromX, fromY);
		path.lineTo(toX, toY);
		addArrowHead(fromX, fromY, angle);
		addArrowHead(toX, toY, angle + Math.PI);
		stroke.setColor(OUTLINE_COLOR);
		stroke.setStrokeWidth(ARROW_OUTLINE_WIDTH);
		canvas.drawPath(path, stroke);
		stroke.setColor(Color.WHITE);
		stroke.setStrokeWidth(ARROW_WIDTH);
		canvas.drawPath(path, stroke);
	}

	/**
	 * @param angle the way the arrow's line goes from its tip
	 */
	private void addArrowHead(float x, float y, double angle) {
		path.moveTo(x + ARROW_HEAD * (float) Math.cos(angle + 0.5),
				y + ARROW_HEAD * (float) Math.sin(angle + 0.5));
		path.lineTo(x, y);
		path.lineTo(x + ARROW_HEAD * (float) Math.cos(angle - 0.5),
				y + ARROW_HEAD * (float) Math.sin(angle - 0.5));
	}

	/**
	 * A pin standing on a point, as the A.R. puts its tip on the route.
	 */
	private static void drawPin(Canvas canvas, Drawable pin, float x, float y, float height) {
		float scale = height / pin.getIntrinsicHeight();
		drawScaled(canvas, pin, x - pin.getIntrinsicWidth() * scale / 2, y - height, scale);
	}

	private static void drawIcon(Canvas canvas, Drawable icon, float x, float y, float size) {
		drawScaled(canvas, icon, x - size / 2, y - size / 2,
				size / Math.max(icon.getIntrinsicWidth(), icon.getIntrinsicHeight()));
	}

	/**
	 * Draws a drawable at its own size, in pixels, scaled into the diagram's dp.
	 */
	private static void drawScaled(Canvas canvas, Drawable drawable, float left, float top,
	                               float scale) {
		canvas.save();
		canvas.translate(left, top);
		canvas.scale(scale, scale);
		drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		drawable.draw(canvas);
		canvas.restore();
	}

	private void drawPolygon(Canvas canvas, float[] points, Paint paint) {
		setPolyline(points);
		path.close();
		canvas.drawPath(path, paint);
	}

	private void setPolyline(float[] points) {
		path.reset();
		path.moveTo(points[0], points[1]);
		for (int point = 2; point < points.length; point += 2) {
			path.lineTo(points[point], points[point + 1]);
		}
	}
}
