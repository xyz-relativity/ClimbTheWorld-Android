package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

public class RangingManager {
	private static final String TAG = RangingManager.class.getSimpleName();
	private final Context context;

	public RangingManager(Context context) {
		this.context = context;
	}

	@SuppressLint("MissingPermission")
	public void requestRanging(PeerHandle peerHandle, IRangingEvent rangingEvent) {
		Log.d(TAG, "Starting ranging");
		WifiRttManager rttManager =
				(WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

		if (rttManager != null && rttManager.isAvailable()) {
			RangingRequest rangingRequest = new RangingRequest.Builder()
					.addWifiAwarePeer(peerHandle)
					.build();

			rttManager.startRanging(rangingRequest, context.getMainExecutor(),
					new RangingResultCallback() {
						@Override
						public void onRangingFailure(int code) {
							Log.e(TAG, "Distance measurement failed with error code: " + code);
						}

						@Override
						public void onRangingResults(@NonNull List<RangingResult> results) {
							for (RangingResult result : results) {
								if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
									int distanceMm = result.getDistanceMm();
									double distanceMeters = distanceMm / 1000.0;
									rangingEvent.onRangingData(peerHandle, distanceMeters);
									Log.d(TAG, "Physical distance to peer: " + distanceMeters +
											" meters.");
								}
								Log.d(TAG, "Failed to measure distance. Status: " +
										result.getStatus());
							}
						}
					});
		}
	}

	public interface IRangingEvent {
		void onRangingData(PeerHandle peerHandle, double distanceMeters);
	}
}
