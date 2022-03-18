package com.climbtheworld.app.intercom.networking.lan;

import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.utils.ObservableHashMap;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LanEngine {
	private static final String MULTICAST_GROUP = "234.1.8.3";
	private static final int CLIENT_TIMEOUT_S = 7; //has to be bigger then DISCOVER_PING_TIMER_MS
	private static final int DISCOVER_PING_TIMER_S = CLIENT_TIMEOUT_S / 2;
	private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
	private final IClientEventListener clientHandler;
	private ScheduledFuture<?> discoverPing;
	private ScheduledFuture<?> pingTimeout;
	private static List<String> localIPs = new ArrayList<>();
	private UDPServer udpServer;
	private UDPClient udpClient;
	private final ObservableHashMap<String, WifiClient> connectedClients = new ObservableHashMap<>();
	private final String channel;

	private static class WifiClient {
		int ttl = CLIENT_TIMEOUT_S;
		String address = "";
	}

	class ClientTimerTask implements Runnable {
		public void run() {
			List<String> timeoutClients = new ArrayList<>();

			for (String client : connectedClients.keySet()) {
				final WifiClient wifiClient = connectedClients.get(client);
				if (wifiClient == null) {
					continue;
				}
				wifiClient.ttl -= 1;
				if (wifiClient.ttl < 0) {
					timeoutClients.add(client);
				}
			}

			for (String client : timeoutClients) {
				connectedClients.remove(client);
			}
		}
	}

	public static List<String> getLocalIpAddress() {
		List<String> result = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces(); enumNetworkInterfaces.hasMoreElements(); ) {
				NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
				for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddress.nextElement();
					if (!inetAddress.isLoopbackAddress() /* && inetAddress instanceof Inet4Address */) {
						result.add(inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			Log.d("======", "Failed to determine local address.", e);
		}
		return result;
	}

	public LanEngine(IClientEventListener clientHandler, IClientEventListener.ClientType type,  String channel) {
		scheduler.setRemoveOnCancelPolicy(true);
		this.clientHandler = clientHandler;
		this.channel = channel;

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, WifiClient>() {
			@Override
			public void onItemPut(String key, WifiClient value) {
				clientHandler.onClientConnected(type, key);
			}

			@Override
			public void onItemRemove(String key, WifiClient value) {
				clientHandler.onClientDisconnected(type, key);
			}
		});
	}

	private void updateClients(final String remoteAddress, final String messageData) {
		if (localIPs.contains(remoteAddress)) {
			return;
		}

		String[] messageSplit = messageData.split("\\|");
		String command = messageSplit[0];

		if (command.equals("DISCONNECT")) {
			connectedClients.remove(remoteAddress);
			return;
		}

		if (command.equalsIgnoreCase("PING") && messageSplit[1].equalsIgnoreCase(channel)) {
			doPong(remoteAddress);
		}

		WifiClient client = connectedClients.get(remoteAddress);
		if (client == null) {
			client = new WifiClient();
			client.address = remoteAddress;

			connectedClients.put(remoteAddress, client);
		}
		client.ttl = CLIENT_TIMEOUT_S;
	}

	private void discover() {
		doPing(MULTICAST_GROUP);
	}

	private void doPing(String address) {
		udpClient.sendData(DataFrame.buildFrame(("PING|" + channel).getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK), address);
	}

	private void doPong(String address) {
		udpClient.sendData(DataFrame.buildFrame("PONG".getBytes(), DataFrame.FrameType.NETWORK), address);
	}

	private void sendDisconnect() {
		if (udpClient != null) {
			udpClient.sendData(DataFrame.buildFrame("DISCONNECT".getBytes(), DataFrame.FrameType.NETWORK), MULTICAST_GROUP);
		}
	}

	public void openNetwork(int port) {
		localIPs = getLocalIpAddress();

		this.udpServer = new UDPServer(port, MULTICAST_GROUP);
		udpServer.addListener(new INetworkEventListener() {
			@Override
			public void onDataReceived(String sourceAddress, byte[] data) {
				DataFrame inDataFrame = DataFrame.parseData(data);

				if (inDataFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
					if (connectedClients.containsKey(sourceAddress)) {
						clientHandler.onData(inDataFrame, sourceAddress);
					}
					return;
				}

				updateClients(sourceAddress, new String(inDataFrame.getData()));
			}
		});
		udpServer.startServer();

		try {
			this.udpClient = new UDPClient(port);

			if (discoverPing != null) {
				discoverPing.cancel(true);
			}
			discoverPing = scheduler.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					discover();
				}
			}, 500, TimeUnit.SECONDS.toMillis(DISCOVER_PING_TIMER_S), TimeUnit.MILLISECONDS);
			if (pingTimeout != null) {
				pingTimeout.cancel(true);
			}
			pingTimeout= scheduler.scheduleAtFixedRate(new ClientTimerTask(), 100, 1000, TimeUnit.MILLISECONDS);
		} catch (SocketException e) {
			Log.d("UDP", "Failed to init udp client.", e);
		}
	}

	public void closeNetwork() {
		sendDisconnect();
		pingTimeout = null;
		discoverPing = null;

		if (udpServer != null) {
			udpServer.stopServer();
			this.udpServer = null;
		}

		connectedClients.clear();
	}

	public void sendData(DataFrame data) {
		for (WifiClient client : connectedClients.values()) {
			udpClient.sendData(data, client.address);
		}
	}
}
