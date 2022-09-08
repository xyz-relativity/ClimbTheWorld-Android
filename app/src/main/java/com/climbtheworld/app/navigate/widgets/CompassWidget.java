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
	ValueAnimator animator = new ValueAnimator();

	public CompassWidget(View compassContainer) {
		this.compass = compassContainer;

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

	public double getOrientation() {
		return compass.getRotation();
	}

	public void updateOrientation(Vector4d event) {
		float angle = (float) AugmentedRealityUtils.diffAngle(-(float) event.x, compass.getRotation());
		animator.cancel();
		animator.setFloatValues(compass.getRotation(), compass.getRotation() + angle);
		animator.start();
	}
}
