package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.DiscoverySession;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class PubSub {
	private static final String TAG = PubSub.class.getSimpleName();
	private static final int PING_INTERVAL_SECONDS = 2;
	private static final int TIMEOUT_INTERVAL = (PING_INTERVAL_SECONDS * 1000) * 3;
	private static final int RANGING_FREQUENCY = 10;
	protected final Context context;
	protected final String serviceName;
	protected final WifiAwareSession awareSession;
	protected final ITransportEvents transportEventsListener;
	protected final ITransportLayer transport;
	private int rangingCount = 0;
	private ScheduledExecutorService scheduler;
	private boolean isRunning;

	public PubSub(Context context, String serviceName, WifiAwareSession awareSession,
	              ITransportEvents transportEventsListener, ITransportLayer transport) {
		this.context = context;
		this.serviceName = serviceName;
		this.awareSession = awareSession;
		this.transportEventsListener = transportEventsListener;
		this.transport = transport;
		startHeartbeat();
	}

	public abstract void onInnerDestroy();

	public void onDestroy() {
		stopHeartbeat();
		onInnerDestroy();
	}

	protected abstract void onRangingData(PeerHandle peerHandle, double distanceMeters);

	protected abstract void onTimerEvent();

	protected abstract DiscoverySession getSession();

	@SuppressLint("MissingPermission")
	public void InitiateRanging(PeerHandle peerHandle) {
		if (rangingCount > 0) {
			rangingCount--;
			return;
		}

		rangingCount = RANGING_FREQUENCY;

		Log.d("RTT", "Starting ranging");
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

	private synchronized void startHeartbeat() {
		if (isRunning) return;

		// Create a single-threaded scheduler
		scheduler = Executors.newSingleThreadScheduledExecutor();
		isRunning = true;

		// Schedule the ping task to run every 1 second, with an initial delay of 0
		scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					onTimerEvent();
				} catch (Exception e) {
					Log.e(TAG, "Heartbeat task execution failed", e);
				}
			}
		}, 0, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);

		Log.d(TAG, "Heartbeat timer started.");
	}

	private synchronized void stopHeartbeat() {
		if (!isRunning || scheduler == null) return;

		scheduler.shutdown();
		try {
			// Wait briefly for the current task to finish if it's running
			if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
		}

		isRunning = false;
		Log.d(TAG, "Heartbeat timer stopped.");
	}

	protected void sendMessage(PeerHandle peerHandle, TransportMessage.Command command) {
		sendMessage(peerHandle, command, "");
	}

	protected void sendMessage(PeerHandle peerHandle, TransportMessage.Command command,
	                           String message) {
		Log.d(TAG, "Send Message: " + peerHandle + " " + command + " " + message);
		getSession().sendMessage(peerHandle, 0,
				Handshake.buildMessage(Handshake.ConnectionState.ACTIVE,
						TransportMessage.buildMessage(
								command,
								message)));
	}

	protected void sendHandshake(PeerHandle peerHandle, Handshake.ConnectionState state) {
		sendHandshake(peerHandle, state, "");
	}

	protected void sendHandshake(PeerHandle peerHandle, Handshake.ConnectionState state,
	                             String message) {
		Log.d(TAG, "Send Handshake: " + peerHandle + " " + state + " " + message);
		getSession().sendMessage(peerHandle, 0, Handshake.buildMessage(
				state, message));
	}

	protected static class ServicePubSub {
		public final String uuid;
		public final PeerHandle peerHandle;
		private long ping = System.currentTimeMillis();

		public ServicePubSub(String uuid, PeerHandle peerHandle) {
			this.uuid = uuid;
			this.peerHandle = peerHandle;
		}

		public void pong(long pong) {
			this.ping = pong;
		}

		public boolean stillAlive() {
			return Math.abs(System.currentTimeMillis() - ping) < TIMEOUT_INTERVAL;
		}
	}
}
