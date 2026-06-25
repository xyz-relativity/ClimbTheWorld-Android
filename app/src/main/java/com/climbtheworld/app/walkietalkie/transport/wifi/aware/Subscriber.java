package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.transport.Handshake;
import com.climbtheworld.app.walkietalkie.transport.TransportUtilities;

import java.util.HashMap;
import java.util.Map;

public class Subscriber extends PubSub {
	private static final String TAG = Subscriber.class.getSimpleName();
	private final Map<PeerHandle, ServicePublisher> publishers = new HashMap<>();
	private SubscribeDiscoverySession clientSession;

	public Subscriber(Context context, String serviceName,
	                  WifiAwareSession awareSession,
	                  ITransportEvents transportEventsListener,
	                  ITransportLayer transport) {
		super(context, serviceName, awareSession, transportEventsListener, transport);
	}

	// --- SUBSCRIBER ROLE (Client device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	public void startSubscribing(String channel, String callsign, String appUUID) {
		SubscribeConfig config = new SubscribeConfig.Builder()
				.setServiceName(serviceName)
				.setMinDistanceMm(0)
				.setMaxDistanceMm(Integer.MAX_VALUE)
				.build();

		awareSession.subscribe(config, new DiscoverySessionCallback() {
			@Override
			public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
				super.onSubscribeStarted(session);
				clientSession = session;
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Publish session terminated.");
				publishers.clear();
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);

				publishers.put(peerHandle,
						new ServicePublisher(new String(serviceSpecificInfo), peerHandle));

				startRanging(peerHandle);

				Log.d(TAG, "Publisher service discovered!");

				clientSession.sendMessage(peerHandle, 0,
						(Handshake.buildMessage(Handshake.ConnectionState.IDENTITY, appUUID)));
			}

			@Override
			public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
				Log.d(TAG,
						"Service lost, removing publisher: " + peerHandle + ". Reason:" + reason);
				publishers.remove(peerHandle);
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);

				if (!publishers.containsKey(peerHandle)) {
					return;
				}

				Handshake handshake = Handshake.fromData(message);

				Log.d(TAG, "Received reply from publisher: " + handshake.data);

				switch (handshake.connectionState) {
					case AUTH:
						if (TransportUtilities.computeDigest(appUUID + channel)
								.equals(handshake.data)) {
							clientSession.sendMessage(peerHandle, 0, Handshake.buildMessage(
									Handshake.ConnectionState.AUTH,
									TransportUtilities.computeDigest(
											publishers.get(peerHandle).uuid + channel)));
						}
						break;
					case ACTIVE:
						TransportMessage transportMessage =
								TransportMessage.fromString(handshake.data);
						switch (transportMessage.command) {
							case CALLSIGH:
								clientSession.sendMessage(peerHandle, 0,
										Handshake.buildMessage(Handshake.ConnectionState.ACTIVE,
												TransportMessage.buildMessage(
														TransportMessage.Command.CALLSIGH,
														callsign)));
								break;
						}
						break;
					case DISCONNECTING:
						publishers.remove(peerHandle);
						break;
				}
			}
		}, new Handler(Looper.getMainLooper()));
	}

	@Override
	public void onInnerDestroy() {
		for (ServicePublisher node : publishers.values()) {
			clientSession.sendMessage(node.peerHandle, 0,
					Handshake.buildMessage(Handshake.ConnectionState.DISCONNECTING));
		}

		if (clientSession != null) {
			clientSession.close();
		}
	}

	@Override
	protected void onRangingData(PeerHandle peerHandle, double distanceMeters) {
		Log.i(TAG, "------------------------> Physical distance to peer: " + distanceMeters +
				" meters.");
	}

	@Override
	protected void onTimerEvent() {
		for (ServicePublisher pub : publishers.values()) {
			clientSession.sendMessage(pub.peerHandle, 0,
					Handshake.buildMessage(Handshake.ConnectionState.ACTIVE,
							TransportMessage.buildMessage(
									TransportMessage.Command.PING)));
		}
	}


	protected static class ServicePublisher extends ServicePubSub {
		public ServicePublisher(String uuid, PeerHandle peerHandle) {
			super(uuid, peerHandle);
		}
	}
}
