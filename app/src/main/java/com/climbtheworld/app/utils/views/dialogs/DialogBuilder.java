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

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.RemotePagerFragment;
import com.climbtheworld.app.utils.views.EdgeToEdge;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import needle.Needle;

/**
 * Created by xyz on 1/4/18.
 */

public class DialogBuilder {
	// The dialogs closeAllDialogs closes. Held weakly: a dialog has only one dismiss listener, so
	// one whose listener was set again, as the settings dialogs and the download prompt do, stays
	// listed once dismissed, and would keep its activity from being freed. A dialog on screen is
	// kept by its window, and one not shown yet by whoever builds it.
	private static final List<WeakReference<Dialog>> activeDialogs = new ArrayList<>();
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
			alertDialog = new AlertDialog.Builder(activity, R.style.FullscreenDialogTheme).create();
			// Fullscreen dialogs are edge-to-edge from Android 15, like the activities.
			EdgeToEdge.padForSystemBars(alertDialog.getWindow().getDecorView().findViewById(android.R.id.content));
		} else {
			alertDialog = new AlertDialog.Builder(activity).create();
		}

		forget(alertDialog);
		activeDialogs.add(new WeakReference<>(alertDialog));
		alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialogInterface) {
				forget(alertDialog);
			}
		});

		return alertDialog;
	}

	/** Takes the dialog off the dialogs to close, along with the ones freed since. */
	private static void forget(Dialog dialog) {
		activeDialogs.removeIf(listed -> {
			Dialog other = listed.get();
			return other == null || other == dialog;
		});
	}

	/** A system dialog icon in the colour, like the alert icon in orange. */
	public static Drawable tintedIcon(Context context, @DrawableRes int iconId,
	                                  @ColorRes int colorId) {
		Drawable icon = ContextCompat.getDrawable(context, iconId).mutate();
		icon.setTint(ContextCompat.getColor(context, colorId));
		return icon;
	}

	public static void closeAllDialogs() {
		for (WeakReference<Dialog> listed : activeDialogs) {
			Dialog diag = listed.get();
			if (diag != null) {
				diag.dismiss();
			}
		}
		activeDialogs.clear();
	}

	public static AlertDialog buildDownloadRegionAlert(final AppCompatActivity activity) {
		final AlertDialog alertDialog = getNewDialog(activity);
		alertDialog.setCancelable(true);
		alertDialog.setCanceledOnTouchOutside(true);
		alertDialog.setTitle(R.string.tutorial_region_download_title);

		alertDialog.setIcon(tintedIcon(activity, android.R.drawable.ic_dialog_alert, android.R.color.holo_orange_light));

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

	/** Asks before a download over mobile data, which the settings turn off for climbing data. */
	public static void showMobileDataDownloadDialog(final AppCompatActivity activity,
	                                                String countryName,
	                                                DialogInterface.OnClickListener onDownload) {
		AlertDialog alertDialog = getNewDialog(activity);
		alertDialog.setIcon(tintedIcon(activity, android.R.drawable.ic_dialog_alert, android.R.color.holo_orange_light));

		alertDialog.setTitle(R.string.download_mobile_data_title);
		alertDialog.setMessage(activity.getString(R.string.download_mobile_data_message, countryName));

		alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
				activity.getString(R.string.download_anyway), onDownload);
		alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.cancel),
				(DialogInterface.OnClickListener) null);
		alertDialog.show();
	}

	public static void showLoadingDialogue(AppCompatActivity activity, String message, DialogInterface.OnCancelListener cancelListener) {
		if (loadingDialog != null) {
			return;
		}

		loadingDialog = getNewDialog(activity);
		loadingDialog.setTitle(R.string.loading_dialog);
		loadingDialog.setIcon(tintedIcon(activity, android.R.drawable.ic_dialog_info, android.R.color.holo_green_light));

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
		alertDialog.setIcon(tintedIcon(activity, android.R.drawable.ic_dialog_alert, android.R.color.holo_red_light));

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
