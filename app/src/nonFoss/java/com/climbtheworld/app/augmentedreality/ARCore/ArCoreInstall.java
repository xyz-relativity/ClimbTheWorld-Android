package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.google.ar.core.ArCoreApk;

/**
 * The ways to get Google Play Services for AR, the app ARCore runs in, which the app cannot carry
 * itself: from Google Play, or as the APK Google puts on GitHub, for phones without Google Play.
 * Both need the internet, which there may be none of at the crag, so the setup wizard offers them
 * as well as the A.R. views.
 */
public final class ArCoreInstall {
	private static final String ARCORE_PACKAGE = "com.google.ar.core";
	private static final String PLAY_STORE_PACKAGE = "com.android.vending";
	// Google's releases of the ARCore SDK, each with the APK for phones,
	// Google_Play_Services_for_AR_<version>.apk, beside the emulator's and the SDK.
	private static final String APK_PAGE =
			"https://github.com/google-ar/arcore-android-sdk/releases/latest";

	private ArCoreInstall() {
	}

	/**
	 * @return whether ARCore is to be installed, or updated, before it runs
	 */
	public static boolean needsInstall(Context context) {
		return !isInstalled(context, ARCORE_PACKAGE)
				|| ArCoreApk.getInstance().checkAvailability(context)
				== ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD;
	}

	public static boolean hasPlayStore(Context context) {
		return isInstalled(context, PLAY_STORE_PACKAGE);
	}

	/**
	 * Makes the badges of arcore_install_badges open where to get ARCore, Google Play's only where
	 * it is there. Picking one tells about ARCore as its notice does, which the A.R. views then
	 * leave out.
	 *
	 * @param onPicked run as a badge is picked, before it opens, or null
	 */
	public static void bindBadges(View badges, Context context, Runnable onPicked) {
		View playStore = badges.findViewById(R.id.buttonPlayStore);
		playStore.setVisibility(hasPlayStore(context) ? View.VISIBLE : View.GONE);
		playStore.setOnClickListener(v -> {
			picked(context, onPicked);
			openPlayStore(context);
		});
		badges.findViewById(R.id.buttonApk).setOnClickListener(v -> {
			picked(context, onPicked);
			openApkPage(context);
		});
	}

	private static void picked(Context context, Runnable onPicked) {
		Configs.instance(context).setBoolean(Configs.ConfigKey.showArCoreNotice, false);
		if (onPicked != null) {
			onPicked.run();
		}
	}

	public static void openPlayStore(Context context) {
		try {
			open(context, "market://details?id=" + ARCORE_PACKAGE);
		} catch (ActivityNotFoundException e) {
			open(context, "https://play.google.com/store/apps/details?id=" + ARCORE_PACKAGE);
		}
	}

	public static void openApkPage(Context context) {
		open(context, APK_PAGE);
	}

	private static void open(Context context, String uri) {
		context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
	}

	private static boolean isInstalled(Context context, String packageName) {
		try {
			context.getPackageManager().getPackageInfo(packageName, 0);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}
}
