package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.annotation.SuppressLint;
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
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.transport.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.transport.TransportUtilities;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PubSubManager {
	private static final String TAG = PubSubManager.class.getSimpleName();
	private static final int UDP_PORT = 30183;
	private static final int MAX_BUFFER_SIZE = 256;
	private static final long PEER_RECOVERY_DELAY_MS = 1_000;
	private final Handler backgroundHandler;
	private final UUID instanceUUID;
	private final WifiAwareSession awareSession;
	private final ITransportEvents transportEventsListener;
	private final WifiAwareTransport wifiAwareTransport;
	private final RangingManager rangingManager;
	private final String channel;
	private final ConnectivityManager connectivityManager;
	private final Map<PeerHandle, ConnectivityManager.NetworkCallback> networkCallbacks =
			new ConcurrentHashMap<>();
	private final PeerConnectionStateMachine<PeerHandle> peerConnectionStates =
			new PeerConnectionStateMachine<>();
	private final Map<PeerHandle, Network> peerNetworks = new ConcurrentHashMap<>();
	private final Map<PeerHandle, DatagramSocket> peerSendSockets = new ConcurrentHashMap<>();
	private final Map<PeerHandle, InetAddress> peerIPv6Addresses = new ConcurrentHashMap<>();
	private final PeerRegistry<PeerHandle> peerRegistry = new PeerRegistry<>();
	private final Map<PeerHandle, Runnable> peerRecoveryTasks = new ConcurrentHashMap<>();
	private DatagramSocket udpReceiveSocket;
	private Publisher publisher;
	private Subscriber subscriber;
	private String callsign = "";
	private ScheduledExecutorService scheduler;
	private boolean isHeartbeatRunning;
	private boolean isReceiverLoopRunning = false;
	private volatile boolean destroyed;

	public PubSubManager(Handler backgroundHandler, Context context,
	                     String channel,
	                     WifiAwareSession awareSession,
	                     ITransportEvents transportEventsListener,
	                     WifiAwareTransport wifiAwareTransport) {
		this.backgroundHandler = backgroundHandler;
		this.channel = channel;
		this.awareSession = awareSession;
		this.transportEventsListener = transportEventsListener;
		this.wifiAwareTransport = wifiAwareTransport;

		this.instanceUUID = computeInstanceUUID(context, channel);
		this.rangingManager = new RangingManager(context);

		this.connectivityManager =
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	}

	/**
	 * Stable across Wi-Fi Aware restarts, so peers recognise this device when they rediscover it.
	 * ANDROID_ID is per device and is not copied by backups or phone transfers. Hashing it with
	 * the channel keeps the ID broadcast in discovery from linking the device across channels.
	 */
	@SuppressLint("HardwareIds")
	private static UUID computeInstanceUUID(Context context, String channel) {
		String androidId = Settings.Secure.getString(context.getContentResolver(),
				Settings.Secure.ANDROID_ID);
		if (androidId == null || androidId.isEmpty()) {
			Log.w(TAG, "ANDROID_ID is unavailable; peers will see a restart as a new device.");
			return UUID.randomUUID();
		}
		return UUID.nameUUIDFromBytes(("ctw.walkietalkie." + androidId + "." + channel)
				.getBytes(StandardCharsets.UTF_8));
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
		destroyed = true;
		cancelAllPeerRecoveries();
		teardownAllChannels();
		stopHeartbeat();
		for (PubSubID peer : peerRegistry.clear()) {
			triggerUiUpdate(peer, ObservableHashMap.MapEvent.REMOVED);
		}

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

		// Periodically refresh the distance to the peers and keep the data paths busy.
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

					// Wi-Fi Aware tears down data paths that stay idle; the receiver drops this.
					sendData(new byte[0]);
				} catch (Exception e) {
					Log.e(TAG, "Heartbeat task execution failed", e);
				}
			}
		}, 10 + new Random().nextInt(5), 20 + new Random().nextInt(10), TimeUnit.SECONDS);
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

	public synchronized void sendData(byte[] data) {
		if (peerSendSockets.isEmpty()) {
			Log.w(TAG, "Drop voice packet: No active client data channels connected.");
			return;
		}

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
					Log.e(TAG, "Failed sending voice packet to client session: " + peer, e);
				}
			}
		}
	}

	public void onSubscriberPublisherChanged(PeerHandle peerHandle, PubSubID uuid,
	                                         ObservableHashMap.MapEvent event) {
		onPeerChanged("Publisher", PeerRegistry.Role.SUBSCRIBE, peerHandle, uuid, event, false);
	}

	public void onPublisherSubscriberChanged(PeerHandle peerHandle, PubSubID uuid,
	                                         ObservableHashMap.MapEvent event) {
		onPeerChanged("Subscriber", PeerRegistry.Role.PUBLISH, peerHandle, uuid, event, true);
	}

	/**
	 * A peer found, changed or lost by this side's session of the role.
	 *
	 * @param opensNetwork whether this side opens the data path to a peer found, as the publisher
	 *                     does
	 */
	private void onPeerChanged(String peerKind, PeerRegistry.Role role, PeerHandle peerHandle,
	                           PubSubID uuid, ObservableHashMap.MapEvent event,
	                           boolean opensNetwork) {
		Log.d(TAG, peerKind + " " + uuid.callsign + " has " + event + ": " + uuid.uuid);
		if (destroyed) {
			return;
		}

		if (event == ObservableHashMap.MapEvent.REMOVED) {
			applyRegistryChange(peerRegistry.lose(peerHandle));
			return;
		}

		PeerRegistry.Change<PeerHandle> change = peerRegistry.register(role, peerHandle, uuid);
		applyRegistryChange(change);
		peerConnectionStates.markDiscovered(peerHandle);
		if (opensNetwork) {
			initiateNetworkStack(peerHandle, publisher.getDiscoverySession(), true);
		}
		triggerUiUpdate(uuid, change.newPeer ? ObservableHashMap.MapEvent.ADDED :
				ObservableHashMap.MapEvent.UPDATED);
	}

	private void applyRegistryChange(PeerRegistry.Change<PeerHandle> change) {
		for (PeerHandle staleHandle : change.staleHandles) {
			Log.d(TAG, "Dropping stale peer handle: " + staleHandle);
			cancelPeerRecovery(staleHandle);
			teardownSinglePeerChannel(staleHandle);
			publisher.forgetPeer(staleHandle);
			subscriber.forgetPeer(staleHandle);
		}
		for (PubSubID lostPeer : change.lostPeers) {
			triggerUiUpdate(lostPeer, ObservableHashMap.MapEvent.REMOVED);
		}
	}

	void onDiscoverySessionTerminated(String role) {
		wifiAwareTransport.requestRecovery(this, role + " discovery session terminated");
	}

	// Builder#setPmk is API 30+; WifiAwareTransport does not start below that.
	@SuppressLint("NewApi")
	private void initiateNetworkStack(PeerHandle peerHandle,
	                                  DiscoverySession discoverySession,
	                                  boolean isPublisher) {
		if (!peerConnectionStates.beginRequest(peerHandle)) {
			PeerConnectionStateMachine.State state = peerConnectionStates.getState(peerHandle);
			Log.d(TAG, "Skipping duplicate network request for peer in state: " + state);
			if (isPublisher && state == PeerConnectionStateMachine.State.REQUESTING) {
				// The initiator asked again, so its last attempt never reached this pending
				// request: without a new READY it would wait forever.
				publisher.onNetworkReady(peerHandle);
			}
			return;
		}

		Log.d(TAG, "Starting network stack. Is Publisher:" + isPublisher);

		NetworkRequest networkRequest;
		try {
			NetworkSpecifier networkSpecifier;
			if (isPublisher) {
				networkSpecifier =
						new WifiAwareNetworkSpecifier.Builder(discoverySession,
								peerHandle)
								.setPmk(getEncryptionKey().substring(0, 32).getBytes())
								.setPort(UDP_PORT)
								.build();
			} else {
				networkSpecifier =
						new WifiAwareNetworkSpecifier.Builder(discoverySession,
								peerHandle)
								.setPmk(getEncryptionKey().substring(0, 32).getBytes())
								.build();
			}
			networkRequest = new NetworkRequest.Builder()
					.addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
					.setNetworkSpecifier(networkSpecifier)
					.build();
		} catch (RuntimeException e) {
			peerConnectionStates.requestFailed(peerHandle);
			Log.e(TAG, "Unable to build Wi-Fi Aware network request", e);
			return;
		}

		ConnectivityManager.NetworkCallback callback =
				new ConnectivityManager.NetworkCallback() {
					@Override
					public void onAvailable(@NonNull Network network) {
						if (networkCallbacks.get(peerHandle) != this ||
								!peerConnectionStates.markAvailable(peerHandle)) {
							Log.d(TAG, "Ignoring stale or duplicate network availability " +
									"callback");
							return;
						}

						cancelPeerRecovery(peerHandle);
						Log.d(TAG, "Network available. Is Publisher:" + isPublisher);
						connectivityManager.bindProcessToNetwork(network);
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
						if (networkCallbacks.get(peerHandle) != this) {
							Log.d(TAG, "Ignoring stale network capabilities callback");
							return;
						}

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
						if (networkCallbacks.get(peerHandle) != this) {
							Log.d(TAG, "Ignoring stale network loss callback");
							return;
						}

						Log.d(TAG, "Network lost. Is Publisher:" + isPublisher);
						handlePeerPathFailure(peerHandle, isPublisher,
								"Wi-Fi Aware data path lost");
					}

					@Override
					public void onReserved(@NonNull NetworkCapabilities networkCapabilities) {
						Log.d(TAG, "Network reserved. Is Publisher:" + isPublisher);
					}

					@Override
					public void onUnavailable() {
						if (networkCallbacks.get(peerHandle) != this) {
							Log.d(TAG, "Ignoring stale network unavailable callback");
							return;
						}

						Log.d(TAG, "Network unavailable. Is Publisher:" + isPublisher);
						handlePeerPathFailure(peerHandle, isPublisher,
								"Wi-Fi Aware data path unavailable");
					}
				};

		networkCallbacks.put(peerHandle, callback);
		try {
			connectivityManager.requestNetwork(networkRequest, callback);
		} catch (RuntimeException e) {
			networkCallbacks.remove(peerHandle, callback);
			peerConnectionStates.requestFailed(peerHandle);
			Log.e(TAG, "Unable to request Wi-Fi Aware network", e);
			return;
		}

		if (isPublisher) {
			publisher.onNetworkReady(peerHandle);
		}
	}

	private void handlePeerPathFailure(PeerHandle peerHandle, boolean isPublisher,
	                                   String reason) {
		Log.w(TAG, "Recovering peer after: " + reason);
		teardownSinglePeerChannel(peerHandle);
		if (isPublisher || destroyed) {
			return;
		}
		schedulePeerRecovery(peerHandle);
	}

	private void schedulePeerRecovery(PeerHandle peerHandle) {
		Runnable retryTask = new Runnable() {
			@Override
			public void run() {
				if (!peerRecoveryTasks.remove(peerHandle, this) || destroyed) {
					return;
				}
				if (!subscriber.retryConnection(peerHandle)) {
					Log.d(TAG, "Peer is no longer discoverable; waiting for rediscovery");
				}
			}
		};

		Runnable previousTask = peerRecoveryTasks.put(peerHandle, retryTask);
		if (previousTask != null) {
			backgroundHandler.removeCallbacks(previousTask);
		}
		if (!backgroundHandler.postDelayed(retryTask, PEER_RECOVERY_DELAY_MS)) {
			peerRecoveryTasks.remove(peerHandle, retryTask);
			Log.e(TAG, "Unable to schedule peer data-path recovery");
		}
	}

	private void cancelPeerRecovery(PeerHandle peerHandle) {
		Runnable retryTask = peerRecoveryTasks.remove(peerHandle);
		if (retryTask != null) {
			backgroundHandler.removeCallbacks(retryTask);
		}
	}

	private void cancelAllPeerRecoveries() {
		for (Runnable retryTask : peerRecoveryTasks.values()) {
			backgroundHandler.removeCallbacks(retryTask);
		}
		peerRecoveryTasks.clear();
	}

	private void setupUdpSocketsForPeer(PeerHandle peerHandle, Network network) {
		NETWORK_EXECUTOR.execute(() -> {
			synchronized (this) {
				if (peerConnectionStates.getState(peerHandle) !=
						PeerConnectionStateMachine.State.AVAILABLE ||
						!network.equals(peerNetworks.get(peerHandle))) {
					Log.d(TAG, "Ignoring socket setup for a stale peer network");
					return;
				}

				try {
					DatagramSocket sendSocket = new DatagramSocket();
					network.bindSocket(sendSocket);
					peerSendSockets.put(peerHandle, sendSocket);
					wifiAwareTransport.onDataPathStatusChanged(this, true);

					Log.d(TAG, "Outbound socket locked securely for client: " + peerHandle);

					if (!isReceiverLoopRunning || udpReceiveSocket == null ||
							udpReceiveSocket.isClosed()) {
						if (udpReceiveSocket != null) {
							udpReceiveSocket.close();
						}
						udpReceiveSocket = new DatagramSocket(UDP_PORT);
						network.bindSocket(udpReceiveSocket);
						startReceiverEngine();
					}
				} catch (Exception e) {
					Log.e(TAG, "Error initializing socket allocations for client handle", e);
					teardownSinglePeerChannel(peerHandle);
				}
			}
		});
	}

	private void teardownAllChannels() {
		stopReceiverEngine();
		for (PeerHandle peer : networkCallbacks.keySet()) {
			teardownSinglePeerChannel(peer);
		}
		networkCallbacks.clear();
		peerConnectionStates.clear();
		peerNetworks.clear();
		peerSendSockets.clear();
		peerIPv6Addresses.clear();
	}

	private synchronized void teardownSinglePeerChannel(PeerHandle targetPeer) {
		Log.d(TAG, "Cleaning standalone channel descriptors for client session: " + targetPeer);
		ConnectivityManager.NetworkCallback callback = networkCallbacks.remove(targetPeer);
		if (callback != null) {
			try {
				connectivityManager.unregisterNetworkCallback(callback);
			} catch (Exception e) {
				Log.e(TAG, "Error unregistering standalone client callback network hook", e);
			}
		}
		peerConnectionStates.remove(targetPeer);
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
			connectivityManager.bindProcessToNetwork(null);
			stopReceiverEngine();
			wifiAwareTransport.onDataPathStatusChanged(this, false);
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
						UUID clientUUID = findClientUUID(packet.getAddress());
						if (clientUUID == null) {
							Log.w(TAG, "Dropping voice packet from unknown peer address: "
									+ packet.getAddress());
							continue;
						}

						byte[] voicePayload = new byte[dataLength];
						System.arraycopy(packet.getData(), 0, voicePayload, 0, dataLength);
						if (transportEventsListener != null) {
							transportEventsListener.onData(clientUUID, voicePayload);
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

	private UUID findClientUUID(InetAddress sourceAddress) {
		for (Map.Entry<PeerHandle, InetAddress> entry : peerIPv6Addresses.entrySet()) {
			if (sourceAddress.equals(entry.getValue())) {
				return peerRegistry.getUUID(entry.getKey());
			}
		}
		return null;
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
		return instanceUUID;
	}

	public void onNetworkReady(PeerHandle peerHandle) {
		initiateNetworkStack(peerHandle, subscriber.getDiscoverySession(), false);
	}
}
