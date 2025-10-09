package com.climbtheworld.app.ask;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Ask {

	private static final String TAG = Ask.class.getSimpleName();
	private static WeakReference<Fragment> fragmentRef;
	private static WeakReference<Activity> activityRef;
	private static Map<String, Method> permissionMethodMapRef;
	private static int id;
	private static boolean debug = false;
	private static Receiver receiver;
	private final Map<String, String> permissionsMap = new HashMap<>();
	private IOnCompleteListener onCompleteListener;

	private Ask() {
		onCompleteListener = new IOnCompleteListener() {
			@Override
			public void onCompleted(String[] granted, String[] denied) {
				//empty
			}
		};

		permissionMethodMapRef = new HashMap<>();
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
		permissionsMap.put(permission, rationalMessages);
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

		String[] permissions = permissionsMap.keySet().toArray(new String[0]);
		String[] rationalMessages = permissionsMap.values().toArray(new String[0]);

		receiver = new Receiver(onCompleteListener);
		ContextCompat.registerReceiver(getActivity(), receiver, new IntentFilter(Constants.BROADCAST_FILTER), ContextCompat.RECEIVER_NOT_EXPORTED);
		Intent intent = new Intent(getActivity(), AskActivity.class);
		intent.putExtra(Constants.PERMISSIONS, permissions);
		intent.putExtra(Constants.RATIONAL_MESSAGES, rationalMessages);
		intent.putExtra(Constants.REQUEST_ID, id);
		getActivity().startActivity(intent);
	}

	public interface IOnCompleteListener {
		void onCompleted(String[] granted, String[] denied);
	}

	public static class Receiver extends BroadcastReceiver {

		private final IOnCompleteListener onCompleteListener;

		public Receiver(IOnCompleteListener onCompleteListener) {
			this.onCompleteListener = onCompleteListener;
		}

		@Override
		public void onReceive(Context lContext, Intent intent) {
			try {
				int requestId = intent.getIntExtra(Constants.REQUEST_ID, 0);

				if (id != requestId) {
					return;
				}

				String[] permissions = intent.getStringArrayExtra(Constants.PERMISSIONS);
				int[] grantResults = intent.getIntArrayExtra(Constants.GRANT_RESULTS);

				List<String> grantedPermissions = new ArrayList<>();
				List<String> deniedPermissions = new ArrayList<>();
				for (int i = 0; i < permissions.length; i++) {
					if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
						grantedPermissions.add(permissions[i]);
					} else {
						deniedPermissions.add(permissions[i]);
					}
				}

				onCompleteListener.onCompleted(grantedPermissions.toArray(new String[0]), deniedPermissions.toArray(new String[0]));
			} finally {
				if (receiver != null) {
					getActivity().unregisterReceiver(receiver);
				}
				receiver = null;
			}
		}
	}
}
