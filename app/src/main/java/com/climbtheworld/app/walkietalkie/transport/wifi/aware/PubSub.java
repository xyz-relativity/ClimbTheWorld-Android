package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.WifiAwareSession;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.transport.Handshake;

import java.util.List;

public abstract class PubSub {

	protected final Context context;
	protected final String serviceName;
	protected final WifiAwareSession awareSession;
	protected final ITransportEvents transportEventsListener;
	protected final ITransportLayer transport;

	public PubSub(Context context, String serviceName, WifiAwareSession awareSession,
	              ITransportEvents transportEventsListener, ITransportLayer transport) {
		this.context = context;
		this.serviceName = serviceName;
		this.awareSession = awareSession;
		this.transportEventsListener = transportEventsListener;
		this.transport = transport;
	}

	public abstract void onDestroy();

	protected abstract void onRangingData(PeerHandle peerHandle, double distanceMeters);

	protected abstract void onTimerEvent();

	@SuppressLint("MissingPermission")
	public void startRanging(PeerHandle peerHandle) {
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
							Log.e("RTT", "Distance measurement failed with error code: " + code);
						}

						@Override
						public void onRangingResults(List<RangingResult> results) {
							for (RangingResult result : results) {
								if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
									int distanceMm = result.getDistanceMm();
									double distanceMeters = distanceMm / 1000.0;
									onRangingData(peerHandle, distanceMeters);
									Log.i("RTT", "Physical distance to peer: " + distanceMeters +
											" meters.");
								}
							}
						}
					});
		}
	}

	protected static class WifiDirectNode {
		String uuid;
		Handshake.ConnectionState state = Handshake.ConnectionState.IDENTITY;
		PeerHandle subscriberPeerHandle;
		String callsign;
		double distanceMeters;
		int ping = 0;

		WifiDirectNode(String uuid, PeerHandle peerHandle) {
			this.uuid = uuid;
			this.subscriberPeerHandle = peerHandle;
		}
	}
}
