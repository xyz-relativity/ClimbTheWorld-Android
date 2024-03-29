package com.climbtheworld.app.utils.views.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.RemotePagerFragment;

import java.util.ArrayList;
import java.util.List;

import needle.Needle;

/**
 * Created by xyz on 1/4/18.
 */

public class DialogBuilder {
	private static final List<Dialog> activeDialogs = new ArrayList<>();
	private static AlertDialog loadingDialog = null;

	private DialogBuilder() {
		//hide constructor
	}

	static AlertDialog getNewDialog(AppCompatActivity activity) {
		return getNewDialog(activity, false);
	}

	static AlertDialog getNewDialog(AppCompatActivity activity, boolean fullscreen) {
		final AlertDialog alertDialog;
		if (fullscreen) {
			alertDialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen).create();
		} else {
			alertDialog = new AlertDialog.Builder(activity).create();
		}

		activeDialogs.add(alertDialog);
		alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				activeDialogs.remove(alertDialog);
			}
		});

		return alertDialog;
	}

	public static void closeAllDialogs() {
		for (Dialog diag : activeDialogs) {
			diag.dismiss();
		}
		activeDialogs.clear();
	}

	public static AlertDialog buildDownloadRegionAlert(final AppCompatActivity activity) {
		final AlertDialog alertDialog = getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(R.string.tutorial_region_download_title);

		Drawable icon = ContextCompat.getDrawable(activity, android.R.drawable.ic_dialog_alert).mutate();
		icon.setTint(activity.getResources().getColor(android.R.color.holo_orange_light));
		alertDialog.setIcon(icon);

		ViewGroup result = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.fragment_dialog_download, alertDialog.getListView(), false);
		RemotePagerFragment downloadView = new RemotePagerFragment(activity, R.layout.fragment_data_manager_remote_data, DataFragment.initCountryMap(activity));
		downloadView.onCreate(result);

		alertDialog.setView(result);

		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getResources().getString(R.string.dont_show_again), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Configs.instance(activity).setBoolean(Configs.ConfigKey.showDownloadClimbingData, false);
			}
		});

		alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				downloadView.onDestroy(result);
			}
		});
		alertDialog.create();

		return alertDialog;
	}

	public static void showLoadingDialogue(AppCompatActivity activity, String message, DialogInterface.OnCancelListener cancelListener) {
		if (loadingDialog != null) {
			return;
		}

		loadingDialog = getNewDialog(activity);
		loadingDialog.setTitle(R.string.loading_dialog);
		Drawable icon = ContextCompat.getDrawable(activity, android.R.drawable.ic_dialog_info).mutate();
		icon.setTint(activity.getResources().getColor(android.R.color.holo_green_light));
		loadingDialog.setIcon(icon);

		ViewGroup result = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.dialog_loading, loadingDialog.getListView(), false);
		loadingDialog.setView(result);

		((TextView) result.findViewById(R.id.dialogMessage)).setText(message);

		if (cancelListener == null) {
			loadingDialog.setCancelable(false);
		} else {
			loadingDialog.setCancelable(true);
			loadingDialog.setOnCancelListener(cancelListener);
		}

		loadingDialog.setCanceledOnTouchOutside(false);
		loadingDialog.create();
		loadingDialog.show();
	}

	public static void updateLoadingStatus(int status) {
		Needle.onMainThread().execute(new Runnable() {
			public void run() {
				if (loadingDialog != null) {
					((TextView) loadingDialog.getWindow().findViewById(R.id.dialogMessage)).setText(status);
				}
			}
		});
	}

	public static void dismissLoadingDialogue() {
		if (loadingDialog != null) {
			loadingDialog.dismiss();
			loadingDialog = null;
		}
	}

	public static void showErrorDialog(final AppCompatActivity activity, final String message, final DialogInterface.OnClickListener listener) {

		AlertDialog alertDialog = getNewDialog(activity);
		Drawable icon = ContextCompat.getDrawable(activity, android.R.drawable.ic_dialog_alert).mutate();
		icon.setTint(activity.getResources().getColor(android.R.color.holo_red_light));
		alertDialog.setIcon(icon);

		alertDialog.setTitle(activity.getResources().getString(android.R.string.dialog_alert_title));
		alertDialog.setMessage(Html.fromHtml(message));

		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(android.R.string.ok), listener);

		alertDialog.create(); //create all view elements
		((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
		alertDialog.show();
	}

	public static void toastOnMainThread(final Context parent, final String message) {
		Needle.onMainThread().execute(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(parent, message, Toast.LENGTH_LONG).show();
			}
		});
	}
}
