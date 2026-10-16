package com.climbtheworld.app.map.widget;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
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
	public void beyondHalfATurnTheArcIsAFullCircle() {
		Canvas canvas = drawArc(180);

		verify(canvas, times(2)).drawArc(any(RectF.class), eq(-270f), eq(360f), eq(false),
				any(Paint.class));
	}

	@Test
	public void noArcWithoutAWidth() {
		Canvas canvas = drawArc(0);

		verify(canvas, never()).drawArc(any(RectF.class), anyFloat(), anyFloat(), anyBoolean(),
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

	private Canvas drawArc(int halfWidthDegrees) {
		CompassAccuracyArcDrawable arc = new CompassAccuracyArcDrawable();
		arc.setBounds(0, 0, 100, 100);
		arc.setHalfWidthDegrees(halfWidthDegrees);
		Canvas canvas = mock(Canvas.class);
		arc.draw(canvas);
		return canvas;
	}
}
