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
	private ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender;

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
				messageSender = createMessageSender(backgroundHandler);
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

				ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
				if (sender != null) {
					sender.cancelPeer(peerHandle);
				}
				subscribers.remove(peerHandle);
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Publish session terminated.");
				manager.onDiscoverySessionTerminated("publish");
			}

			@Override
			public void onMessageSendSucceeded(int messageId) {
				super.onMessageSendSucceeded(messageId);
				ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
				if (sender != null) {
					sender.onSendSucceeded(messageId);
				}
			}

			@Override
			public void onMessageSendFailed(int messageId) {
				super.onMessageSendFailed(messageId);
				ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
				if (sender != null) {
					sender.onSendFailed(messageId);
				}
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
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		messageSender = null;
		if (sender != null) {
			sender.close();
		}
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
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		if (sender == null) {
			Log.w(TAG, "Cannot send READY before the publish session starts");
			return;
		}
		sender.send(publisherHandler, TransportMessage.Command.READY,
				TransportMessage.buildMessage(TransportMessage.Command.READY));
	}

	private ReliableMessageSender<PeerHandle, TransportMessage.Command> createMessageSender(
			Handler backgroundHandler) {
		return new ReliableMessageSender<>(new HandlerRetryScheduler(backgroundHandler),
				(peer, messageId, payload) -> {
					PublishDiscoverySession session = publishSession;
					if (session == null) {
						throw new IllegalStateException("Publish session is unavailable");
					}
					session.sendMessage(peer, messageId, payload);
				},
				(peer, messageType, attempts) -> Log.e(TAG,
						"Failed to send " + messageType + " after " + attempts + " attempts"));
	}
}
