package com.climbtheworld.app.ask;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.climbtheworld.app.ask.annotations.AskDenied;
import com.climbtheworld.app.ask.annotations.AskGranted;
import com.climbtheworld.app.ask.annotations.AskGrantedAll;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@SuppressWarnings("unused")
public class Ask {

    private String[] permissions;
    private String[] rationalMessages;
    private static final String TAG = Ask.class.getSimpleName();
    private static final String ALL_PERMISSIONS = "All";
    private static WeakReference<Permission> permissionObjRef;
    private static WeakReference<Fragment> fragmentRef;
    private static WeakReference<Activity> activityRef;
    private static Map<String, Method> permissionMethodMapRef;
    private static int id;
    private static boolean debug = false;
    private static Receiver receiver;

    private Ask() {
        permissionMethodMapRef = new HashMap<>();
        debug = false;
        permissionObjRef = null;
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

    public Ask forPermissions(@NonNull @Size(min = 1) String... permissions) {
        if (permissions.length == 0) {
            throw new IllegalArgumentException("The permissions to request are missing");
        }
        this.permissions = permissions;
        return this;
    }

    public Ask withRationales(@StringRes int... rationalMessages) {
        if (rationalMessages.length == 0) {
            throw new IllegalArgumentException("The Rationale Messages are missing");
        }
        String[] msges = new String[rationalMessages.length];
        for (int i = 0; i < rationalMessages.length; i++) {
            msges[i] = getActivity().getString(rationalMessages[i]);
        }
        this.rationalMessages = msges;
        return this;
    }

    public Ask withRationales(@NonNull String... rationalMessages) {
        if (rationalMessages.length == 0) {
            throw new IllegalArgumentException("The Rationale Messages are missing");
        }
        this.rationalMessages = rationalMessages;
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

    private static Activity getActivity() {
        return fragmentRef != null ? fragmentRef.get().getActivity() : activityRef.get();
    }

    public void go() {
        if (debug) {
            Log.d(TAG, "request id :: " + id);
        }
        getAnnotatedMethod();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (permissionObjRef != null && permissionObjRef.get() != null) {
                permissionObjRef.get().granted(Arrays.asList(permissions));
                permissionObjRef.get().denied(new ArrayList<String>());
            }
            for (String permission : permissions) {
                invokeMethod(permission, true);
            }
            invokeMethod("All", true);
        } else {
            receiver = new Receiver();
            getActivity().registerReceiver(receiver, new IntentFilter(Constants.BROADCAST_FILTER));
            Intent intent = new Intent(getActivity(), AskActivity.class);
            intent.putExtra(Constants.PERMISSIONS, permissions);
            intent.putExtra(Constants.RATIONAL_MESSAGES, rationalMessages);
            intent.putExtra(Constants.REQUEST_ID, id);
            getActivity().startActivity(intent);
        }
    }

    public Ask when(@Nullable Permission permission) {
        permissionObjRef = new WeakReference<>(permission);
        return this;
    }

    interface Permission {
        void granted(List<String> permissions);

        void denied(List<String> permissions);

        void grantedAll();
    }

    public static class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context lContext, Intent intent) {
            try {
                boolean grantedAll = true;
                int requestId = intent.getIntExtra(Constants.REQUEST_ID, 0);
                if (debug) {
                    Log.d(TAG, "request id :: " + id + ",  received request id :: " + requestId);
                }

                if (id != requestId) {
                    return;
                }
                String[] permissions = intent.getStringArrayExtra(Constants.PERMISSIONS);
                int[] grantResults = intent.getIntArrayExtra(Constants.GRANT_RESULTS);
                List<String> grantedPermissions = new ArrayList<>();
                List<String> deniedPermissions = new ArrayList<>();
                for (int i = 0; i < permissions.length; i++) {
                    boolean isGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    invokeMethod(permissions[i], isGranted);
                    if (isGranted) {
                        grantedPermissions.add(permissions[i]);
                    } else {
                        deniedPermissions.add(permissions[i]);
                        grantedAll = false;
                    }
                }
                //if all permissions are granted
                if (grantedAll) {
                    invokeMethod(ALL_PERMISSIONS, true);
                }
                if (permissionObjRef != null && permissionObjRef.get() != null) {
                    permissionObjRef.get().denied(deniedPermissions);
                    permissionObjRef.get().granted(grantedPermissions);
                    if (deniedPermissions.size() == 0)
                        permissionObjRef.get().grantedAll();
                }
            } finally {
                if (permissionMethodMapRef != null) {
                    permissionMethodMapRef.clear();
                }
                permissionMethodMapRef = null;

                if (receiver != null) {
                    getActivity().unregisterReceiver(receiver);
                }
                receiver = null;
            }
        }
    }

    private static void getAnnotatedMethod() {

        permissionMethodMapRef.clear();
        Method[] methods = fragmentRef != null ? fragmentRef.get().getClass().getMethods() : activityRef.get().getClass().getMethods();
        for (Method method : methods) {
            AskDenied askDenied = method.getAnnotation(AskDenied.class);
            AskGranted askGranted = method.getAnnotation(AskGranted.class);
            AskGrantedAll askGrantedAll = method.getAnnotation(AskGrantedAll.class);
            if (askDenied != null) {
                int lId = askDenied.id() != -1 ? askDenied.id() : id;
                permissionMethodMapRef.put(false + "_" + askDenied.value(), method);
            }
            if (askGranted != null) {
                int lId = askGranted.id() != -1 ? askGranted.id() : id;
                permissionMethodMapRef.put(true + "_" + askGranted.value(), method);
            }
            if (askGrantedAll != null) {
                int lId = askGrantedAll.id() != -1 ? askGrantedAll.id() : id;
                permissionMethodMapRef.put(true + "_" + askGrantedAll.value(), method);
            }
        }
        if (debug) {
            Log.d(TAG, "annotated methods map :: " + permissionMethodMapRef);
        }
    }

    private static void invokeMethod(String permission, boolean isGranted) {
        String key = isGranted + "_" + permission;
        String val = isGranted ? "Granted" : "Denied";
        try {
            if (debug) {
                Log.d(TAG, "invoke method for key :: " + key);
            }
            if (permissionMethodMapRef.containsKey(key)) {
                try {
                    permissionMethodMapRef.get(key).invoke(fragmentRef != null ? fragmentRef.get() : activityRef.get(), id);
                } catch (IllegalArgumentException ex) {
                    permissionMethodMapRef.get(key).invoke(fragmentRef != null ? fragmentRef.get() : activityRef.get());
                }
            } else if (debug) {
                Log.w(TAG, "No method found to handle the " + permission + " " + val + " case. Please check for the detail here https://github.com/00ec454/Ask");
            }
        } catch (Exception e) {
            if (debug)
                Log.e(TAG, e.getMessage(), e);
        }
    }
}
