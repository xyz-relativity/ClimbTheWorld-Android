package com.climbtheworld.app.walkietalkie.application;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.WalkieTalkieActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.application.states.WalkietalkieHandler;
import com.climbtheworld.app.walkietalkie.transport.wifi.aware.WifiAwareTransport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WalkietalkieBackgroundService extends Service {
	private static final String TAG = WalkietalkieBackgroundService.class.getSimpleName();
	private static final int SERVICE_ID = 682987;
	Map<String, Client> activeClients = new HashMap<>();
	List<ITransportLayer> transportLayers = new ArrayList<>();
	private Context parent;
	private IUiClientEvent uiEventListener;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;

	public void startIntercom(IUiClientEvent uiEventListener, Configs configs) {
		this.uiEventListener = uiEventListener;
		this.configs = configs;

		PowerManager pm = (PowerManager) getSystemService(WalkieTalkieActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
		wakeLock.acquire(); // we want to be able to stream audio when the screen is off.

		transportLayers.add(new WifiAwareTransport(parent, configs, new ITransportEvents() {
			@Override
			public void onClientEvent(ITransportLayer transport, TransportPeer peer,
			                          ClientEvent event) {

				Log.d(TAG, "Got client backend event: " + event + " for: " + peer.callsign + ".");

				if (event == ClientEvent.CONNECT) {
					activeClients.put(peer.clientUUID,
							new Client(peer.clientUUID, peer.callsign, transport).withDistance(
									peer.distanceMeters));
				}

				if (event == ClientEvent.UPDATE) {
					activeClients.put(peer.clientUUID,
							new Client(peer.clientUUID, peer.callsign, transport).withDistance(
									peer.distanceMeters));
				}

				if (event == ClientEvent.DISCONNECT) {
					activeClients.remove(peer.clientUUID);
				}

				uiEventListener.notifyClientChange();
			}
		}));

		updateConfigs();
	}

	public void updateConfigs() {
		for (ITransportLayer transport : transportLayers) {
			transport.notifyConfigChange();
		}
	}

	public void setRecordingState(WalkietalkieHandler activeState) {
		activeState.setDataChannelListener(new WalkietalkieHandler.IDataEvent() {
			@Override
			public void onData(byte[] frame, int numberOfReadBytes) {
				sendData(Arrays.copyOfRange(frame, 0, numberOfReadBytes));
			}
		});
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new LocalBinder();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		String CHANNEL_ID = "intercomService";
		NotificationChannel channel =
				new NotificationChannel(CHANNEL_ID, "Channel human readable title",
						NotificationManager.IMPORTANCE_DEFAULT);

		((NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

		Notification notification =
				new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(
								getText(R.string.walkie_talkie_notification))
						.setContentText(getText(R.string.walkie_talkie_notification_rational))
						.setSmallIcon(R.drawable.ic_walkietalkie).build();

		startForeground(SERVICE_ID, notification);

		this.parent = getApplicationContext();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		activeClients.clear();

		uiEventListener = null;

		for (ITransportLayer transport : transportLayers) {
			transport.onDestroy();
		}

		for (Client client : activeClients.values()) {
			client.onDestroy();
		}
	}

	public void sendData(byte[] data) {
		for (Client client : activeClients.values()) {
			client.sendData(data);
		}
	}

	public List<Client> getUiClientList() {
		return activeClients.values().stream()
				.sorted(Comparator.comparing(uiClient -> uiClient.callSign))
				.collect(Collectors.toList());
	}

	public class LocalBinder extends Binder {
		public WalkietalkieBackgroundService getService() {
			return WalkietalkieBackgroundService.this;
		}
	}
}
