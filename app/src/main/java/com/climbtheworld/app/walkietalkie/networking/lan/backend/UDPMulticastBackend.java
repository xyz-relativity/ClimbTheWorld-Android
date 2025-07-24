package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.IClientEventListener;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.lan.INetworkEventListener;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

public class UDPMulticastBackend implements IDataLayerBackend {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private static final String MULTICAST_GROUP = "234.1.8.3";
	private final Integer serverPort;
	private final IClientEventListener.ClientType clientType;
	private final INetworkEventListener listener;
	private final Context parent;
	private ServerThread server;
	private WifiManager.MulticastLock multicastLock;

	public UDPMulticastBackend(Context parent, int port, INetworkEventListener listener, IClientEventListener.ClientType type) {
		this.parent = parent;
		this.serverPort = port;
		this.listener = listener;
		this.clientType = type;
	}

	private NetworkInterface findP2pInterface() {
		if (clientType == IClientEventListener.ClientType.WIFI_AWARE
				|| clientType == IClientEventListener.ClientType.WIFI_DIRECT) {
			try {
				for (Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces(); enumNetworkInterfaces.hasMoreElements(); ) {
					NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
					if (networkInterface.supportsMulticast() && networkInterface.getDisplayName().toLowerCase(Locale.ROOT).startsWith("p2p-")) {
						return networkInterface;
					}
				}
			} catch (SocketException e) {
				return null;
			}
		}
		return null;
	}

	public void startServer() {
		stopServer();

		server = new ServerThread();
		server.start();
	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
		}
	}

	public void broadcastData(final DataFrame sendData) {
		sendData(sendData, MULTICAST_GROUP);
	}

	public void sendData(final DataFrame sendData, final String destination) {
		if (server != null) {
			server.sendData(sendData, destination);
		}
	}

	class ServerThread extends Thread {

		private MulticastSocket serverSocket;
		private volatile boolean isRunning = true;

		@Override
		public void run() {
			WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			multicastLock = wifiManager.createMulticastLock("multicastLock");
			if (multicastLock.isHeld()) {
				multicastLock.release();
			}
			multicastLock.acquire();

			try {
				serverSocket = new MulticastSocket(serverPort);
				serverSocket.setBroadcast(true);
				serverSocket.setLoopbackMode(true);
				NetworkInterface netInterface = findP2pInterface();
				if (netInterface != null) {
					serverSocket.setNetworkInterface(netInterface); //hack for p2p routing table misconfiguration. Should use service discovery maybe: https://developer.android.com/training/connect-devices-wirelessly/nsd-wifi-direct#java
				}

				InetAddress bindGroup = InetAddress.getByName(MULTICAST_GROUP);

					if (netInterface != null) {
						SocketAddress socketAddress = new InetSocketAddress(bindGroup, serverPort);
						serverSocket.joinGroup(socketAddress, netInterface);
					} else {
						serverSocket.joinGroup(bindGroup);
					}

				while (isRunning && !serverSocket.isClosed()) {
					byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
					DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

					serverSocket.receive(receivePacket);

					InetAddress ipAddress = receivePacket.getAddress();

					byte[] result = new byte[receivePacket.getLength()];
					System.arraycopy(receivePacket.getData(), 0, result, 0, receivePacket.getLength());
					notifyListeners(ipAddress.getHostAddress(), result);
				}

				serverSocket.leaveGroup(bindGroup);
				serverSocket.close();
			} catch (java.io.IOException e) {
				Log.d("UDPMulticast", "Failed to join multicast group.", e);
			} finally {
				multicastLock.release();
			}
		}

		public void sendData(final DataFrame sendData, final String destination) {
			NETWORK_EXECUTOR.execute(new Runnable() { //no networking on main thread
				@Override
				public void run() {
					if (serverSocket != null && !serverSocket.isClosed()) {
						try {
							DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), serverPort);
							serverSocket.send(sendPacket);
						} catch (IOException e) {
							Log.d("UDPMulticast", "Failed to send udp data." + e.getMessage());
						}
					}
				}
			});
		}

		private void notifyListeners(String address, byte[] data) {
			listener.onDataReceived(address, data);
		}

		void stopServer() {
			isRunning = false;
		}
	}
}
