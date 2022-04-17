package com.climbtheworld.app.tutorial;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;

public abstract class TutorialFragment {
	public final LayoutInflater inflater;
	WeakReference<AppCompatActivity> parent;
	@LayoutRes
	private final int viewID;

	TutorialFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		this.parent = new WeakReference<>(parent);
		this.viewID = viewID;

		inflater = parent.getLayoutInflater();
	}

	public @LayoutRes
	int getViewId() {
		return this.viewID;
	}

	public void onCreate(final ViewGroup view) {

	}

	public void onDestroy(final ViewGroup view) {

	}
}
