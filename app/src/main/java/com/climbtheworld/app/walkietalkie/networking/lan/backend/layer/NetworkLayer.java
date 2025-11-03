package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer;

import android.util.Log;

import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPClient;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPServer;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.data.UDPChannelBackend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class NetworkLayer {
	private static final String TAG = NetworkLayer.class.getSimpleName();
	private final IControlLayerListener eventListener;
	private final TCPServer tcpServer;
	private final UDPChannelBackend udpChannel;
	private final int port;
	private final ObservableHashMap<String, NetworkNode> connectedClients;
	private final Map<String, String> addressLookupMap = new HashMap<>();
	private final String channel;
	private final TCPClient.ITCPClientListener clientListener = new TCPClient.ITCPClientListener() {
			private static final String HELLO = "HELLO:";

		@Override
		public void onClientConnected(TCPClient client) {
			Log.i(TAG, "Network client ready.");
			client.sendControlMessage(HELLO + Constants.uuid);
		}

		@Override
		public void onControlMessageReceived(TCPClient client, String data) {
			if (data.startsWith(HELLO)) {
				String clientUUID = data.split(HELLO)[1];
				if (connectedClients.containsKey(clientUUID)) {
					return;
				}

				client.setUuid(clientUUID);
				connectedClients.put(clientUUID, new NetworkNode(channel, client, udpChannel, new NetworkNode.INetworkNodeEventListener() {
					@Override
					public void onClientConnected(NetworkNode networkNode) {

					}

					@Override
					public void onData(InetAddress sourceAddress, byte[] data) {
						eventListener.onDataReceived(sourceAddress, data);
					}

					@Override
					public void onControlMessage(InetAddress sourceAddress, String message) {
						eventListener.onControlMessage(sourceAddress, message);
					}

					@Override
					public void onClientDisconnected(NetworkNode networkNode) {

					}
				}));
				addressLookupMap.put(client.getRemoteIp().getHostAddress(), clientUUID);
			}
		}

		@Override
		public void onClientDisconnected(TCPClient client) {
			addressLookupMap.values().remove(client.getUuid());
			connectedClients.remove(client.getUuid());
		}
	};

	public NetworkLayer(String channel, int port, ObservableHashMap<String, NetworkNode> connectedClients, IControlLayerListener eventsListener) {
		this.channel = channel;
		this.port = port;
		this.connectedClients = connectedClients;
		this.eventListener = eventsListener;

		this.tcpServer = new TCPServer(port, new TCPServer.ITCPServerListener() {
			@Override
			public void onServerStarted() {
				eventListener.onServerStarted();
			}

			@Override
			public void onClientConnected(Socket clientSocket) {
				TCPClient client = TCPClient.buildFromSocket(clientSocket, clientListener);
				client.start();
			}

			@Override
			public void onServerStopped() {
				eventListener.onServerStopped();
			}
		});

		this.udpChannel = new UDPChannelBackend(port, new UDPChannelBackend.IUDPChannelEventListener() {
			@Override
			public void onServerStarted() {

			}

			@Override
			public void onServerStopped() {

			}

			@Override
			public void onDataReceived(InetAddress sourceAddress, byte[] data) {
				if (!addressLookupMap.containsKey(sourceAddress.getHostAddress())) {
					return;
				}

				connectedClients.get(addressLookupMap.get(sourceAddress.getHostAddress())).onDataReceived(sourceAddress, data);
			}
		});
	}

	public void startLayer() {
		tcpServer.start();
		udpChannel.start();
	}

	public void nodeDiscovered(InetAddress host) {
		try {
			TCPClient client = TCPClient.connectToServer(host.getHostAddress(), port, clientListener);
			if (client == null) {
				return;
			}
			client.start();

		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void stopLayer() {
		tcpServer.stopServer();
		udpChannel.stopServer();
	}

	public void nodeLost(InetAddress host) {

	}

	public interface IControlLayerListener {
		void onServerStarted();

		void onDataReceived(InetAddress sourceAddress, byte[] data);

		void onControlMessage(InetAddress sourceAddress, String message);

		void onServerStopped();
	}
}
