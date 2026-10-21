package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;

import java.util.Map;

public class Subscriber {
	private static final String TAG = Subscriber.class.getSimpleName();
	private final PubSubManager manager;
	private final ObservableHashMap<PeerHandle, PubSubID> publishers = new ObservableHashMap<>();
	private SubscribeDiscoverySession subscribeSession;
	private ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender;

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

		manager.getAwareSession().subscribe(config, new MessageSessionCallback(TAG,
				"Subscriber " + manager.getInstaceUUID()) {
			@Override
			public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
				super.onSubscribeStarted(session);
				subscribeSession = session;
				messageSender = createMessageSender(backgroundHandler);
				Log.d(TAG, "Subscribe session started. Looking for publishers...");
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Subscribe session terminated.");
				manager.onDiscoverySessionTerminated("subscribe");
			}

			@Override
			public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
			                                java.util.List<byte[]> matchFilter) {
				super.onServiceDiscovered(peerHandle, serviceSpecificInfo, matchFilter);
				Log.d(TAG, "Publisher service " + new String(serviceSpecificInfo) + " discovered" +
						"!");

				TransportMessage message = TransportMessage.fromData(serviceSpecificInfo);
				PubSubID publisher = message == null ? null : message.toPubSubID(-1);
				if (publisher == null) {
					Log.w(TAG, "Ignoring publisher with malformed service info.");
					return;
				}

				publishers.put(peerHandle, publisher);

				sendCallsign(peerHandle);
			}

			@Override
			public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
				Log.d(TAG,
						"Service lost, removing publisher: " + publishers.get(peerHandle) +
								". Reason:" + reason);
				forgetPeer(peerHandle);
			}

			@Override
			protected ReliableMessageSender<PeerHandle, TransportMessage.Command> messageSender() {
				return messageSender;
			}

			@Override
			protected void onMessage(PeerHandle peerHandle, TransportMessage transportMessage) {
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
		PubSubID publisher = publishers.get(peerHandle);
		if (publisher == null) {
			return;
		}
		if (!PeerConnectionElection.localInitiates(manager.getInstaceUUID(), publisher.uuid)) {
			Log.d(TAG, "Peer elected to initiate the data path: " + publisher.uuid);
			return;
		}

		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		if (sender == null) {
			Log.w(TAG, "Cannot send INSTANCE before the subscribe session starts");
			return;
		}

		Log.d(TAG, "Local device elected to initiate the data path: " + publisher.uuid);
		sender.send(peerHandle, TransportMessage.Command.INSTANCE,
				TransportMessage.buildMessage(TransportMessage.Command.INSTANCE,
						manager.getInstaceUUID().toString(), manager.getCallsign()));
	}

	boolean retryConnection(PeerHandle peerHandle) {
		PubSubID publisher = publishers.get(peerHandle);
		if (publisher == null ||
				!PeerConnectionElection.localInitiates(manager.getInstaceUUID(), publisher.uuid)) {
			return false;
		}

		manager.onSubscriberPublisherChanged(peerHandle, publisher,
				ObservableHashMap.MapEvent.UPDATED);
		sendCallsign(peerHandle);
		return true;
	}

	void forgetPeer(PeerHandle peerHandle) {
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		if (sender != null) {
			sender.cancelPeer(peerHandle);
		}
		publishers.remove(peerHandle);
	}

	public void onDestroy() {
		ReliableMessageSender<PeerHandle, TransportMessage.Command> sender = messageSender;
		messageSender = null;
		if (sender != null) {
			sender.close();
		}
		if (subscribeSession != null) {
			subscribeSession.close();
		}

		publishers.clear();
	}

	public void onHeartBeat() {
		Map<PeerHandle, PubSubID> publisherSnapshot = publishers.snapshot();
		manager.getRangingManager().requestRanging(publisherSnapshot.keySet(),
				new RangingManager.IRangingEvent() {
					@Override
					public void onRangingData(PeerHandle peerHandle, double distanceMeters) {
						PubSubID expectedPublisher = publisherSnapshot.get(peerHandle);
						if (expectedPublisher == null) {
							Log.w(TAG, "Ignoring ranging result for an unrequested peer");
							return;
						}

						PubSubID updatedPublisher = new PubSubID(expectedPublisher.uuid,
								expectedPublisher.callsign, distanceMeters);
						if (publishers.replaceIfSame(peerHandle, expectedPublisher, updatedPublisher)) {
							Log.d(TAG, "Got ranging result: " + distanceMeters + "m");
						} else {
							Log.d(TAG, "Ignoring stale ranging result for a replaced peer");
						}
					}
				});
	}

	public DiscoverySession getDiscoverySession() {
		return subscribeSession;
	}

	private ReliableMessageSender<PeerHandle, TransportMessage.Command> createMessageSender(
			Handler backgroundHandler) {
		return new ReliableMessageSender<>(new HandlerRetryScheduler(backgroundHandler),
				(peer, messageId, payload) -> {
					SubscribeDiscoverySession session = subscribeSession;
					if (session == null) {
						throw new IllegalStateException("Subscribe session is unavailable");
					}
					session.sendMessage(peer, messageId, payload);
				},
				(peer, messageType, attempts) -> Log.e(TAG,
						"Failed to send " + messageType + " after " + attempts + " attempts"));
	}
}
