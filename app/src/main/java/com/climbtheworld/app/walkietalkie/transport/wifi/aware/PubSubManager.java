package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.transport.TransportUtilities;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PubSubManager {
	private static final String TAG = PubSubManager.class.getSimpleName();
	private static final int UDP_PORT = 30183;
	private static final int MAX_BUFFER_SIZE = 256;
	private final Handler backgroundHandler;
	private final UUID sessionUUID;
	private final String serviceName;
	private final WifiAwareSession awareSession;
	private final ITransportEvents transportEventsListener;
	private final WifiAwareTransport wifiAwareTransport;
	private final RangingManager rangingManager;
	private final Context context;
	private final String channel;
	private final ConnectivityManager connectivityManager;
	private final Map<PeerHandle, ConnectivityManager.NetworkCallback> networkCallbacks =
			new ConcurrentHashMap<>();
	private final Map<PeerHandle, Network> peerNetworks = new ConcurrentHashMap<>();
	private final Map<PeerHandle, DatagramSocket> peerSendSockets = new ConcurrentHashMap<>();
	private final Map<PeerHandle, InetAddress> peerIPv6Addresses = new ConcurrentHashMap<>();
	private DatagramSocket udpReceiveSocket;
	private Publisher publisher;
	private Subscriber subscriber;
	private String callsign = "";
	private ScheduledExecutorService scheduler;
	private boolean isHeartbeatRunning;
	private boolean isReceiverLoopRunning = false;

	public PubSubManager(Handler backgroundHandler, Context context,
	                     String channel,
	                     WifiAwareSession awareSession,
	                     ITransportEvents transportEventsListener,
	                     WifiAwareTransport wifiAwareTransport) {
		this.backgroundHandler = backgroundHandler;
		this.context = context;
		this.channel = channel;
		this.awareSession = awareSession;
		this.transportEventsListener = transportEventsListener;
		this.wifiAwareTransport = wifiAwareTransport;

		this.sessionUUID = UUID.randomUUID();
		this.rangingManager = new RangingManager(context);
		this.serviceName =
				TransportUtilities.computeDigest("ctw.walkietalkie." + channel).substring(0, 8)
						.toUpperCase();

		this.connectivityManager =
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	public RangingManager getRangingManager() {
		return rangingManager;
	}

	public String getCallsign() {
		return callsign;
	}

	public void setCallsign(String callsign) {
		this.callsign = callsign;
		if (subscriber != null) {
			subscriber.updateCallsign();
		}
	}

	public PubSubManager withCallsign(String callsign) {
		setCallsign(callsign);
		return this;
	}

	public WifiAwareSession getAwareSession() {
		return awareSession;
	}

	public String getEncryptionKey() {
		return TransportUtilities.computeDigest("ctw.walkietalkie." + channel).toUpperCase();
	}

	public String getServiceName() {
		return getEncryptionKey().substring(0, 8);
	}

	public void startPubSub() {
		publisher = new Publisher(this);
		publisher.startPublishing(backgroundHandler);

		subscriber = new Subscriber(this);
		subscriber.startSubscribing(backgroundHandler);

		startHeartbeat();
	}

	public void onDestroy() {
		teardownAllChannels();
		stopHeartbeat();

		if (publisher != null) {
			publisher.onDestroy();
		}

		if (subscriber != null) {
			subscriber.onDestroy();
		}
	}

	protected synchronized void startHeartbeat() {
		if (isHeartbeatRunning) return;

		// Create a single-threaded scheduler
		scheduler = Executors.newSingleThreadScheduledExecutor();
		isHeartbeatRunning = true;

		// Schedule the ping task to run every 1 second, with an initial delay of 0
		scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				try {
					if (publisher != null) {
						publisher.onHeartBeat();
					}

					if (subscriber != null) {
						subscriber.onHeartBeat();
					}
				} catch (Exception e) {
					Log.e(TAG, "Heartbeat task execution failed", e);
				}
			}
		}, 15, 30, TimeUnit.SECONDS);
	}

	private synchronized void stopHeartbeat() {
		if (!isHeartbeatRunning || scheduler == null) return;

		scheduler.shutdown();
		try {
			// Wait briefly for the current task to finish if it's running
			if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
		}

		isHeartbeatRunning = false;
	}

	public void sendData(byte[] data) {
		if (peerSendSockets.isEmpty()) {
			Log.w(TAG, "Drop voice packet: No active client data channels connected.");
			return;
		}

		NETWORK_EXECUTOR.execute(() -> {
			for (Map.Entry<PeerHandle, DatagramSocket> entry : peerSendSockets.entrySet()) {
				PeerHandle peer = entry.getKey();
				DatagramSocket socket = entry.getValue();
				InetAddress targetAddress = peerIPv6Addresses.get(peer);

				if (socket != null && targetAddress != null && !socket.isClosed()) {
					try {
						DatagramPacket packet =
								new DatagramPacket(data, data.length, targetAddress, UDP_PORT);
						socket.send(packet);
					} catch (Exception e) {
						Log.e(TAG, "Failed blasting voice packet to client session: " + peer, e);
					}
				}
			}
		});
	}

	public void onSubscriberPublisherChanged(PeerHandle peerHandle, PubSubID uuid,
	                                         ObservableHashMap.MapEvent event) {
		Log.d(TAG, "Publisher " + uuid.callsign + " has " + event + ": " + uuid);

		triggerUiUpdate(uuid, event);
	}

	public void onPublisherSubscriberChanged(PeerHandle peerHandle, PubSubID uuid,
	                                         ObservableHashMap.MapEvent event) {
		Log.d(TAG, "Subscriber " + uuid.callsign + " has " + event + ": " + uuid);

		if (event == ObservableHashMap.MapEvent.ADDED) {
			initiateNetworkStack(peerHandle, publisher.getDiscoverySession(), true);
		}

		triggerUiUpdate(uuid, event);
	}

	private void initiateNetworkStack(PeerHandle peerHandle,
	                                  DiscoverySession discoverySession,
	                                  boolean isPublisher) {

		Log.d(TAG, "Starting network stack. Is Publisher:" + isPublisher);

		NetworkSpecifier networkSpecifier;
		if (isPublisher) {
			networkSpecifier =
					new WifiAwareNetworkSpecifier.Builder(discoverySession,
							peerHandle)
							.setPmk(getEncryptionKey().substring(0, 32).getBytes())
							.setPort(30183)
							.build();
		} else {
			networkSpecifier =
					new WifiAwareNetworkSpecifier.Builder(discoverySession,
							peerHandle)
							.setPmk(getEncryptionKey().substring(0, 32).getBytes())
							.build();
		}
		NetworkRequest myNetworkRequest = new NetworkRequest.Builder()
				.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
				.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				.setNetworkSpecifier(networkSpecifier)
				.build();


		ConnectivityManager.NetworkCallback callback =
				new ConnectivityManager.NetworkCallback() {

					@Override
					public void onAvailable(@NonNull Network network) {
						Log.d(TAG, "Network available. Is Publisher:" + isPublisher);

						peerNetworks.put(peerHandle, network);
						setupUdpSocketsForPeer(peerHandle, network);
					}

					@Override
					public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
						Log.d(TAG, "Network blocked status changed. Is Publisher:" + isPublisher);
					}

					@Override
					public void onCapabilitiesChanged(@NonNull Network network,
					                                  @NonNull
					                                  NetworkCapabilities networkCapabilities) {
						Log.d(TAG, "Network capabilities changed. Is Publisher:" + isPublisher);

						WifiAwareNetworkInfo peerAwareInfo =
								(WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
						peerIPv6Addresses.put(peerHandle, peerAwareInfo.getPeerIpv6Addr());
					}

					@Override
					public void onLinkPropertiesChanged(@NonNull Network network,
					                                    @NonNull LinkProperties linkProperties) {
						Log.d(TAG, "Network properties changed. Is Publisher:" + isPublisher);
					}

					@Override
					public void onLosing(@NonNull Network network, int maxMsToLive) {
						Log.d(TAG, "Network losing. Is Publisher:" + isPublisher);
					}

					@Override
					public void onLost(@NonNull Network network) {
						Log.d(TAG, "Network lost. Is Publisher:" + isPublisher);

						teardownSinglePeerChannel(peerHandle);
					}

					@Override
					public void onReserved(@NonNull NetworkCapabilities networkCapabilities) {
						Log.d(TAG, "Network reserved. Is Publisher:" + isPublisher);
					}

					@Override
					public void onUnavailable() {
						Log.d(TAG, "Network unavailable. Is Publisher:" + isPublisher);
					}
				};

		networkCallbacks.put(peerHandle, callback);
		connectivityManager.requestNetwork(myNetworkRequest, callback);
		if (isPublisher) {
			publisher.onNetworkReady(peerHandle);
		}
	}

	private void setupUdpSocketsForPeer(PeerHandle peerHandle, Network network) {
		NETWORK_EXECUTOR.execute(() -> {
			try {
				DatagramSocket sendSocket = new DatagramSocket();
				network.bindSocket(sendSocket);
				peerSendSockets.put(peerHandle, sendSocket);

				Log.d(TAG, "Outbound socket locked securely for client: " + peerHandle);

				synchronized (this) {
					if (!isReceiverLoopRunning || udpReceiveSocket == null ||
							udpReceiveSocket.isClosed()) {
						if (udpReceiveSocket != null) {
							udpReceiveSocket.close();
						}
						udpReceiveSocket = new DatagramSocket(UDP_PORT);
						network.bindSocket(udpReceiveSocket);
						startReceiverEngine();
					}
				}

			} catch (Exception e) {
				Log.e(TAG, "Error initializing socket allocations for client handle", e);
				teardownSinglePeerChannel(peerHandle);
			}
		});
	}

	private void teardownAllChannels() {
		stopReceiverEngine();
		for (PeerHandle peer : networkCallbacks.keySet()) {
			teardownSinglePeerChannel(peer);
		}
		networkCallbacks.clear();
		peerNetworks.clear();
		peerSendSockets.clear();
		peerIPv6Addresses.clear();
	}

	private void teardownSinglePeerChannel(PeerHandle targetPeer) {
		Log.d(TAG, "Cleaning standalone channel descriptors for client session: " + targetPeer);
		ConnectivityManager.NetworkCallback callback = networkCallbacks.remove(targetPeer);
		if (callback != null) {
			try {
				connectivityManager.unregisterNetworkCallback(callback);
			} catch (Exception e) {
				Log.e(TAG, "Error unregistering standalone client callback network hook", e);
			}
		}
		peerNetworks.remove(targetPeer);
		peerIPv6Addresses.remove(targetPeer);
		DatagramSocket socket = peerSendSockets.remove(targetPeer);
		if (socket != null) {
			try {
				socket.close();
			} catch (Exception e) {
				Log.e(TAG, "Failed closing specific destination channel socket context", e);
			}
		}
		if (peerSendSockets.isEmpty()) {
			stopReceiverEngine();
		}
	}

	private void startReceiverEngine() {
		isReceiverLoopRunning = true;

		NETWORK_EXECUTOR.execute(() -> {
			byte[] receiveBuffer = new byte[MAX_BUFFER_SIZE];
			Log.d(TAG, "Shared UDP Inbound Voice worker loop is listening on port: " + UDP_PORT);

			while (isReceiverLoopRunning && udpReceiveSocket != null &&
					!udpReceiveSocket.isClosed()) {
				try {
					DatagramPacket packet = new DatagramPacket(receiveBuffer,
							receiveBuffer.length);
					udpReceiveSocket.receive(packet);

					int dataLength = packet.getLength();
					if (dataLength > 0) {
						byte[] voicePayload = new byte[dataLength];
						System.arraycopy(packet.getData(), 0, voicePayload, 0, dataLength);
						if (transportEventsListener != null) {
							transportEventsListener.onData(UUID.randomUUID(), voicePayload);
						}
					}
				} catch (Exception e) {
					if (isReceiverLoopRunning) {
						Log.e(TAG, "Error encountered during multi-peer inbound buffer parsing",
								e);
					}
				}
			}
		});
	}

	private synchronized void stopReceiverEngine() {
		isReceiverLoopRunning = false;
		if (udpReceiveSocket != null) {
			try {
				udpReceiveSocket.close();
			} catch (Exception e) {
				Log.e(TAG, "Error during fallback receiver infrastructure termination", e);
			}
			udpReceiveSocket = null;
		}
	}

	private void triggerUiUpdate(PubSubID uuid, ObservableHashMap.MapEvent event) {
		switch (event) {
			case ADDED:
				transportEventsListener.onClientEvent(wifiAwareTransport,
						new ITransportEvents.TransportPeer(uuid.uuid, uuid.callsign,
								uuid.distanceMeters), ITransportEvents.ClientEvent.CONNECT);
				break;
			case UPDATED:
				transportEventsListener.onClientEvent(wifiAwareTransport,
						new ITransportEvents.TransportPeer(uuid.uuid, uuid.callsign,
								uuid.distanceMeters), ITransportEvents.ClientEvent.UPDATE);
				break;
			case REMOVED:
				transportEventsListener.onClientEvent(wifiAwareTransport,
						new ITransportEvents.TransportPeer(uuid.uuid, uuid.callsign,
								uuid.distanceMeters), ITransportEvents.ClientEvent.DISCONNECT);
				break;
		}
	}

	public UUID getInstaceUUID() {
		return sessionUUID;
	}

	public void onNetworkReady(PeerHandle peerHandle) {
		initiateNetworkStack(peerHandle, subscriber.getDiscoverySession(), false);
	}
}
