package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.AugmentedRealityFragment;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.IntercomFragment;

import java.util.function.BiConsumer;

/** The settings of one feature, in a dialog over it. */
public class SettingsDialogue {
	private SettingsDialogue() {
	}

	public static void showAugmentedReality(AppCompatActivity activity,
	                                        ConfigFragment.OnConfigChangeListener listener) {
		show(activity, R.string.ar_settings, R.drawable.ic_view_topo_ar,
				R.layout.fragment_settings_ar_filter, AugmentedRealityFragment::new, listener);
	}

	public static void showWalkieTalkie(AppCompatActivity activity,
	                                    ConfigFragment.OnConfigChangeListener listener) {
		show(activity, R.string.walkie_talkie_audio_settings, R.drawable.ic_walkietalkie,
				R.layout.fragment_settings_walkie_talkie, IntercomFragment::new, listener);
	}

	/**
	 * Shows the settings of layout, which fragment wires up, and tells listener once the dialog
	 * closes rather than on every step of a seek bar.
	 */
	private static void show(AppCompatActivity activity, @StringRes int title,
	                         @DrawableRes int icon, @LayoutRes int layout,
	                         BiConsumer<AppCompatActivity, View> fragment,
	                         ConfigFragment.OnConfigChangeListener listener) {
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(activity.getResources().getString(title));
		alertDialog.setIcon(icon);

		View view = DialogueUtils.scrollingView(activity, layout, alertDialog.getListView());
		fragment.accept(activity, view);
		alertDialog.setView(view);
		alertDialog.setOnDismissListener(dialogInterface -> listener.onConfigChange());

		DialogueUtils.addOkButton(activity, alertDialog, (dialog, which) -> {
		});

		alertDialog.create();
		alertDialog.show();
	}
}
