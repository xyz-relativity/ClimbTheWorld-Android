package com.climbtheworld.app.utils.views;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

public class EditorMapSizer {
	/** How much of the form's visible height the map takes when the editor opens. */
	public static final float MAP_SCREEN_FRACTION = 0.75f;

	private EditorMapSizer() {
		//hide
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
