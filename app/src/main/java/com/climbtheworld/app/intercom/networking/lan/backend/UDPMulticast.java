package com.climbtheworld.app.intercom.networking.lan.backend;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

import android.util.Log;

import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.lan.INetworkEventListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Locale;

public class UDPMulticast {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private final Integer serverPort;
	private InetAddress bindGroup;
	private ServerThread server;
	private INetworkEventListener listener = new INetworkEventListener() {};

	private static NetworkInterface findP2pInterface() {
		try {
			for (Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces(); enumNetworkInterfaces.hasMoreElements(); ) {
				NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
				if (networkInterface.supportsMulticast() && networkInterface.getDisplayName().toLowerCase(Locale.ROOT).startsWith("p2p-")) {
					return networkInterface;
				}
			}
		} catch (SocketException e) {
		}
		return null;
	}

	class ServerThread extends Thread {

		private volatile boolean isRunning = true;
		protected MulticastSocket serverSocket;

		@Override
		public void run() {
			try {
				serverSocket = new MulticastSocket(serverPort);
				NetworkInterface netInterface = findP2pInterface();
				if (netInterface != null) {
					serverSocket.setNetworkInterface(netInterface); //hack for p2p routing table misconfiguration. Should use service discovery maybe: https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct#java
				}

				if (bindGroup != null) {
					if (netInterface != null) {
						SocketAddress socketAddress = new InetSocketAddress(bindGroup, serverPort);
						serverSocket.joinGroup(socketAddress, netInterface);
					} else {
						serverSocket.joinGroup(bindGroup);
					}
				}

				byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				while (isRunning) {
					serverSocket.receive(receivePacket);

					InetAddress ipAddress = receivePacket.getAddress();

					byte[] result = new byte[receivePacket.getLength()];
					System.arraycopy(receivePacket.getData(), 0, result, 0, receivePacket.getLength());
					notifyListeners(ipAddress.getHostAddress(), result);
				}

				if (bindGroup != null) {
					serverSocket.leaveGroup(bindGroup);
				}
				serverSocket.close();

			} catch (java.io.IOException e) {
				Log.d("UDPMulticast", "Failed to join multicast group.", e);
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
			Log.d("UDPMulticast", "Failed to create multicast group.", e);
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
				if (server != null && !server.serverSocket.isClosed()) {
					try {
						DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), serverPort);
						server.serverSocket.send(sendPacket);
					} catch (IOException e) {
						Log.d("UDPMulticast", "Failed to send udp data." + e.getMessage());
					}
				}
			}
		});
	}
}
