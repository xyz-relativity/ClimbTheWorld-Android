package com.climbtheworld.app.intercom.networking.lan.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.intercom.networking.lan.INetworkEventListener;
import com.climbtheworld.app.intercom.networking.lan.UDPClient;
import com.climbtheworld.app.intercom.networking.lan.UDPServer;
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

public class WifiNetworkManager extends NetworkManager {
	private static final String MULTICAST_GROUP = "234.1.8.3";
	private static final int CTW_UDP_PORT = 10183;
	private static final int CLIENT_TIMEOUT_S = 7; //has to be bigger then DISCOVER_PING_TIMER_MS
	private static final int DISCOVER_PING_TIMER_MS = CLIENT_TIMEOUT_S / 3;
	private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
	private ScheduledFuture<?> discoverPing;
	private ScheduledFuture<?> pingTimeout;
	private static List<String> localIPs = new ArrayList<>();
	private final Handler handler = new Handler();
	private UDPServer udpServer;
	private final ObservableHashMap<String, WifiClient> connectedClients = new ObservableHashMap<>();
	private android.net.wifi.WifiManager.WifiLock wifiLock = null;
	private UDPClient udpClient;
	private WifiManager.MulticastLock multicastLock;

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

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (isConnected(context)) {
				openNetwork();
			} else {
				closeNetwork();
			}
		}

		private boolean isConnected(Context context) {
			ConnectivityManager cm =
					(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
			return activeNetwork != null &&
					activeNetwork.isConnected();
		}
	};

	public WifiNetworkManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);
		scheduler.setRemoveOnCancelPolicy(true);

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, WifiClient>() {
			@Override
			public void onItemPut(String key, WifiClient value) {
				clientHandler.onClientConnected(IClientEventListener.ClientType.WIFI, key);
			}

			@Override
			public void onItemRemove(String key, WifiClient value) {
				clientHandler.onClientDisconnected(IClientEventListener.ClientType.WIFI, key);
			}
		});

		this.udpServer = new UDPServer(CTW_UDP_PORT, MULTICAST_GROUP);
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

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		parent.registerReceiver(connectionStatus, intentFilter);
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
		} else {
			return;
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

	public void onStart() {

	}

	@Override
	public void onResume() {

	}

	public void onStop() {
		closeNetwork();

		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void onPause() {

	}

	private void openNetwork() {
		localIPs = getLocalIpAddress();

		WifiManager wifiManager = (android.net.wifi.WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "LockTag");
		wifiLock.acquire();
		multicastLock = wifiManager.createMulticastLock("multicastLock");
		multicastLock.setReferenceCounted(true);
		multicastLock.acquire();

		this.udpServer = new UDPServer(CTW_UDP_PORT, MULTICAST_GROUP);
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
			this.udpClient = new UDPClient(CTW_UDP_PORT);

			if (discoverPing != null) {
				discoverPing.cancel(true);
			}
			discoverPing = scheduler.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					discover();
				}
			}, 100, DISCOVER_PING_TIMER_MS, TimeUnit.MILLISECONDS);
			if (pingTimeout != null) {
				pingTimeout.cancel(true);
			}
			pingTimeout= scheduler.scheduleAtFixedRate(new ClientTimerTask(), 100, 1000, TimeUnit.MILLISECONDS);

			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					discover();
				}
			}, 500);
		} catch (SocketException e) {
			Log.d("UDP", "Failed to init udp client.", e);
		}
	}

	private void closeNetwork() {
		sendDisconnect();
		scheduler.shutdownNow();
		pingTimeout = null;
		discoverPing = null;

		if (udpServer != null) {
			udpServer.stopServer();
			this.udpServer = null;
		}

		if (wifiLock != null) {
			wifiLock.release();
		}

		if (multicastLock != null) {
			multicastLock.release();
		}

		connectedClients.clear();
	}

	@Override
	public void sendData(DataFrame data) {
		for (WifiClient client : connectedClients.values()) {
			udpClient.sendData(data, client.address);
		}
	}
}
