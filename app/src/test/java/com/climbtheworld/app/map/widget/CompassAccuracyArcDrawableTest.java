package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CompassAccuracyArcDrawableTest {
	@Test
	public void arcIsCentredOnTheNeedle() {
		Canvas canvas = drawArc(35);

		// The dark casing, then the arc on top of it, both reaching 35 degrees either side of up.
		verify(canvas, times(2)).drawArc(any(RectF.class), eq(-125f), eq(70f), eq(false),
				any(Paint.class));
	}

	@Test
	public void arcRunsBetweenTheIconAndTheEdgeOfTheButton() {
		Canvas canvas = drawArc(35);

		ArgumentCaptor<RectF> oval = ArgumentCaptor.forClass(RectF.class);
		verify(canvas, times(2)).drawArc(oval.capture(), anyFloat(), anyFloat(), anyBoolean(),
				any(Paint.class));
		// A 100 pixel button has a radius of 50, of which the arc sits at 71%.
		assertEquals(14.5f, oval.getValue().left, 0.01f);
		assertEquals(85.5f, oval.getValue().right, 0.01f);
	}

	@Test
	public void arcIsColouredFromItsMiddleOut() {
		Canvas canvas = drawArc(35);

		ArgumentCaptor<Paint> paints = ArgumentCaptor.forClass(Paint.class);
		verify(canvas, times(2)).drawArc(any(RectF.class), anyFloat(), anyFloat(), anyBoolean(),
				paints.capture());
		// The casing is plain, the arc over it takes its colours from the gradient.
		assertTrue(paints.getAllValues().get(1).getShader() instanceof SweepGradient);
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
		Canvas canvas = drawArc(180);

		// A half circle over the top, from the left to the right.
		verify(canvas, times(2)).drawArc(any(RectF.class), eq(-180f), eq(180f), eq(false),
				any(Paint.class));
	}

	@Test
	public void noArcWithoutAWidth() {
		Canvas canvas = drawArc(0);

		verify(canvas, never()).drawArc(any(RectF.class), anyFloat(), anyFloat(), anyBoolean(),
				any(Paint.class));
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
