package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.NetworkLayer;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.NetworkNode;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.discovery.NSDDiscoveryLayerBackend;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LanController {
	private static final String TAG = LanController.class.getSimpleName();

	private static List<String> localIPList = new ArrayList<>();
	protected final IClientEventListener clientHandler;
	private final Context parent;
	private final String channel;
	private final ObservableHashMap<String, NetworkNode> connectedClients = new ObservableHashMap<>();
	private INetworkLayerBackend discoveryBackend;
	private WifiManager.MulticastLock multicastLock;
	private NetworkLayer networkLayer;

	public LanController(Context parent, String channel, IClientEventListener clientHandler, IClientEventListener.ClientType type) {
		this.parent = parent;
		this.channel = channel;
		this.clientHandler = clientHandler;

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, NetworkNode>() {
			@Override
			public void onItemPut(String key, NetworkNode value) {
				clientHandler.onClientConnected(type, key);
			}

			@Override
			public void onItemRemove(String key, NetworkNode value) {
				clientHandler.onClientDisconnected(type, key);
			}
		});
	}

	protected static List<String> getLocalIpAddress() {
		List<String> localIPs = new ArrayList<>();

		try {
			for (Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces(); enumNetworkInterfaces.hasMoreElements(); ) {
				NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
				for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddress.nextElement();
					if (!inetAddress.isLoopbackAddress() /* && inetAddress instanceof Inet4Address */) {
						localIPs.add(inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			Log.d(TAG, "Failed to determine local address.", e);
		}

		return localIPs;
	}

	private void sendDisconnect() {
		sendDataToChannel(DataFrame.buildFrame("DISCONNECT".getBytes(), DataFrame.FrameType.NETWORK));
	}

	public void startNetwork(int port) {
		localIPList = getLocalIpAddress();

		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wifiManager != null) {
			multicastLock = wifiManager.createMulticastLock("ctw_MulticastLock");
			multicastLock.acquire();
		}

		this.networkLayer = new NetworkLayer(channel, port, connectedClients, new NetworkLayer.IControlLayerListener() {
			@Override
			public void onServerStarted() {
				LanController.this.discoveryBackend = new NSDDiscoveryLayerBackend(parent, new INetworkLayerBackend.IEventListener() {
					@Override
					public void onClientConnected(InetAddress host) {
						networkLayer.nodeDiscovered(host);
					}

					@Override
					public void onClientDisconnected(InetAddress host) {
						//no need to react. TCP layer will take care of this.
					}
				});
				discoveryBackend.startServer();
			}

			@Override
			public void onDataReceived(InetAddress sourceAddress, byte[] data) {

			}

			@Override
			public void onControlMessage(InetAddress sourceAddress, String message) {

			}

			@Override
			public void onServerStopped() {
				LanController.this.discoveryBackend.stopServer();
			}
		});

		networkLayer.start();
	}

	public void closeNetwork() {
		sendDisconnect();

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

	public void sendDataToChannel(DataFrame data) {
		for (NetworkNode client : connectedClients.values()) {
			switch (data.getFrameType()) {
				case DATA:
					client.sendData(data.getData());
					break;
			}
		}
	}
}
