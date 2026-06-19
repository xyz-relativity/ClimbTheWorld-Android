package com.climbtheworld.app.walkietalkie.transport.networking.lan;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.transport.IClientEventListener;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer.IpNetworkNode;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer.NetworkLayer;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer.discovery.NSDDiscoveryLayerBackend;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LanController {
	private static final String TAG = LanController.class.getSimpleName();
	protected final IClientEventListener clientHandler;
	private final Context parent;
	private final String channel;
	private final Map<String, IpNetworkNode> connectedClients = new ConcurrentHashMap<>();
	private final ClientType type;
	private final String uuid;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private NSDDiscoveryLayerBackend discoveryBackend;
	private WifiManager.MulticastLock multicastLock;
	private NetworkLayer networkLayer;

	public LanController(Context parent, String channel, IClientEventListener clientHandler,
	                     ClientType type) {
		this.parent = parent;
		this.channel = channel;
		this.clientHandler = clientHandler;
		this.type = type;
		this.uuid = Configs.instance(parent).getString(Configs.ConfigKey.instanceUUID);
	}

	private void sendDisconnect() {
		for (IpNetworkNode client : connectedClients.values()) {
			NETWORK_EXECUTOR.execute(client::disconnect);
		}
	}

	public void startNetwork(int port) {
		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			multicastLock = wifiManager.createMulticastLock("ctw_MulticastLock");
			multicastLock.acquire();
		}

		this.networkLayer = new NetworkLayer(uuid, channel, port, connectedClients,
				new NetworkLayer.IControlLayerListener() {
					@Override
					public void onNetworkLayerControlStarted() {
						LanController.this.discoveryBackend =
								new NSDDiscoveryLayerBackend(parent, uuid,
										new NSDDiscoveryLayerBackend.INDSEventListener() {
											@Override
											public void onNSDNodeDiscovered(InetAddress host) {
												networkLayer.nodeDiscovered(host);
											}

											@Override
											public void onNSDNodeLost(String hostId) {
												networkLayer.nodeLost(hostId);
											}
										});
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								discoveryBackend.start();
							}
						}, 1000);
					}

					@Override
					public void onNetworkLayerClientConnected(String uuID) {
						clientHandler.onClientConnected(type, uuID);
					}

					@Override
					public void onNetworkLayerDataReceived(IpNetworkNode source,
					                                       byte[] data) {
						clientHandler.onData(source.getUUID(), data);
					}

					@Override
					public void onNetworkLayerControlMessage(IpNetworkNode source,
					                                         String message) {
						clientHandler.onControlMessage(source.getUUID(), message);
					}

					@Override
					public void onNetworkLayerClientDisconnected(String uuID) {
						clientHandler.onClientDisconnected(type, uuID);
					}

					@Override
					public void onNetworkLayerControlStopped() {
						if (discoveryBackend != null) {
							discoveryBackend.stopServer();
						}
					}
				});

		networkLayer.startLayer();
	}

	public void closeNetwork() {
		sendDisconnect();

		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (discoveryBackend != null) {
					discoveryBackend.stopServer();
				}

				if (multicastLock != null) {
					multicastLock.release();
				}

				if (networkLayer != null) {
					networkLayer.stopLayer();
				}

				connectedClients.clear();
			}
		}, 500);
	}

	public void sendDataToChannel(byte[] data) {
		for (IpNetworkNode client : connectedClients.values()) {
			client.sendData(data);
		}
	}

	public void sendControlMessage(String message) {
		for (IpNetworkNode client : connectedClients.values()) {
			NETWORK_EXECUTOR.execute(() -> client.sendControl(message));
		}
	}
}
