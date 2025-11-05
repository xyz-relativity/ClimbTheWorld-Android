package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer;

import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPClient;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPServer;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.data.UDPChannelBackend;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkLayer {
	private static final String TAG = NetworkLayer.class.getSimpleName();
	private final IControlLayerListener eventListener;
	private final TCPServer tcpServer;
	private final UDPChannelBackend udpChannel;
	private final int port;
	private final Map<String, NetworkNode> connectedClients;
	private final Map<String, String> addressLookupMap = new HashMap<>();
	private final String channel;
	private final List<String> localIPList;
	private final String uuid;
	private final TCPClient.ITCPClientListener clientListener =
			new TCPClient.ITCPClientListener() {
				private static final String HELLO = "HELLO:";

				@Override
				public void onTCPClientConnected(TCPClient client) {
					Log.i(TAG, "Network client ready.");
					client.sendControlMessage(HELLO, uuid);
				}

				@Override
				public void onControlMessageReceived(TCPClient client, String data) {
					if (data.startsWith(HELLO)) {
						String clientUUID = data.split(HELLO)[1];
						if (connectedClients.containsKey(clientUUID)) {
							return;
						}

						client.setUuid(clientUUID);
						connectedClients.put(clientUUID,
								new NetworkNode(channel, client, udpChannel,
										new NetworkNode.INetworkNodeEventListener() {
											@Override
											public void onClientConnected(NetworkNode networkNode) {
												eventListener.onNetworkLayerClientConnected(
														networkNode.getUUID());
											}

											@Override
											public void onData(InetAddress sourceAddress,
											                   byte[] data) {
												eventListener.onNetworkLayerDataReceived(
														sourceAddress, data);
											}

											@Override
											public void onControlMessage(InetAddress sourceAddress,
											                             String message) {
												eventListener.onNetworkLayerControlMessage(
														sourceAddress, message);
											}

											@Override
											public void onClientDisconnected(
													NetworkNode networkNode) {
												clientLost(networkNode.getUUID());
											}
										}));
						addressLookupMap.put(client.getRemoteIp().getHostAddress(), clientUUID);
					}
				}

				@Override
				public void onTCPClientDisconnected(TCPClient client) {
					clientLost(client.getUuid());
				}
			};

	public NetworkLayer(String uuid, String channel, int port,
	                    Map<String, NetworkNode> connectedClients,
	                    IControlLayerListener eventsListener) {
		this.uuid = uuid;
		this.channel = channel;
		this.port = port;
		this.connectedClients = connectedClients;
		this.eventListener = eventsListener;

		localIPList = getLocalIpAddress();

		this.tcpServer = new TCPServer(port, new TCPServer.ITCPServerListener() {
			@Override
			public void onTCPServerStarted() {
				eventListener.onNetworkLayerControlStarted();
			}

			@Override
			public void onTCPClientConnected(Socket clientSocket) {
				TCPClient client = TCPClient.buildFromSocket(clientSocket, clientListener);
				client.start();
			}

			@Override
			public void onTCPServerStopped() {
				eventListener.onNetworkLayerControlStopped();
			}
		});

		this.udpChannel =
				new UDPChannelBackend(port, new UDPChannelBackend.IUDPChannelEventListener() {
					@Override
					public void onUDPServerStarted() {

					}

					@Override
					public void onUDPServerStopped() {

					}

					@Override
					public void onDataReceived(InetAddress sourceAddress, byte[] data) {
						if (!addressLookupMap.containsKey(sourceAddress.getHostAddress())) {
							return;
						}

						connectedClients.get(addressLookupMap.get(sourceAddress.getHostAddress()))
								.onDataReceived(sourceAddress, data);
					}
				});
	}

	private void clientLost(String uuID) {
		addressLookupMap.values().remove(uuID);
		connectedClients.remove(uuID);
		eventListener.onNetworkLayerClientDisconnected(
				uuID);
	}

	public void startLayer() {
		tcpServer.start();
		udpChannel.start();
	}

	public void nodeDiscovered(InetAddress host) {
		if (localIPList.contains(host.getHostAddress())) {
			return;
		}

		try {
			TCPClient client =
					TCPClient.connectToServer(host.getHostAddress(), port, clientListener);
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

	public void nodeLost(String hostId) {
		clientLost(hostId);
	}

	private List<String> getLocalIpAddress() {
		List<String> localIPs = new ArrayList<>();

		try {
			for (Enumeration<NetworkInterface> enumNetworkInterfaces =
			     NetworkInterface.getNetworkInterfaces();
			     enumNetworkInterfaces.hasMoreElements(); ) {
				NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
				for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses();
				     enumIpAddress.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddress.nextElement();
					if (!inetAddress.isLoopbackAddress() /* && inetAddress instanceof Inet4Address
					 */) {
						localIPs.add(inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			Log.d(TAG, "Failed to determine local address.", e);
		}

		return localIPs;
	}

	public interface IControlLayerListener {
		void onNetworkLayerControlStarted();

		void onNetworkLayerClientConnected(String uuID);

		void onNetworkLayerDataReceived(InetAddress sourceAddress, byte[] data);

		void onNetworkLayerControlMessage(InetAddress sourceAddress, String message);

		void onNetworkLayerClientDisconnected(String uuID);

		void onNetworkLayerControlStopped();
	}
}
