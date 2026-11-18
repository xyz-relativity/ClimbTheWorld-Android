package com.climbtheworld.app.map.widget;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.climbtheworld.app.utils.Globals;

import org.maplibre.android.MapLibre;

/**
 * Keeps MapLibre off the network while map tiles may not be downloaded: over mobile data, unless
 * the user allows it, see Globals.allowMapDownload. The maps then draw from MapLibre's cache
 * only, as the OSMDroid map did with setUseDataConnection. Otherwise MapLibre finds out about the
 * network itself.
 * <p>
 * MapLibre's connection state is the same for every map of the app, so the maps shown share
 * this: it is set again whenever one shows, see {@link #onResume}, and as the network changes
 * while any does. Only to be used from the main thread, after MapLibre.getInstance.
 */
final class MapDataConnection {
	// The maps shown, see onResume. The network is only watched while there is one.
	private static int shownMaps;
	private static ConnectivityManager.NetworkCallback networkWatch;

	private MapDataConnection() {
		//hide constructor.
	}

	/**
	 * A map shows: sets MapLibre's connection, which the settings may have changed since, and
	 * watches the network until {@link #onPause} is called as many times.
	 */
	static void onResume(Context context) {
		Context application = context.getApplicationContext();
		apply(application);
		if (shownMaps++ > 0) {
			return;
		}
		networkWatch = new ConnectivityManager.NetworkCallback() {
			// The capabilities tell whether the network is metered: told when it becomes the
			// default one, and when that changes, as when a hotspot turns metered.
			@Override
			public void onCapabilitiesChanged(@NonNull Network network,
			                                  @NonNull NetworkCapabilities capabilities) {
				apply(application);
			}

			@Override
			public void onLost(@NonNull Network network) {
				apply(application);
			}
		};
		// Told on the main thread, which MapLibre is set from.
		application.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(
				networkWatch, new Handler(Looper.getMainLooper()));
	}

	/**
	 * A map is no longer shown, see {@link #onResume}.
	 */
	static void onPause(Context context) {
		if (shownMaps == 0 || --shownMaps > 0) {
			return;
		}
		context.getApplicationContext().getSystemService(ConnectivityManager.class)
				.unregisterNetworkCallback(networkWatch);
		networkWatch = null;
	}

	/**
	 * Offline while map tiles may not be downloaded; otherwise, null hands the state back to
	 * MapLibre, which follows the network. Also for a map before it shows, so that it requests
	 * nothing it may not, should it load its style first.
	 */
	static void apply(Context context) {
		MapLibre.setConnected(Globals.allowMapDownload(context) ? null : false);
	}
}
