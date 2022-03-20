package com.climbtheworld.app.intercom.networking.lan.backend.tcp;

import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.lan.backend.LanEngine;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.ObservableHashMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
					if (socket.isConnected()) {
						TCPClient client = new TCPClient(socket);
						client.start();
						client.sendData(DataFrame.buildFrame(channel.getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK).toByteArray());
						activeConnections.put(address, client);
					}
				} catch (IOException e) {
					Log.d("TCP", "Failed to create socket." + e.getMessage());
					return;
				}
			}
		});
	}

	@Override
	public void openNetwork(String address, int port) {
		if (address == null || address.isEmpty()) {
			TCPServer server = new TCPServer(new ITCPEventListener(){
				@Override
				public void onDeviceDisconnected(Socket device) {
					activeConnections.remove(device.getInetAddress().getHostAddress());
				}

				@Override
				public void onDeviceConnected(Socket device) {
					TCPClient client = new TCPClient(device);
					client.start();
					client.sendData(DataFrame.buildFrame(channel.getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK).toByteArray());
					activeConnections.put(address, client);
				}

				@Override
				public void onDataReceived(Socket device, byte[] data) {

				}
			});
			server.startServer(port);
		} else {
			openConnection(address, port);
		}
	}

	@Override
	public void closeNetwork() {
		activeConnections.clear();
	}

	@Override
	public void sendData(DataFrame data) {
		for (TCPClient client : activeConnections.values()) {
			client.sendData(data.toByteArray());
		}
	}

	protected interface ITCPEventListener {
		void onDeviceDisconnected(Socket device);

		void onDeviceConnected(Socket device);

		void onDataReceived(Socket device, byte[] data);
	}
}
