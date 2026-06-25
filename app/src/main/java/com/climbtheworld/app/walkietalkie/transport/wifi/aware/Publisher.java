package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
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
import java.util.UUID;

public class Publisher extends PubSub {
	private static final String TAG = Publisher.class.getSimpleName();
	private static final UUID sessionUUID = UUID.randomUUID();
	private final Map<PeerHandle, ServiceSubscriber>
			subscribers = new HashMap<>();
	private DiscoverySession hostSession;

	public Publisher(Context context, String serviceName,
	                 WifiAwareSession awareSession,
	                 ITransportEvents transportEventsListener,
	                 ITransportLayer transport) {
		super(context, serviceName, awareSession, transportEventsListener, transport);
	}

	// --- PUBLISHER ROLE (Host device) ---
	@SuppressLint("MissingPermission") //already done at the activity level
	public void startPublishing(String channel, String callsign) {
		PublishConfig config = new PublishConfig.Builder()
				.setServiceName(serviceName)
				.setServiceSpecificInfo(sessionUUID.toString().getBytes())
				.setRangingEnabled(true)
				.build();

		awareSession.publish(config, new DiscoverySessionCallback() {
			@Override
			public void onPublishStarted(@NonNull PublishDiscoverySession session) {
				super.onPublishStarted(session);
				hostSession = session;
				Log.d(TAG, "Publish session started. Waiting for subscribers...");
			}

			@Override
			public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
				super.onServiceLost(peerHandle, reason);
				ServiceSubscriber node = subscribers.get(peerHandle);
				transportEventsListener.onClientEvent(transport,
						new ITransportEvents.TransportPeer(node.uuid,
								node.callsign,
								node.distanceMeters),
						ITransportEvents.ClientEvent.DISCONNECT);
				subscribers.remove(peerHandle);
			}

			@Override
			public void onSessionTerminated() {
				super.onSessionTerminated();
				Log.d(TAG, "Publish session terminated.");
				for (ServiceSubscriber node : subscribers.values()) {
					transportEventsListener.onClientEvent(transport,
							new ITransportEvents.TransportPeer(node.uuid,
									node.callsign,
									node.distanceMeters),
							ITransportEvents.ClientEvent.DISCONNECT);
				}
				subscribers.clear();
			}

			@Override
			public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
				super.onMessageReceived(peerHandle, message);

				if (subscribers.containsKey(peerHandle)) {
					subscribers.get(peerHandle).pong(System.currentTimeMillis());
				}

				Handshake handshake = Handshake.fromData(message);
				Log.d(TAG, "Received message from subscriber: " + handshake.data);

				switch (handshake.connectionState) {
					case IDENTITY:
						ServiceSubscriber
								serviceSubscriber =
								new ServiceSubscriber(handshake.data, peerHandle);
						subscribers.put(peerHandle, serviceSubscriber);
						serviceSubscriber.state = Handshake.ConnectionState.AUTH;
						hostSession.sendMessage(peerHandle, 0, Handshake.buildMessage(
								Handshake.ConnectionState.AUTH,
								TransportUtilities.computeDigest(
										serviceSubscriber.uuid + channel)));
						break;
					case AUTH:
						if (TransportUtilities.computeDigest(sessionUUID + channel)
								.equals(handshake.data) && subscribers.containsKey(peerHandle)) {

							subscribers.get(peerHandle).state = Handshake.ConnectionState.ACTIVE;
							hostSession.sendMessage(peerHandle, 0,
									Handshake.buildMessage(Handshake.ConnectionState.ACTIVE,
											TransportMessage.buildMessage(
													TransportMessage.Command.CALLSIGH, callsign)));
						}
						break;
					case ACTIVE:
						TransportMessage transportMessage =
								TransportMessage.fromString(handshake.data);
						switch (transportMessage.command) {
							case CALLSIGH:
								ServiceSubscriber node =
										subscribers.get(peerHandle);
								node.callsign = transportMessage.message;
								transportEventsListener.onClientEvent(transport,
										new ITransportEvents.TransportPeer(node.uuid,
												node.callsign,
												node.distanceMeters),
										ITransportEvents.ClientEvent.CONNECT);
								break;
						}
						break;
					case DISCONNECTING:
						ServiceSubscriber node =
								subscribers.get(peerHandle);
						transportEventsListener.onClientEvent(transport,
								new ITransportEvents.TransportPeer(node.uuid,
										node.callsign,
										node.distanceMeters),
								ITransportEvents.ClientEvent.DISCONNECT);
						subscribers.remove(peerHandle);
						break;
				}
			}
		}, new Handler(Looper.getMainLooper()));
	}

	public void onInnerDestroy() {
		for (ServiceSubscriber node : subscribers.values()) {
			hostSession.sendMessage(node.peerHandle, 0,
					Handshake.buildMessage(Handshake.ConnectionState.DISCONNECTING));
		}

		if (hostSession != null) {
			hostSession.close();
		}
	}

	@Override
	protected void onRangingData(PeerHandle peerHandle, double distanceMeters) {
		ServiceSubscriber node =
				subscribers.get(peerHandle);
		node.distanceMeters = distanceMeters;
		transportEventsListener.onClientEvent(transport,
				new ITransportEvents.TransportPeer(node.uuid,
						node.callsign,
						node.distanceMeters),
				ITransportEvents.ClientEvent.UPDATE);

		Log.i(TAG, "------------------------> Physical distance to peer: " + distanceMeters +
				" meters.");
	}

	@Override
	protected void onTimerEvent() {
		for (Map.Entry<PeerHandle, ServiceSubscriber> pub : subscribers.entrySet()) {
			if (!pub.getValue().stillAlive()) {
				ServiceSubscriber node =
						subscribers.get(pub.getKey());
				transportEventsListener.onClientEvent(transport,
						new ITransportEvents.TransportPeer(node.uuid,
								node.callsign,
								node.distanceMeters),
						ITransportEvents.ClientEvent.DISCONNECT);
				subscribers.remove(pub.getKey());
			}
		}
	}

	protected static class ServiceSubscriber extends ServicePubSub {
		Handshake.ConnectionState state = Handshake.ConnectionState.IDENTITY;
		String callsign;
		double distanceMeters;

		ServiceSubscriber(String uuid, PeerHandle peerHandle) {
			super(uuid, peerHandle);
		}
	}
}
