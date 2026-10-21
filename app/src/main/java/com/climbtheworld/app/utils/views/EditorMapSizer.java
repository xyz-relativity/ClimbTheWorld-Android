package com.climbtheworld.app.utils.views;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

public class EditorMapSizer {
	/** How much of the form's visible height the map takes when the editor opens. */
	public static final float MAP_SCREEN_FRACTION = 0.75f;

	private EditorMapSizer() {
		//hide
	}

	/**
	 * A touch listener keeping the form's scroll view from taking over a drag on the map, which
	 * pans or rotates it instead.
	 */
	public static View.OnTouchListener holdScrollWhileTouched() {
		return (view, motionEvent) -> {
			ViewParent scrollParent = view.getParent();
			while (scrollParent != null && !(scrollParent instanceof ScrollView)) {
				scrollParent = scrollParent.getParent();
			}
			if (scrollParent != null) {
				switch (motionEvent.getAction()) {
					case MotionEvent.ACTION_DOWN:
						scrollParent.requestDisallowInterceptTouchEvent(true);
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						scrollParent.requestDisallowInterceptTouchEvent(false);
						break;
				}
			}
			return false;
		};
	}

	/**
	 * Gives mapContainer MAP_SCREEN_FRACTION of what scrollView shows on the first layout.
	 * It is sized once, so the map does not shrink when the keyboard opens.
	 */
	public static void sizeOnFirstLayout(View scrollView, View mapContainer) {
		scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				int visibleHeight = scrollView.getHeight() - scrollView.getPaddingTop() - scrollView.getPaddingBottom();
				if (visibleHeight <= 0) {
					return;
				}
				scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

				ViewGroup.LayoutParams params = mapContainer.getLayoutParams();
				params.height = Math.round(visibleHeight * MAP_SCREEN_FRACTION);
				mapContainer.setLayoutParams(params);
			}
		});
	}
}
