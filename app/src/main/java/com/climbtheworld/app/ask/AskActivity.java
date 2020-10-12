package com.climbtheworld.app.ask;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.ListViewItemBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AskActivity extends AppCompatActivity {

	private static final int PERMISSION_REQUEST = 100;
	@SuppressWarnings("unused")
	private static final String TAG = AskActivity.class.getSimpleName();
	private static final String NEEDED_PERMISSIONS = "needed_permissions";
	private static final String SHOW_RATIONAL_FOR = "show_rational_for";
	private static final String RATIONALE_MESSAGES_TO_SHOW = "rational_messages";
	private String[] permissions;
	private String[] rationaleMessages;
	private int requestId;
	private PackageManager packageManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

		packageManager = this.getPackageManager();

		init(savedInstanceState);
		getPermissions();
	}

	private void init(Bundle state) {
		if (state != null) {
			permissions = state.getStringArray(Constants.PERMISSIONS);
			rationaleMessages = state.getStringArray(Constants.RATIONAL_MESSAGES);
			requestId = state.getInt(Constants.REQUEST_ID);
		} else {
			Intent intent = getIntent();
			permissions = intent.getStringArrayExtra(Constants.PERMISSIONS);
			rationaleMessages = intent.getStringArrayExtra(Constants.RATIONAL_MESSAGES); // To show rational messages.
			requestId = intent.getExtras().getInt(Constants.REQUEST_ID, 0);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putStringArray(Constants.PERMISSIONS, permissions);
		state.putStringArray(Constants.RATIONAL_MESSAGES, rationaleMessages);
		state.putInt(Constants.REQUEST_ID, requestId);
	}

	private void getPermissions() {

		Map<String, List<String>> map = separatePermissions(permissions, rationaleMessages);
		List<String> neededPermissions = map.get(NEEDED_PERMISSIONS);
		final List<String> showRationaleFor = map.get(SHOW_RATIONAL_FOR);
		List<String> rationalMessagesToShow = map.get(RATIONALE_MESSAGES_TO_SHOW);

		if (showRationaleFor != null && showRationaleFor.size() > 0 && rationalMessagesToShow != null && rationalMessagesToShow.size() > 0) {
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(getString(R.string.permissions_rational_title));

			Drawable icon = getDrawable(android.R.drawable.ic_dialog_info);
			icon.setTint(getResources().getColor(android.R.color.holo_green_light));
			alertDialog.setIcon(icon);

			alertDialog.setView(buildRationalMessage(showRationaleFor, rationalMessagesToShow));

			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getResources().getString(R.string.done), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ActivityCompat.requestPermissions(AskActivity.this, showRationaleFor.toArray(new String[0]), PERMISSION_REQUEST);
					dialog.dismiss();
				}
			});

			alertDialog.create();
			alertDialog.show();
		} else if (neededPermissions != null && neededPermissions.size() > 0) {
			ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[showRationaleFor.size()]), PERMISSION_REQUEST);
		} else {
			int[] result = new int[permissions.length];
			Arrays.fill(result, PackageManager.PERMISSION_GRANTED);
			broadcast(permissions, result);
			finish();
		}
	}

	private Map<String, List<String>> separatePermissions(String[] permissions, String[] rationalMessages) {
		Map<String, List<String>> map = new HashMap<>();
		List<String> neededPermissions = new ArrayList<>();
		List<String> showRationalsFor = new ArrayList<>();
		List<String> neededRationalMessages = new ArrayList<>();
		for (int i = 0; i < permissions.length; i++) {
			String permission = permissions[i];
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				neededPermissions.add(permission);
			}
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
				showRationalsFor.add(permission);
				// if multiple rational message corresponding to each permission
				if (rationalMessages != null && rationalMessages.length == permissions.length) {
					if (!neededRationalMessages.contains(rationalMessages[i])) {
						neededRationalMessages.add(rationalMessages[i]);
					}
				}
			}
		}
		// if rational message is only one
		if (rationalMessages != null && rationalMessages.length == 1 && !neededRationalMessages.contains(rationalMessages[0])) {
			neededRationalMessages.add(rationalMessages[0]);
		}
		map.put(NEEDED_PERMISSIONS, neededPermissions);
		map.put(SHOW_RATIONAL_FOR, showRationalsFor);
		map.put(RATIONALE_MESSAGES_TO_SHOW, neededRationalMessages);
		return map;
	}

	@NonNull
	private View buildRationalMessage(@NonNull List<String> permissions, @NonNull List<String> messages) {
		LayoutInflater inflater = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE));
		View ll = inflater.inflate(R.layout.fragment_dialog_permissions, null);
		for (int i = 0; i < messages.size(); ++i) {
			ListViewItemBuilder builder = ListViewItemBuilder.getPaddedBuilder(this);

			try {
				PermissionInfo permissionInfo = packageManager.getPermissionInfo(permissions.get(i), PackageManager.GET_META_DATA);
				if (permissionInfo.icon == 0) {
					builder.setIcon(getGenericLocation(permissions.get(i)));
				} else {
					builder.setIcon(getDrawable(permissionInfo.icon));
				}

				builder.setTitle(getString(permissionInfo.labelRes).toUpperCase());
			} catch (PackageManager.NameNotFoundException e) {
				//empty
			}

			builder.setDescription(messages.get(i));

			((ViewGroup) ll.findViewById(R.id.dialogContent)).addView(builder.build());
		}
		return ll;
	}

	private Drawable getGenericLocation(String permissionName) throws PackageManager.NameNotFoundException {
		switch (permissionName) {
			case Manifest.permission.ACCESS_FINE_LOCATION:
				return getDrawable(android.R.drawable.ic_menu_mylocation);
			case Manifest.permission.CAMERA:
				return getDrawable(android.R.drawable.ic_menu_camera);
			case Manifest.permission.RECORD_AUDIO:
				return getDrawable(android.R.drawable.ic_btn_speak_now);
			default:
				return getDrawable(android.R.drawable.ic_menu_help);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST: {
				broadcast(permissions, grantResults);
				finish();
			}
		}
	}

	private void broadcast(String[] permissions, int[] grantResults) {
		if (grantResults.length > 0) {
			Intent intent = new Intent();
			intent.setAction(Constants.BROADCAST_FILTER);
			intent.putExtra(Constants.PERMISSIONS, permissions);
			intent.putExtra(Constants.GRANT_RESULTS, grantResults);
			intent.putExtra(Constants.REQUEST_ID, requestId);
			sendBroadcast(intent);
		}
	}
}
