package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import android.content.Context;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.lan.INetworkEventListener;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LanEngine implements INetworkLayerBackend.IEventListener {
	private static final int COMMAND_SPLIT = 0;
	private static final int MESSAGE_SPLIT = 1;

	private final IClientEventListener.ClientType clientType;
	private final Context parent;
	protected final String channel;
	protected final IClientEventListener clientHandler;

	private static List<String> localIPList = new ArrayList<>();

	private INetworkLayerBackend discoveryBackend;
	private IDataLayerLayerBackend transmissionChannelBackend;

	private static class NetworkClient {
		String address = "";
	}

	private final ObservableHashMap<String, NetworkClient> connectedClients = new ObservableHashMap<>();

	private final INetworkEventListener dataEventListener = new INetworkEventListener() {
			@Override
			public void onDataReceived(String sourceAddress, byte[] data) {
				DataFrame inDataFrame = DataFrame.parseData(data);

				if (inDataFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
					if (connectedClients.containsKey(sourceAddress)) {
						clientHandler.onData(inDataFrame, sourceAddress);
					}
					return;
				}

				updateClients(sourceAddress, new String(inDataFrame.getData()));
			}
		};

	@Override
	public void onClientConnected(InetAddress host) {
		if (host == null) {
			return;
		}

		if (connectedClients.containsKey(host.getHostAddress())) {
			return;
		}

		sendData(DataFrame.buildFrame(("PING|" + channel).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK), host.getHostAddress());
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
			Log.d("======", "Failed to determine local address.", e);
		}

		return localIPs;
	}

	public LanEngine(Context parent, String channel, IClientEventListener clientHandler, IClientEventListener.ClientType type) {
		this.parent = parent;
		this.channel = channel;
		this.clientHandler = clientHandler;
		this.clientType = type;

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, NetworkClient>() {
			@Override
			public void onItemPut(String key, NetworkClient value) {
				clientHandler.onClientConnected(type, key);
			}

			@Override
			public void onItemRemove(String key, NetworkClient value) {
				clientHandler.onClientDisconnected(type, key);
			}
		});
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

		if (messageSplit[COMMAND_SPLIT].equals("PING") && !messageSplit[MESSAGE_SPLIT].equals(channel)) {
			return;
		}

		NetworkClient client = connectedClients.get(remoteAddress);
		if (client == null) {
			client = new NetworkClient();
			client.address = remoteAddress;

			connectedClients.put(remoteAddress, client);
		}
	}

	private void sendDisconnect() {
		sendDataToChannel(DataFrame.buildFrame("DISCONNECT".getBytes(), DataFrame.FrameType.NETWORK));
	}

	public void openNetwork(int port) {
		localIPList = getLocalIpAddress();

		this.transmissionChannelBackend = new UDPDataLayerBackend(parent, port, dataEventListener);
		transmissionChannelBackend.startServer();

		this.discoveryBackend = new NetworkServiceDiscoveryLayerBackend(parent, this);
		discoveryBackend.startServer();

//		this.dataLayerBackend = new UDPMulticastBackend(parent, port, new INetworkEventListener() {
//			@Override
//			public void onDataReceived(String sourceAddress, byte[] data) {
//				DataFrame inDataFrame = DataFrame.parseData(data);
//
//				if (inDataFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
//					if (connectedClients.containsKey(sourceAddress)) {
//						clientHandler.onData(inDataFrame, sourceAddress);
//					}
//					return;
//				}
//
//				updateClients(sourceAddress, new String(inDataFrame.getData()));
//			}
//		}, clientType);
	}

	public void closeNetwork() {
		sendDisconnect();

		if (discoveryBackend != null) {
			discoveryBackend.stopServer();
		}

		connectedClients.clear();
	}

	public void sendDataToChannel(DataFrame data) {
		for (NetworkClient client : connectedClients.values()) {
			sendData(data, client.address);
		}
	}

	private void sendData(DataFrame data, String address) {
		transmissionChannelBackend.sendData(data, address);
	}
}
