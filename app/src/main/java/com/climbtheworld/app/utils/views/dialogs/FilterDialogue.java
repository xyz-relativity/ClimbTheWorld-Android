package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.ConfigFragment;
import com.climbtheworld.app.configs.DisplayFilterFragment;

public class FilterDialogue {
	public static void showFilterDialog(final AppCompatActivity activity, ConfigFragment.OnConfigChangeListener listener) {
		final AlertDialog alertDialog = DialogBuilder.getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(activity.getResources().getString(R.string.filter));

		alertDialog.setIcon(R.drawable.ic_filter);

		View view = DialogueUtils.scrollingView(activity, R.layout.fragment_dialog_filter,
				alertDialog.getListView());

		final DisplayFilterFragment filter = new DisplayFilterFragment(activity, view);
		filter.addListener(listener);

		alertDialog.setView(view);

		DialogueUtils.addOkButton(activity, alertDialog, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				filter.done();
			}
		});

		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.reset_filters), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				filter.reset();
			}
		});

		alertDialog.create();
		alertDialog.show();
	}
}
