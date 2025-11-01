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
		@Override
		public void onClientReady(TCPClient client) {
			Log.i(TAG, "Network client ready.");
			client.sendData(Commands.HELLO + Constants.uuid);
		}

		@Override
		public void onControlMessageReceived(TCPClient client, String data) {
			if (data.startsWith(Commands.HELLO)) {
				String clientUUID = data.split(Commands.HELLO)[1];
				if (connectedClients.containsKey(clientUUID)) {
					return;
				}

				client.setUuid(clientUUID);
				connectedClients.put(clientUUID, new NetworkNode(channel, client, new NetworkNode.INetworkNodeEventListener() {
					@Override
					public void onData(String data) {

					}

					@Override
					public void onControlMessage(String message) {

					}

					@Override
					public void onClientDisconnected(NetworkNode networkNode) {

					}
				}));
				addressLookupMap.put(client.getRemoteIp(), clientUUID);
			}

			eventListener.onDataReceived(data);
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
			public void onDataReceived(String sourceAddress, byte[] data) {
				if (!addressLookupMap.containsKey(sourceAddress)) {
					return;
				}

				connectedClients.get(addressLookupMap.get(sourceAddress)).onDataReceived(sourceAddress, data);
			}
		});
	}

	public void start() {
		tcpServer.start();
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

	public void stop() {
		tcpServer.stopServer();
		udpChannel.stopServer();
	}

	public interface IControlLayerListener {
		void onServerStarted();

		void onDataReceived(String data);

		void onServerStopped();
	}

	protected interface Commands {
		String HELLO = "HELLO:";
	}
}
