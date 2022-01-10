package com.climbtheworld.app.utils.views;

import android.view.MotionEvent;

public class RotationGestureDetector {
	public interface RotationListener {
		void onRotate(float deltaAngle);
	}

	protected float mRotation;
	private final RotationListener mListener;
	private boolean mEnabled = true;

	public RotationGestureDetector(RotationListener listener) {
		mListener = listener;
	}

	private static float rotation(MotionEvent event) {
		double delta_x = (event.getX(0) - event.getX(1));
		double delta_y = (event.getY(0) - event.getY(1));
		double radians = Math.atan2(delta_y, delta_x);
		return (float) Math.toDegrees(radians);
	}

	public void onTouch(MotionEvent e) {
		if (e.getPointerCount() != 2)
			return;

		if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
			mRotation = rotation(e);
		}

		float rotation = rotation(e);
		float delta = rotation - mRotation;

		//we have to allow detector to capture and store the new rotation to avoid UI jump when
		//user enables the overlay again
		if (mEnabled) {
			mRotation += delta;
			mListener.onRotate(delta);
		} else {
			mRotation = rotation;
		}
	}

	public void setEnabled(final boolean pEnabled) {
		this.mEnabled = pEnabled;
	}

	public boolean isEnabled() {
		return this.mEnabled;
	}
}
