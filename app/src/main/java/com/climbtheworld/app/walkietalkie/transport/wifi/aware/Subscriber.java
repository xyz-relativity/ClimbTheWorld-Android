package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;

import java.util.Map;
import java.util.UUID;

public class Subscriber {
	private static final String TAG = Subscriber.class.getSimpleName();
	private final PubSubManager manager;
	private final ObservableHashMap<PeerHandle, PubSubID> publishers = new ObservableHashMap<>();
	private SubscribeDiscoverySession subscribeSession;

	public Subscriber(PubSubManager manager) {
		this.manager = manager;
	}

	// --- SUBSCRIBER ROLE (Client device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	public void startSubscribing(Handler backgroundHandler) {
		SubscribeConfig config = new SubscribeConfig.Builder()
				.setServiceName(manager.getServiceName())
				.build();

		publishers.setListener(new ObservableHashMap.MapChangeListener<PeerHandle, PubSubID>() {
			@Override
			public void onMapChanged(PeerHandle peerHandle, PubSubID value,
			                         ObservableHashMap.MapEvent event) {
				manager.onSubscriberPublisherChanged(peerHandle, value, event);
			}
		});

		manager.getAwareSession().subscribe(config, new DiscoverySessionCallback() {
			@Override
			public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
				super.onSubscribeStarted(session);
				subscribeSession = session;
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Subscribe session terminated.");
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);
				Log.d(TAG, "Publisher service " + new String(serviceSpecificInfo) + " discovered" +
						"!");

				TransportMessage message = TransportMessage.fromData(serviceSpecificInfo);

				publishers.put(peerHandle, new PubSubID(
						UUID.fromString(message.message[0]), message.message[1], 0));


				sendCallsign(peerHandle);
			}

			@Override
			public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
				Log.d(TAG,
						"Service lost, removing publisher: " + publishers.get(peerHandle) +
								". Reason:" + reason);

				publishers.remove(peerHandle);
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);

				Log.d(TAG, "Subscriber " + manager.getInstaceUUID().toString() +
						" message received: " +
						new String(message));

				TransportMessage transportMessage = TransportMessage.fromData(message);
				switch (transportMessage.command) {
					case READY:
						manager.onNetworkReady(peerHandle);
						break;
				}
			}
		}, backgroundHandler);
	}

	public void updateCallsign() {
		for (PeerHandle peerHandle : publishers.keySet()) {
			sendCallsign(peerHandle);
		}

	}

	private void sendCallsign(PeerHandle peerHandle) {
		subscribeSession.sendMessage(peerHandle, 0, TransportMessage.buildMessage(
				TransportMessage.Command.INSTANCE, manager.getInstaceUUID().toString(),
				manager.getCallsign()));
	}

	public void onDestroy() {
		if (subscribeSession != null) {
			subscribeSession.close();
		}

		publishers.clear();
	}

	public void onHeartBeat() {
		for (Map.Entry<PeerHandle, PubSubID> peer : publishers.entrySet()) {
			manager.getRangingManager()
					.requestRanging(peer.getKey(), new RangingManager.IRangingEvent() {
						@Override
						public void onRangingData(PeerHandle peerHandle, double distanceMeters) {
							Log.d(TAG, "Got ranging results: " + distanceMeters);
							publishers.put(peerHandle,
									new PubSubID(peer.getValue().uuid,
											peer.getValue().callsign, distanceMeters));
						}
					});
		}
	}

	public DiscoverySession getDiscoverySession() {
		return subscribeSession;
	}
}
