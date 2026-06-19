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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.WalkieTalkieActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.walkietalkie.ClientType;
import com.climbtheworld.app.walkietalkie.ITransportClient;
import com.climbtheworld.app.walkietalkie.application.client.Client;
import com.climbtheworld.app.walkietalkie.application.client.UiClient;
import com.climbtheworld.app.walkietalkie.application.states.WalkietalkieHandler;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WalkietalkieBackgroundService extends Service {
	private static final String TAG = WalkietalkieBackgroundService.class.getSimpleName();
	private final static String CALL_SIGN_COMMAND = "CALL_SIGN:";
	private static final int SERVICE_ID = 682987;
	Map<String, Client> activeClients = new HashMap<>();
	private Context parent;
	private UiClient.IUiClientEvent uiEventListener;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	private String channel;
	private String callSign;

	public void startIntercom(UiClient.IUiClientEvent uiEventListener, Configs configs) {
		this.uiEventListener = uiEventListener;
		this.configs = configs;
		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
		this.callSign = configs.getString(Configs.ConfigKey.intercomCallsign);

		PowerManager pm = (PowerManager) getSystemService(WalkieTalkieActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
		wakeLock.acquire(); // we want to be able to stream audio when the screen is off.

		updateConfigs();

		// TODO: debug code
		String uuid = UUID.randomUUID().toString();
		activeClients.put(uuid, new Client(uuid, "Xyz", new ITransportClient() {
			@Override
			public void sendData() {

			}

			@Override
			public ClientType getType() {
				return ClientType.WIFI_AWARE;
			}
		}));
		uiEventListener.notifyClientChange();
	}

	public void updateConfigs() {
		if (!this.channel.equalsIgnoreCase(configs.getString(Configs.ConfigKey.intercomChannel))) {
			this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
			onDestroy();
			startIntercom(uiEventListener, configs);
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
						.setSmallIcon(R.drawable.ic_intercom).build();

		startForeground(SERVICE_ID, notification);

		this.parent = getApplicationContext();
	}

	@Override
	public void onDestroy() {
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		activeClients.clear();

		uiEventListener = null;

		super.onDestroy();
	}

	public void sendData(byte[] data) {
	}

	public List<UiClient> getUiClientList() {
		return activeClients.values().stream()
//				.filter(client -> ConnectionState.ACTIVE.equals(client.networkClient.getState()))
				.map(Client::getUiClient)
				.sorted(Comparator.comparing(uiClient -> uiClient.callSign))
				.collect(Collectors.toList());
	}

	public class LocalBinder extends Binder {
		public WalkietalkieBackgroundService getService() {
			return WalkietalkieBackgroundService.this;
		}
	}
}
