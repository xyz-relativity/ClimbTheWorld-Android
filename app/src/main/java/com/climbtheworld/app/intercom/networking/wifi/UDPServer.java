package com.climbtheworld.app.intercom.networking.wifi;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

public class UDPServer {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private final Integer serverPort;
	private final String bindGroup;
	private ServerThread server;
	private final List<INetworkEventListener> listeners = new ArrayList<>();

	class ServerThread extends Thread {

		private volatile boolean isRunning = true;
		private MulticastSocket serverSocket;
		private InetAddress group = null;

		@Override
		public void run() {
			try {
				serverSocket = new MulticastSocket(serverPort);

				if (bindGroup != null && !bindGroup.isEmpty()) {
					group = InetAddress.getByName(bindGroup);
					serverSocket.joinGroup(group);
				}

				byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				while (isRunning) {
					serverSocket.receive(receivePacket);

					InetAddress ipAddress = receivePacket.getAddress();
					int port = receivePacket.getPort();

					byte[] result = new byte[receivePacket.getLength()];
					System.arraycopy(receivePacket.getData(), 0, result, 0, receivePacket.getLength());
					notifyListeners(ipAddress.getHostAddress(), result);
				}

				if (group != null) {
					serverSocket.leaveGroup(group);
				}
				serverSocket.close();

			} catch (java.io.IOException ignored) {
			}
		}

		private void notifyListeners(String address, byte[] data) {
			for (INetworkEventListener obs : listeners) {
				obs.onDataReceived(address, data);
			}
		}

		void stopServer() {
			isRunning = false;
			serverSocket.close();
		}
	}

	public UDPServer(int port) {
		this.serverPort = port;
		this.bindGroup = null;
	}

	public UDPServer(int port, String group) {
		this.serverPort = port;
		this.bindGroup = group;
	}

	public void addListener(INetworkEventListener listener) {
		this.listeners.add(listener);
	}

	public void startServer() {
		stopServer();

		server = new ServerThread();
		server.start();
	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
			server = null;
		}
	}
}
