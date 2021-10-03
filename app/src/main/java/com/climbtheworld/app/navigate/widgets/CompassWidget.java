package com.climbtheworld.app.navigate.widgets;

import android.view.View;

import com.climbtheworld.app.utils.Quaternion;

/**
 * Created by xyz on 1/31/18.
 */

public class CompassWidget {
	private final View compass;

	public CompassWidget(View compassContainer) {
		this.compass = compassContainer;
	}

	public double getOrientation() {
		return compass.getRotation();
	}

	public void updateOrientation(Quaternion event) {
		compass.setRotation(-(float) event.x);
	}
}
