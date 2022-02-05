package com.climbtheworld.app.intercom.networking.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.NetworkConnectionAggregator;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class LanManager extends NetworkManager {
	private static final String MULTICAST_GROUP = "234.1.8.3";
	private static final int CTW_UDP_PORT = 10183;
	private static final int CLIENT_TIMER_COUNT = 1;
	private static final int PING_TIMER_MS = 7000;
	private final Handler handler = new Handler();
	DataFrame inFrame = new DataFrame();
	DataFrame outFrame = new DataFrame();

	private final UDPServer udpServer;
	private UDPClient udpClient;
	private final Timer pingTimer = new Timer();

	private final Map<String, WifiClientInfo> connectedClients = new HashMap<>();

	private static class WifiClientInfo {
		int ttl = CLIENT_TIMER_COUNT;
		String address = "";
		String uuid = "";
	}

	private final BroadcastReceiver connectionStatus = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (isConnected(context)) {
				initNetwork();
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

	public LanManager(AppCompatActivity parent, IClientEventListener uiHandler) {
		super(parent, uiHandler);

		this.udpServer = new UDPServer(CTW_UDP_PORT, MULTICAST_GROUP);
		udpServer.addListener(new com.climbtheworld.app.intercom.networking.wifi.INetworkEventListener() {
			@Override
			public void onDataReceived(String sourceAddress, byte[] data) {
				inFrame.parseData(data);

				if (inFrame.getFrameType() != DataFrame.FrameType.NETWORK) {
					if (connectedClients.containsKey(sourceAddress)) {
						uiHandler.onData(inFrame);
					}
				} else {
					String[] signals = (new String(inFrame.getData())).split(" ");
					updateClients(sourceAddress, signals[0], signals[1]);
				}
			}
		});

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		parent.registerReceiver(connectionStatus, intentFilter);
	}

	private void updateClients(final String address, final String command, final String uuid) {
		if (NetworkConnectionAggregator.myUUID.compareTo(UUID.fromString(uuid)) == 0) {
			return;
		}

		if (command.equals("DISCONNECT")) {
			connectedClients.remove(address);
			uiHandler.onClientDisconnected(IClientEventListener.ClientType.LAN, address, uuid);
			return;
		}

		if (command.equals("PING")) {
			doPong(address);
		}

		WifiClientInfo client = connectedClients.get(address);
		if (client == null) {
			client = new WifiClientInfo();
			client.uuid = uuid;
			client.address = address;

			connectedClients.put(address, client);

			uiHandler.onClientConnected(IClientEventListener.ClientType.LAN, address, uuid);
		}
		client.ttl = CLIENT_TIMER_COUNT;
	}


	class PingTask extends TimerTask {
		public void run() {
			discover(); //send a new discover message

			List<String> timeoutClients = new ArrayList<>();

			for (String client : connectedClients.keySet()) {
				final WifiClientInfo wifiClientInfo = connectedClients.get(client);
				if (wifiClientInfo == null) {
					continue;
				}

				wifiClientInfo.ttl -= 1;
				if (wifiClientInfo.ttl == 0) {
					doPing(wifiClientInfo.address);
				}
				if (wifiClientInfo.ttl < 0) {
					timeoutClients.add(client);

					uiHandler.onClientDisconnected(IClientEventListener.ClientType.LAN, wifiClientInfo.address, wifiClientInfo.uuid);

				}
			}

			for (String client : timeoutClients) {
				connectedClients.remove(client);
			}
		}
	}

	private void discover() {
		doPing(MULTICAST_GROUP);
	}

	private void doPing(String address) {
		outFrame.setFields(("PING " + NetworkConnectionAggregator.myUUID).getBytes(), DataFrame.FrameType.NETWORK);
		udpClient.sendData(outFrame, address);
	}

	private void doPong(String address) {
		outFrame.setFields(("PONG " + NetworkConnectionAggregator.myUUID).getBytes(), DataFrame.FrameType.NETWORK);
		udpClient.sendData(outFrame, address);
	}

	private void sendDisconnect(String address) {
		outFrame.setFields(("DISCONNECT " + NetworkConnectionAggregator.myUUID).getBytes(), DataFrame.FrameType.NETWORK);
		udpClient.sendData(outFrame, address);
	}

	public void onStart() {
		try {
			this.udpClient = new UDPClient(CTW_UDP_PORT);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onResume() {

	}

	public void onDestroy() {
		sendDisconnect(MULTICAST_GROUP);
		closeNetwork();
	}

	@Override
	public void onPause() {

	}

	private void initNetwork() {
		udpServer.startServer();
		TimerTask pingTask = new PingTask();
		pingTimer.scheduleAtFixedRate(pingTask, 0, PING_TIMER_MS);

		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				discover();
			}
		}, 500);
	}

	private void closeNetwork() {
		udpServer.stopServer();
		pingTimer.cancel();
		parent.unregisterReceiver(connectionStatus);
	}

	@Override
	public void sendData(DataFrame data) {
		for (WifiClientInfo client : connectedClients.values()) {
			udpClient.sendData(data, client.address);
		}
	}
}
