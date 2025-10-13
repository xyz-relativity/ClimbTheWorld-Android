package com.climbtheworld.app.ask;

import android.app.Activity;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

		ActivityResultLauncher<String[]> permissionsLauncher =
				((ComponentActivity) activityRef.get()).registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
						result -> {
							List<String> allowed = new ArrayList<>();
							List<String> denied = new ArrayList<>();

							for (String permission: result.keySet())
							{
								if (Boolean.TRUE.equals(result.get(permission))) {
									allowed.add(permission);
								} else  {
									denied.add(permission);
								}
							}
							onCompleteListener.onCompleted(allowed.toArray(new String[0]), denied.toArray(new String[0]));
						});

		permissionsLauncher.launch(permissionsWithRational.keySet().toArray(new String[0]));
	}

	public interface IOnCompleteListener {
		void onCompleted(String[] granted, String[] denied);
	}
}
