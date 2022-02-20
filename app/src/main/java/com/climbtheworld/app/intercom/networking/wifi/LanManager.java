package com.climbtheworld.app.intercom.networking.wifi;

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
import com.climbtheworld.app.utils.ObservableHashMap;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LanManager extends NetworkManager {
	private static final String MULTICAST_GROUP = "234.1.8.3";
	private static final int CTW_UDP_PORT = 10183;
	private static final int CLIENT_TIMER_COUNT = 1;
	private static final int PING_TIMER_MS = 5000;
	private static List<String> localIPs = new ArrayList<>();
	private final Handler handler = new Handler();
	private final UDPServer udpServer;
	private Timer pingTimer = new Timer();
	private final ObservableHashMap<String, WifiClient> connectedClients = new ObservableHashMap<>();
	private WifiManager.WifiLock wifiLock = null;
	private UDPClient udpClient;

	private static class WifiClient {
		int ttl = CLIENT_TIMER_COUNT;
		String address = "";
	}

	class PingTask extends TimerTask {
		public void run() {
			discover(); //send a new discover message

			List<String> timeoutClients = new ArrayList<>();

			for (String client : connectedClients.keySet()) {
				final WifiClient wifiClient = connectedClients.get(client);
				if (wifiClient == null) {
					continue;
				}

				wifiClient.ttl -= 1;
				if (wifiClient.ttl == 0) {
					doPing(wifiClient.address);
				}
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
				initNetwork();
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

	public LanManager(Context parent, IClientEventListener clientHandler, String channel) {
		super(parent, clientHandler, channel);

		connectedClients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, WifiClient>() {
			@Override
			public void onItemPut(String key, WifiClient value) {
				clientHandler.onClientConnected(IClientEventListener.ClientType.LAN, key);
			}

			@Override
			public void onItemRemove(String key, WifiClient value) {
				clientHandler.onClientDisconnected(IClientEventListener.ClientType.LAN, key);
			}
		});

		this.udpServer = new UDPServer(CTW_UDP_PORT, MULTICAST_GROUP);
		udpServer.addListener(new com.climbtheworld.app.intercom.networking.wifi.INetworkEventListener() {
			@Override
			public void onDataReceived(String sourceAddress, byte[] data) {
				DataFrame inDataFrame = DataFrame.parseData(data);

				if (inDataFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
					if (connectedClients.containsKey(sourceAddress)) {
						clientHandler.onData(inDataFrame, sourceAddress);
					}
					return;
				}

				String[] signals = (new String(inDataFrame.getData())).split(" ");
				updateClients(sourceAddress, signals[0]);
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

	private void updateClients(final String remoteAddress, final String command) {
		if (localIPs.contains(remoteAddress)) {
			return;
		}

		if (command.equals("DISCONNECT")) {
			connectedClients.remove(remoteAddress);
			return;
		}

		if (command.equals("PING")) {
			doPong(remoteAddress);
		}

		WifiClient client = connectedClients.get(remoteAddress);
		if (client == null) {
			client = new WifiClient();
			client.address = remoteAddress;

			connectedClients.put(remoteAddress, client);
		}
		client.ttl = CLIENT_TIMER_COUNT;
	}

	private void discover() {
		doPing(MULTICAST_GROUP);
	}

	private void doPing(String address) {
		udpClient.sendData(DataFrame.buildFrame("PING".getBytes(StandardCharsets.UTF_8), DataFrame.FrameType.NETWORK), address);
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

	private void initNetwork() {
		localIPs = getLocalIpAddress();

		WifiManager wifiManager = (WifiManager) parent.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "LockTag");
		wifiLock.acquire();

		udpServer.startServer();

		try {
			this.udpClient = new UDPClient(CTW_UDP_PORT);

			TimerTask pingTask = new PingTask();
			pingTimer = new Timer();
			pingTimer.scheduleAtFixedRate(pingTask, 0, PING_TIMER_MS);

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
		udpServer.stopServer();
		pingTimer.cancel();

		if (wifiLock != null) {
			wifiLock.release();
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
