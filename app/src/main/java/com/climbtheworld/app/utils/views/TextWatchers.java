package com.climbtheworld.app.utils.views;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.function.Consumer;

public final class TextWatchers {
	private TextWatchers() {
	}

	/** A watcher acting once the text has changed, as most of the app's do. */
	public static TextWatcher afterTextChanged(Consumer<Editable> action) {
		return new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				action.accept(s);
			}
		};
	}
}
