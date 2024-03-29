package com.climbtheworld.app.utils.views;

import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

public interface IPagerViewFragment {
	@LayoutRes
	int getViewId();

	void onCreate(ViewGroup view);

	void onDestroy(ViewGroup view);

	void onViewSelected();
}
