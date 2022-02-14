package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.IntercomFragment;

public class IntercomSettingsDialogue {
	private static View buildFilterDialog(final AppCompatActivity activity,
	                                      final ViewGroup container) {
		ScrollView wrapper = new ScrollView(activity);
		wrapper.addView(activity.getLayoutInflater().inflate(R.layout.fragment_settings_intercom, container, false));
		wrapper.setVerticalScrollBarEnabled(true);
		wrapper.setHorizontalScrollBarEnabled(false);
		return wrapper;
	}

	public static void showFilterDialog(final AppCompatActivity activity, ConfigFragment.OnConfigChangeListener listener) {
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(activity.getResources().getString(R.string.intercom_settings));

		alertDialog.setIcon(R.drawable.ic_intercom);

		View view = buildFilterDialog(activity, alertDialog.getListView());

		IntercomFragment intercom = new IntercomFragment(activity, view);

		alertDialog.setView(view);

		DialogueUtils.addOkButton(activity, alertDialog, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				listener.onConfigChange();
			}
		});

		alertDialog.create();
		alertDialog.show();
	}
}
