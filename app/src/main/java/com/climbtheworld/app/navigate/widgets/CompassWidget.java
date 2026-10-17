package com.climbtheworld.app.navigate.widgets;

import android.animation.TimeAnimator;
import android.view.View;

import com.climbtheworld.app.utils.GeoUtils;
import com.climbtheworld.app.utils.Vector4d;

/**
 * Created by xyz on 1/31/18.
 *
 * When animated, the needle glides towards the latest reading on every frame, however often the
 * readings come. Restarting an animation on each reading instead left the needle standing still
 * once readings came about once a frame, since an animation shows where it starts on its first
 * frame.
 */
public class CompassWidget {
	// How quickly the needle closes in on the latest reading: after this long, about a third of
	// the way is left. Long enough to glide between readings at the UI rate, short enough not to
	// trail behind while turning.
	static final double FOLLOW_SECONDS = 0.05;
	// Close enough to stop animating until the next reading.
	private static final double SETTLED_DEGREES = 0.05;

	private final View compass;
	private final TimeAnimator animator;
	private float targetRotation;

	public CompassWidget(View compassContainer, boolean animate) {
		this.compass = compassContainer;

		if (animate) {
			animator = new TimeAnimator();
			animator.setTimeListener((animation, totalTime, deltaTime) -> followTarget(deltaTime));
		} else {
			animator = null;
		}
	}

	public double getOrientation() {
		return compass.getRotation();
	}

	public void updateOrientation(Vector4d event) {
		targetRotation = normalize(-event.x);
		if (animator == null) {
			compass.setRotation(targetRotation);
		} else if (!animator.isStarted()) {
			animator.start();
		}
	}

	private void followTarget(long elapsedMs) {
		float rotation = normalize(compass.getRotation());
		double remaining = GeoUtils.diffAngle(targetRotation, rotation);
		if (Math.abs(remaining) < SETTLED_DEGREES) {
			compass.setRotation(targetRotation);
			animator.cancel();
			return;
		}
		compass.setRotation(normalize(rotation + followStep(remaining, elapsedMs)));
	}

	/**
	 * @return how far the needle moves in one frame towards a reading this far away, the same
	 * whether the frames come often or not
	 */
	static double followStep(double remainingDegrees, long elapsedMs) {
		return remainingDegrees * (1 - Math.exp(-elapsedMs / 1000.0 / FOLLOW_SECONDS));
	}

	/**
	 * Between 0 and 360, where GeoUtils.diffAngle() gives the shorter way round.
	 */
	private static float normalize(double degrees) {
		return (float) (((degrees % 360) + 360) % 360);
	}
}
