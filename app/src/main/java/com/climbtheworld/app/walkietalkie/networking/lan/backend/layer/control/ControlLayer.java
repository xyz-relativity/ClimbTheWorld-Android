package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control;

import android.util.Log;

import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.NetworkNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class ControlLayer {
	private static final String TAG = ControlLayer.class.getSimpleName();
	private final IControlLayerListener eventListener;
	private final TCPServer tcpServer;
	private final int port;
	private final ObservableHashMap<String, NetworkNode> connectedClients;
	private final String channel;
	private final TCPClient.ITCPClientListener clientListener = new TCPClient.ITCPClientListener() {
		@Override
		public void onClientReady(TCPClient client) {
			Log.i(TAG, "Network client ready.");
			client.sendData(Commands.HELLO + Constants.uuid);
		}

		@Override
		public void onDataReceived(TCPClient client, String data) {
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
			}

			eventListener.onDataReceived(data);
		}

		@Override
		public void onClientDisconnected(TCPClient client) {
			connectedClients.remove(client.getUuid());
		}
	};

	public ControlLayer(String channel, int port, ObservableHashMap<String, NetworkNode> connectedClients, IControlLayerListener eventsListener) {
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
