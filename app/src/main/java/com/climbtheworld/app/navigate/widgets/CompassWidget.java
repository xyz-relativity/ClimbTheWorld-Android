package com.climbtheworld.app.navigate.widgets;

import android.animation.ValueAnimator;
import android.view.View;

import com.climbtheworld.app.augmentedreality.AugmentedRealityUtils;
import com.climbtheworld.app.utils.Vector4d;

/**
 * Created by xyz on 1/31/18.
 */

public class CompassWidget {
	private final View compass;
	ValueAnimator animator = null;

	public CompassWidget(View compassContainer, boolean animate) {
		this.compass = compassContainer;

		if (animate) {
			animator = new ValueAnimator();
			animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator valueAnimator) {
					float animatedValue = (float) valueAnimator.getAnimatedValue();
					compass.setRotation(animatedValue);
				}
			});
			animator.setFloatValues(compass.getRotation(), compass.getRotation());
			animator.setDuration(100);
		}
	}

	public double getOrientation() {
		return compass.getRotation();
	}

	public void updateOrientation(Vector4d event) {
		if (animator != null) {
			float angle = (float) AugmentedRealityUtils.diffAngle(-(float) event.x, compass.getRotation());
			float from = compass.getRotation() % 360;

			animator.cancel();
			animator.setFloatValues(from, from + angle);
			animator.start();
		} else {
			compass.setRotation(-(float) event.x);
		}
	}
}
