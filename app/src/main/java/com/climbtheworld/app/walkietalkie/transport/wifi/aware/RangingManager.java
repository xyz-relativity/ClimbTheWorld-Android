package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RangingManager {
	private static final String TAG = RangingManager.class.getSimpleName();
	private final Context context;
	private final AtomicBoolean requestInFlight = new AtomicBoolean(false);

	public RangingManager(Context context) {
		this.context = context.getApplicationContext();
	}

	@SuppressLint("MissingPermission")
	public void requestRanging(Collection<PeerHandle> peerHandles, IRangingEvent rangingEvent) {
		Set<PeerHandle> peers = new LinkedHashSet<>(peerHandles);
		peers.remove(null);
		if (peers.isEmpty()) {
			return;
		}

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
			Log.w(TAG, "Wi-Fi RTT is not supported by this device");
			return;
		}

		WifiRttManager rttManager =
				(WifiRttManager) context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
		if (rttManager == null) {
			Log.w(TAG, "Wi-Fi RTT service is unavailable");
			return;
		}
		if (!rttManager.isAvailable()) {
			Log.w(TAG, "Wi-Fi RTT is temporarily unavailable; Wi-Fi and location must be enabled");
			return;
		}
		if (!requestInFlight.compareAndSet(false, true)) {
			Log.d(TAG, "Skipping ranging because another request is still in flight");
			return;
		}

		try {
			RangingRequest.Builder requestBuilder = new RangingRequest.Builder();
			for (PeerHandle peer : peers) {
				requestBuilder.addWifiAwarePeer(peer);
			}

			Log.d(TAG, "Starting ranging for " + peers.size() + " peer(s)");
			rttManager.startRanging(requestBuilder.build(), context.getMainExecutor(),
					new RangingResultCallback() {
						@Override
						public void onRangingFailure(int code) {
							requestInFlight.set(false);
							Log.e(TAG, "Distance measurement request failed: " +
									requestFailureToString(code) + " (" + code + ")");
						}

						@Override
						public void onRangingResults(@NonNull List<RangingResult> results) {
							requestInFlight.set(false);
							Log.d(TAG, "Received " + results.size() + " ranging result(s) for " +
									peers.size() + " peer(s)");

							for (RangingResult result : results) {
								PeerHandle peerHandle = result.getPeerHandle();
								if (peerHandle == null) {
									Log.w(TAG, "Ignoring ranging result without a Wi-Fi Aware peer handle");
									continue;
								}

								if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
									double distanceMeters = result.getDistanceMm() / 1000.0;
									Log.d(TAG, "Distance to peer: " + distanceMeters + "m, stddev=" +
											result.getDistanceStdDevMm() + "mm, rssi=" + result.getRssi() +
											"dBm, measurements=" + result.getNumSuccessfulMeasurements() +
											"/" + result.getNumAttemptedMeasurements());
									rangingEvent.onRangingData(peerHandle, distanceMeters);
								} else {
									Log.w(TAG, "Failed to measure distance to peer. Status: " +
											resultStatusToString(result.getStatus()) + " (" +
											result.getStatus() + ")");
								}
							}
						}
					});
		} catch (SecurityException | IllegalArgumentException e) {
			requestInFlight.set(false);
			Log.e(TAG, "Unable to start Wi-Fi RTT ranging", e);
		} catch (RuntimeException e) {
			requestInFlight.set(false);
			Log.e(TAG, "Unexpected error while starting Wi-Fi RTT ranging", e);
		}
	}

	private static String requestFailureToString(int code) {
		switch (code) {
			case RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE:
				return "RTT not available";
			case RangingResultCallback.STATUS_CODE_FAIL:
				return "generic failure";
			default:
				return "unknown failure";
		}
	}

	private static String resultStatusToString(int status) {
		switch (status) {
			case RangingResult.STATUS_FAIL:
				return "generic peer failure";
			case RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC:
				return "peer does not support IEEE 802.11mc";
			default:
				return "unknown peer failure";
		}
	}

	public interface IRangingEvent {
		void onRangingData(PeerHandle peerHandle, double distanceMeters);
	}
}
