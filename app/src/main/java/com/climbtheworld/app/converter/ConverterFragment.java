package com.climbtheworld.app.converter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.views.IPagerViewFragment;

public abstract class ConverterFragment implements IPagerViewFragment {
	final AppCompatActivity parent;
	protected final Configs configs;
	@LayoutRes
	private
	int viewID;
	ViewGroup view;

	ConverterFragment(AppCompatActivity parent, @LayoutRes int viewID) {
		this.parent = parent;
		this.viewID = viewID;
		configs = Configs.instance(parent);
	}

	public int getViewId() {
		return viewID;
	}

	<T extends View> T findViewById(@IdRes int id) {
		return view.findViewById(id);
	}

	void showKeyboard(final View focused) {
		focused.post(new Runnable() {
			@Override
			public void run() {
				final InputMethodManager inputManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputManager.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT);
			}
		});
	}

	void hideKeyboard() {
		view.post(new Runnable() {
			@Override
			public void run() {
				InputMethodManager inputManager = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			}
		});
	}

	public void onCreate(final ViewGroup view) {

	}

	public void onDestroy(final ViewGroup view) {

	}
}
