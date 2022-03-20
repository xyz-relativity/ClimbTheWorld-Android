package com.climbtheworld.app.intercom.networking.lan.backend.tcp;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.lan.backend.LanEngine;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.ObservableHashMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class LanTCPEngine extends LanEngine {
	private final ObservableHashMap<String, TCPClient> activeConnections = new ObservableHashMap<>();

	public LanTCPEngine(String channel, IClientEventListener clientHandler, IClientEventListener.ClientType type) {
		super(channel, clientHandler);

		activeConnections.addMapListener(new ObservableHashMap.MapChangeEventListener<String, TCPClient>() {
			@Override
			public void onItemPut(String key, TCPClient value) {
				clientHandler.onClientConnected(type, key);
			}

			@Override
			public void onItemRemove(String key, TCPClient value) {
				value.closeConnection();
				clientHandler.onClientDisconnected(type, key);
			}
		});
	}

	private void openConnection(String address, int port) {
		Constants.ASYNC_TASK_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				Socket socket = new Socket();

				try {
					socket.bind(null);
					socket.connect((new InetSocketAddress(address, port)), 5000);
					TCPClient client = new TCPClient(socket);
					activeConnections.put(address, client);
				} catch (IOException e) {
					return;
				}
			}
		});
	}

	@Override
	public void openNetwork(String address, int port) {
		if (address == null || address.isEmpty()) {
			TCPServer server = new TCPServer(port);
			server.startServer();
		} else {
		}
	}

	@Override
	public void closeNetwork() {
		activeConnections.clear();
	}

	@Override
	public void sendData(DataFrame data) {

	}

	protected interface ITCPEventListener {
		void onDeviceDisconnected(Socket device);

		void onDeviceConnected(Socket device);

		void onDataReceived(Socket device, byte[] data);
	}
}
