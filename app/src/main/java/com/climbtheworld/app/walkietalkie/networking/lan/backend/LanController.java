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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LanController implements INetworkLayerBackend.IEventListener {
	private static final String TAG = LanController.class.getSimpleName();

	private static final int COMMAND_SPLIT = 0;
	private static final int MESSAGE_SPLIT = 1;

	private final Context parent;
	protected final IClientEventListener clientHandler;

	private static List<String> localIPList = new ArrayList<>();
	private final String channel;

	private INetworkLayerBackend discoveryBackend;
	private WifiManager.MulticastLock multicastLock;
	private NetworkLayer networkLayer;

	private final ObservableHashMap<String, NetworkNode> connectedClients = new ObservableHashMap<>();

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

	@Override
	public void onClientConnected(InetAddress host) {
		if (host == null) {
			return;
		}

		if (connectedClients.containsKey(host.getHostAddress())) {
			return;
		}

		sendData(DataFrame.buildFrame(("PING|").getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK), host.getHostAddress());
	}

	@Override
	public void onClientDisconnected(InetAddress host) {
		if (host == null) {
			return;
		}

		connectedClients.remove(host.getHostAddress());
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

	private void updateClients(final String remoteAddress, final String messageData) {
		if (localIPList.contains(remoteAddress)) {
			return;
		}

		String[] messageSplit = messageData.split("\\|");

		if (messageSplit[COMMAND_SPLIT].equals("DISCONNECT")) {
			connectedClients.remove(remoteAddress);
			return;
		}

		if (messageSplit[COMMAND_SPLIT].equals("PING") && !messageSplit[MESSAGE_SPLIT].equals("")) {
		}
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
			public void onDataReceived(String data) {

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
			networkLayer.stop();
		}

		connectedClients.clear();
	}

	public void sendDataToChannel(DataFrame data) {
		for (NetworkNode client : connectedClients.values()) {
			sendData(data, client.getRemoteAddress());
		}
	}

	private void sendData(DataFrame data, String address) {
//		transmissionChannelBackend.sendData(data, address);
	}
}
