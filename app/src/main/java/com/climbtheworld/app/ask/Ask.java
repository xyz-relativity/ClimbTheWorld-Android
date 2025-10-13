package com.climbtheworld.app.ask;

import android.app.Activity;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Ask {
	private static final String TAG = Ask.class.getSimpleName();
	private static WeakReference<Fragment> fragmentRef;
	private static WeakReference<Activity> activityRef;
	private static int id;
	private static boolean debug = false;
	private final Map<String, String> permissionsWithRational = new HashMap<>();
	private IOnCompleteListener onCompleteListener;
	private int waitingOn = 0;

	private Ask() {
		onCompleteListener = new IOnCompleteListener() {
			@Override
			public void onCompleted(String[] granted, String[] denied) {
				//empty
			}
		};

		debug = false;
		Random rand = new Random();
		id = rand.nextInt();
	}

	public static Ask on(Activity lActivity) {
		if (lActivity == null) {
			throw new IllegalArgumentException("Null Reference");
		}
		activityRef = new WeakReference<>(lActivity);

		return new Ask();
	}

	public static Ask on(Fragment lFragment) {
		if (lFragment == null) {
			throw new IllegalArgumentException("Null Reference");
		}
		fragmentRef = new WeakReference<>(lFragment);
		return new Ask();
	}

	private static Activity getActivity() {
		return fragmentRef != null ? fragmentRef.get().getActivity() : activityRef.get();
	}

	public Ask addPermission(@NonNull String permission)
	{
		return addPermission(permission, null);
	}

	public Ask addPermission(@NonNull String permission, @StringRes int rationalMessages)
	{
		return addPermission(permission, getActivity().getString(rationalMessages));
	}

	public Ask addPermission(@NonNull String permission, String rationalMessages)
	{
		permissionsWithRational.put(permission, rationalMessages);
		return this;
	}

	public Ask debug(boolean lDebug) {
		debug = lDebug;
		return this;
	}

	public Ask id(int lId) {
		id = lId;
		return this;
	}

	public Ask onCompleteListener(IOnCompleteListener listener) {
		this.onCompleteListener = listener;
		return this;
	}

	public void go() {
		if (debug) {
			Log.d(TAG, "request id :: " + id);
		}

		waitingOn = permissionsWithRational.size();

		for (String permission: permissionsWithRational.keySet()) {
			ActivityResultLauncher<String> requestPermissionLauncher = ((ComponentActivity) activityRef.get()).registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
				final String askedForPermission = permission;
				@Override
				public void onActivityResult(Boolean isGranted) {
					waitingOn--;

					if (isGranted) {
						// Permission is granted. Continue the action or workflow in your app.
					} else {
						// Explain to the user why the feature is unavailable.
						// You might want to show a UI with a rationale and an option to go to settings.
					}

					if (waitingOn == 0) {
						onCompleteListener.onCompleted(null, null);
					}
				}
			});

			requestPermissionLauncher.launch(permission);
		}
	}

	public interface IOnCompleteListener {
		void onCompleted(String[] granted, String[] denied);
	}
}
