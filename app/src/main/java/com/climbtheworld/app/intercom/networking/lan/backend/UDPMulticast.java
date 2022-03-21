package com.climbtheworld.app.intercom.networking.lan.backend;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

import android.util.Log;

import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.lan.INetworkEventListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class UDPMulticast {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private final Integer serverPort;
	private InetAddress bindGroup;
	private ServerThread server;
	private INetworkEventListener listener = new INetworkEventListener() {};

	class ServerThread extends Thread {

		private volatile boolean isRunning = true;
		protected MulticastSocket serverSocket;

		@Override
		public void run() {
			try {
				serverSocket = new MulticastSocket(serverPort);
				System.out.println("------ multicast interface: " + serverSocket.getNetworkInterface());

				if (bindGroup != null) {
					serverSocket.joinGroup(bindGroup);
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

				if (bindGroup != null) {
					serverSocket.leaveGroup(bindGroup);
				}
				serverSocket.close();

			} catch (java.io.IOException e) {
				Log.d("UDPMulticast", "Failed to join multicast group." + e.getMessage());
			}
		}

		private void notifyListeners(String address, byte[] data) {
			listener.onDataReceived(address, data);
		}

		void stopServer() {
			isRunning = false;
			serverSocket.close();
		}
	}

	public UDPMulticast(int port, String multicastIP) {
		this.serverPort = port;
		try {
			this.bindGroup = InetAddress.getByName(multicastIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	public void setListener(INetworkEventListener listener) {
		this.listener = listener;
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

	public void sendData(final DataFrame sendData, final String destination) {
		NETWORK_EXECUTOR.execute(new Runnable() { //no networking on main thread
			@Override
			public void run() {
				if (server != null && !server.serverSocket.isClosed())
				try {
					DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), serverPort);
					server.serverSocket.send(sendPacket);
				} catch (IOException e) {
					Log.d("UDPMulticast", "Failed to send udp data." + e.getMessage());
				}
			}
		});
	}
}
