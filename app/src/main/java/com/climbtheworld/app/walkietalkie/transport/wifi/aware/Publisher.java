package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;

public class Publisher {
	private static final String TAG = Publisher.class.getSimpleName();
	private final PubSubManager manager;
	private final ObservableHashMap<PeerHandle, PubSubID> subscribers = new ObservableHashMap<>();
	private PublishDiscoverySession publishSession;
	private ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender;
	// Whether the callsign changed since the session published it in its service info, which is
	// not published again: a peer finding this device then reads a callsign it no longer has.
	// Set by the screen's thread and read by the session's.
	private volatile boolean callsignChanged;

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

		manager.getAwareSession().publish(config, new MessageSessionCallback(TAG,
				"Publish " + manager.getInstaceUUID()) {
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
				forgetPeer(peerHandle);
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Publish session terminated.");
				manager.onDiscoverySessionTerminated("publish");
			}

			@Override
			protected ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender() {
				return messageSender;
			}

			@Override
			protected void onMessage(PeerHandle peerHandle, TransportMessage transportMessage) {
				switch (transportMessage.command) {
					case INSTANCE:
						// Only the subscribing side measures the distance: this side has none
						// for the peer, and 0 would be shown as a measured one.
						PubSubID subscriber =
								transportMessage.toPubSubID(PubSubID.UNKNOWN_DISTANCE);
						if (subscriber == null) {
							Log.w(TAG, "Ignoring malformed instance message.");
							break;
						}
						subscribers.put(peerHandle, subscriber);
						if (callsignChanged) {
							// The peer found this device, or found it again, and read the
							// callsign of the service info.
							sendCallsign(peerHandle);
						}
						break;
				}
			}
		}, backgroundHandler);
	}

	/**
	 * Tells the callsign of the moment to the peers that initiated the data path to this device.
	 * They are the ones the subscriber cannot tell, see Subscriber#updateCallsign, and the ones
	 * whose handle is known here, from their INSTANCE message.
	 */
	public void updateCallsign() {
		// Before the peers are listed: one whose INSTANCE message comes meanwhile is told there.
		callsignChanged = true;
		for (PeerHandle peerHandle : subscribers.keySet()) {
			sendCallsign(peerHandle);
		}
	}

	private void sendCallsign(PeerHandle peerHandle) {
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		if (sender == null) {
			Log.w(TAG, "Cannot send CALLSIGN before the publish session starts");
			return;
		}
		sender.send(peerHandle, TransportMessage.Command.CALLSIGN,
				TransportMessage.buildMessage(TransportMessage.Command.CALLSIGN,
						manager.getCallsign()));
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

	void forgetPeer(PeerHandle peerHandle) {
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		if (sender != null) {
			sender.cancelPeer(peerHandle);
		}
		subscribers.remove(peerHandle);
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
