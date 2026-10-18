package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CompassAccuracyArcDrawableTest {
	@Test
	public void arcIsCentredOnTheNeedle() {
		RectF outline = outlineBounds(drawArc(35));

		// As far either side of up, and over the top of the button.
		assertEquals(50f, outline.centerX(), 0.01f);
		assertTrue(outline.bottom < 50f);
	}

	@Test
	public void widerArcReachesFurtherRound() {
		RectF narrow = outlineBounds(drawArc(10));
		RectF wide = outlineBounds(drawArc(35));

		assertTrue(wide.width() > narrow.width());
		assertTrue(wide.bottom > narrow.bottom);
	}

	@Test
	public void arcIsCasedThenColouredFromItsMiddleOut() {
		Canvas canvas = drawArc(35);

		ArgumentCaptor<Paint> paints = ArgumentCaptor.forClass(Paint.class);
		verify(canvas, times(2)).drawPath(any(Path.class), paints.capture());
		// The casing is plain and grows the outline on all sides, the arc over it fills the
		// outline with the colours of the gradient.
		Paint casing = paints.getAllValues().get(0);
		Paint arc = paints.getAllValues().get(1);
		assertEquals(Paint.Style.FILL_AND_STROKE, casing.getStyle());
		assertNull(casing.getShader());
		assertEquals(Paint.Style.FILL, arc.getStyle());
		assertTrue(arc.getShader() instanceof SweepGradient);
	}

	@Test
	public void endsAreSquaredOffByLinesInAlmostToTheCompass() {
		CompassAccuracyArcDrawable arc = new CompassAccuracyArcDrawable();
		arc.setBounds(0, 0, 100, 100);
		arc.setHalfWidthDegrees(90);
		arc.draw(mock(Canvas.class));
		Path right = mock(Path.class);
		Path left = mock(Path.class);

		arc.addEnd(right, 90, 50);
		arc.addEnd(left, -90, 50);

		// Level with the centre, from the middle of the arc at 71% of the radius in to 49%, just
		// short of the compass icon, and as wide as the middle, 5% of the radius, on the side of
		// the arc. Clockwise, like the arc.
		InOrder inRight = inOrder(right);
		inRight.verify(right).moveTo(AdditionalMatchers.eq(74.5f, 0.01f),
				AdditionalMatchers.eq(47.5f, 0.01f));
		inRight.verify(right).lineTo(AdditionalMatchers.eq(85.5f, 0.01f),
				AdditionalMatchers.eq(47.5f, 0.01f));
		inRight.verify(right).lineTo(AdditionalMatchers.eq(85.5f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f));
		inRight.verify(right).lineTo(AdditionalMatchers.eq(74.5f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f));
		inRight.verify(right).close();
		InOrder inLeft = inOrder(left);
		inLeft.verify(left).moveTo(AdditionalMatchers.eq(25.5f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f));
		inLeft.verify(left).lineTo(AdditionalMatchers.eq(14.5f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f));
		inLeft.verify(left).lineTo(AdditionalMatchers.eq(14.5f, 0.01f),
				AdditionalMatchers.eq(47.5f, 0.01f));
		inLeft.verify(left).lineTo(AdditionalMatchers.eq(25.5f, 0.01f),
				AdditionalMatchers.eq(47.5f, 0.01f));
		inLeft.verify(left).close();
	}

	@Test
	public void middleIsMarkedAcrossTheArc() {
		Canvas canvas = drawArc(35);

		// Straight up from the centre, from the inner edge of the arc at 64% of the radius to its
		// outer edge at 78%: the dark edge, then the white line.
		verify(canvas, times(2)).drawLine(AdditionalMatchers.eq(50f, 0.01f),
				AdditionalMatchers.eq(18f, 0.01f), AdditionalMatchers.eq(50f, 0.01f),
				AdditionalMatchers.eq(11f, 0.01f), any(Paint.class));
	}

	@Test
	public void ticksShowOnceTheArcReachesThem() {
		// The middle is two lines, a dark edge and a white line; each tick one dark line.
		assertEquals(2, countLines(drawArc(10)));
		assertEquals(2 + 2, countLines(drawArc(15)));
		assertEquals(2 + 2 * 2, countLines(drawArc(35)));
		assertEquals(2 + 2 * 6, countLines(drawArc(90)));
		// The arc stops at a right angle, and so do the ticks.
		assertEquals(2 + 2 * 6, countLines(drawArc(180)));
	}

	@Test
	public void ticksEveryFortyFiveDegreesCrossTheWholeArc() {
		Canvas canvas = drawArc(90);

		// Level with the centre, from the inner edge of the arc at 64% of the radius to its outer
		// edge at 78%, on the right and on the left.
		verify(canvas).drawLine(AdditionalMatchers.eq(82f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f), AdditionalMatchers.eq(89f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f), any(Paint.class));
		verify(canvas).drawLine(AdditionalMatchers.eq(18f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f), AdditionalMatchers.eq(11f, 0.01f),
				AdditionalMatchers.eq(50f, 0.01f), any(Paint.class));
	}

	@Test
	public void beyondARightAngleTheArcStopsAtTheEndOfTheScale() {
		RectF rightAngle = outlineBounds(drawArc(90));
		RectF beyond = outlineBounds(drawArc(180));

		assertEquals(rightAngle, beyond);
	}

	@Test
	public void noArcWithoutAWidth() {
		Canvas canvas = drawArc(0);

		verify(canvas, never()).drawPath(any(Path.class), any(Paint.class));
		verify(canvas, never()).drawLine(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
				any(Paint.class));
	}

	@Test
	public void onlyRedrawsWhenTheWidthChanges() {
		CompassAccuracyArcDrawable arc = new CompassAccuracyArcDrawable();
		Drawable.Callback callback = mock(Drawable.Callback.class);
		arc.setCallback(callback);

		arc.setHalfWidthDegrees(10);
		arc.setHalfWidthDegrees(10);

		verify(callback, times(1)).invalidateDrawable(arc);
	}

	private static RectF outlineBounds(Canvas canvas) {
		ArgumentCaptor<Path> outline = ArgumentCaptor.forClass(Path.class);
		verify(canvas, times(2)).drawPath(outline.capture(), any(Paint.class));
		RectF bounds = new RectF();
		outline.getValue().computeBounds(bounds, true);
		return bounds;
	}

	private static int countLines(Canvas canvas) {
		ArgumentCaptor<Paint> paints = ArgumentCaptor.forClass(Paint.class);
		verify(canvas, atLeast(0)).drawLine(anyFloat(), anyFloat(), anyFloat(), anyFloat(),
				paints.capture());
		return paints.getAllValues().size();
	}

	private Canvas drawArc(int halfWidthDegrees) {
		CompassAccuracyArcDrawable arc = new CompassAccuracyArcDrawable();
		arc.setBounds(0, 0, 100, 100);
		arc.setHalfWidthDegrees(halfWidthDegrees);
		Canvas canvas = mock(Canvas.class);
		arc.draw(canvas);
		return canvas;
	}
}
