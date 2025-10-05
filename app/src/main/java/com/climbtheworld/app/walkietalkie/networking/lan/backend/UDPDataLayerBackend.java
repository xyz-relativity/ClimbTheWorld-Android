package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.content.Context;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.lan.INetworkEventListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class UDPDataLayerBackend implements IDataLayerLayerBackend {
	private final Context parent;
	private final int port;
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private final INetworkEventListener dataEventListener;

	private ServerThread server;

	class ServerThread extends Thread {
		public DatagramSocket serverSocket;
		private volatile boolean isRunning = true;

		@Override
		public void run() {
			try {
				serverSocket = new DatagramSocket(port);

				dataEventListener.onServerStarted();

				while (isRunning && !serverSocket.isClosed()) {
					byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
					DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

					serverSocket.receive(receivePacket);

					InetAddress ipAddress = receivePacket.getAddress();

					byte[] result = Arrays.copyOfRange(receivePacket.getData(), 0, receivePacket.getLength());
					notifyListeners(ipAddress.getHostAddress(), result);
				}

				serverSocket.close();
			} catch (java.io.IOException e) {
				Log.d("UDPMulticast", "Failed to join multicast group.", e);
			}
		}

		public void sendData(final DataFrame sendData, final String destination) {
			NETWORK_EXECUTOR.execute(new Runnable() { //no networking on main thread
				@Override
				public void run() {
					if (serverSocket != null && !serverSocket.isClosed()) {
						try {
							DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), port);
							serverSocket.send(sendPacket);
						} catch (IOException e) {
							Log.d("UDPMulticast", "Failed to send udp data." + e.getMessage());
						}
					}
				}
			});
		}

		private void notifyListeners(String address, byte[] data) {
			dataEventListener.onDataReceived(address, data);
		}

		void stopServer() {
			isRunning = false;
		}
	}

	public UDPDataLayerBackend(Context parent, int port, INetworkEventListener dataEventListener) {
		this.parent = parent;
		this.port = port;
		this.dataEventListener = dataEventListener;
	}

	@Override
	public void sendData(DataFrame sendData, String destination) {
		if (server != null) {
			server.sendData(sendData, destination);
		}
	}

	@Override
	public void startServer() {
		stopServer();

		server = new ServerThread();
		server.start();
	}

	@Override
	public void stopServer() {
		if (server != null) {
			server.stopServer();
		}
	}
}
