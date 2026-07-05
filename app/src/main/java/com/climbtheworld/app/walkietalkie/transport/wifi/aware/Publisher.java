package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;

import java.util.UUID;

public class Publisher {
	private static final String TAG = Publisher.class.getSimpleName();
	private final PubSubManager manager;
	private final ObservableHashMap<PeerHandle, PubSubID> subscribers = new ObservableHashMap<>();
	private PublishDiscoverySession publishSession;

	public Publisher(PubSubManager manager) {
		this.manager = manager;
	}

	// --- PUBLISHER ROLE (Host device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	public void startPublishing(Handler backgroundHandler) {
		PublishConfig config = new PublishConfig.Builder()
				.setServiceName(manager.getServiceName())
				.setServiceSpecificInfo(TransportMessage.buildMessage(
						TransportMessage.Command.INSTANCE, manager.getInstaceUUID().toString(),
						manager.getCallsign()))
				.setRangingEnabled(true)
				.build();

		subscribers.setListener(new ObservableHashMap.MapChangeListener<PeerHandle, PubSubID>() {
			@Override
			public void onMapChanged(PeerHandle key, PubSubID value,
			                         ObservableHashMap.MapEvent event) {
				manager.onPublisherSubscriberChanged(key, value, event);
			}
		});

		manager.getAwareSession().publish(config, new DiscoverySessionCallback() {
			@Override
			public void onPublishStarted(@NonNull PublishDiscoverySession session) {
				super.onPublishStarted(session);
				publishSession = session;
				Log.d(TAG,
						"Publish " + manager.getInstaceUUID() +
								" session started. Waiting for subscribers.." +
								".");
			}

			@Override
			public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
				super.onServiceLost(peerHandle, reason);
				Log.d(TAG,
						"Service lost, removing publisher: " + subscribers.get(peerHandle) +
								". Reason:" + reason);

				subscribers.remove(peerHandle);
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Publish session terminated.");
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);

				Log.d(TAG, "Publish " + manager.getInstaceUUID() + " message received: " +
						new String(message));

				TransportMessage transportMessage = TransportMessage.fromData(message);
				switch (transportMessage.command) {
					case INSTANCE:
						subscribers.put(peerHandle,
								new PubSubID(UUID.fromString(transportMessage.message[0]),
										transportMessage.message[1], 0));
						break;
				}

			}
		}, backgroundHandler);
	}

	public void onDestroy() {
		if (publishSession != null) {
			publishSession.close();
		}

		subscribers.clear();
	}

	public void onHeartBeat() {

	}

	public PublishDiscoverySession getDiscoverySession() {
		return publishSession;
	}

	public void onNetworkReady(PeerHandle publisherHandler) {
		publishSession.sendMessage(publisherHandler, 0, TransportMessage.buildMessage(
				TransportMessage.Command.READY));
	}
}
