package com.climbtheworld.app.utils.views;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class EdgeToEdge {
	private EdgeToEdge() {
	}

	/**
	 * Pads the view by the system bars, the display cutout and the keyboard, which the app's
	 * windows draw under from Android 15 on.
	 */
	public static void padForSystemBars(View view) {
		ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
	}
}
