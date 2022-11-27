package com.climbtheworld.app.walkietalkie;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.activities.WalkieTalkieActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.utils.ObservableHashMap;
import com.climbtheworld.app.walkietalkie.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.audiotools.PlaybackThread;
import com.climbtheworld.app.walkietalkie.networking.DataFrame;
import com.climbtheworld.app.walkietalkie.networking.NetworkManager;
import com.climbtheworld.app.walkietalkie.states.InterconState;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IntercomBackgroundService extends Service implements IClientEventListener {
	private static final int SERVICE_ID = 682987;
	private Context parent;
	private NetworkManager wifiManager;
	private NetworkManager bluetoothManager;
	private NetworkManager wifiDirectManager;
	private NetworkManager wifiAwareManger;
	ObservableHashMap<String, Client> clients = new ObservableHashMap<>();
	private IClientEventListener uiEventListener;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	private String channel;

	public void startIntercom(IClientEventListener uiEventListener, Configs configs) {
		this.uiEventListener = uiEventListener;
		this.configs = configs;
		this.channel = configs.getString(Configs.ConfigKey.intercomChannel);

		PowerManager pm = (PowerManager) getSystemService(WalkieTalkieActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app::intercom");
		wakeLock.acquire(); // we want to be able to stream audio when the screen is off.

		updateConfigs();
	}

	public void updateConfigs() {
		if (!this.channel.equalsIgnoreCase(configs.getString(Configs.ConfigKey.intercomChannel))) {
			this.channel = configs.getString(Configs.ConfigKey.intercomChannel);
			onDestroy();
			startIntercom(uiEventListener, configs);
			return;
		}

		wifiManager = updateBackend(wifiManager, configs.getBoolean(Configs.ConfigKey.intercomAllowWiFi), ClientType.WIFI);
		bluetoothManager = updateBackend(bluetoothManager, configs.getBoolean(Configs.ConfigKey.intercomAllowBluetooth), ClientType.BLUETOOTH);
		wifiDirectManager = updateBackend(wifiDirectManager, configs.getBoolean(Configs.ConfigKey.intercomAllowWiFiDirect), ClientType.WIFI_DIRECT);
//		wifiAwareManger = updateBackend(wifiAwareManger, configs.getBoolean(Configs.ConfigKey.intercomAllowWiFiDirect), ClientType.WIFI_AWARE);
	}

	private NetworkManager updateBackend(NetworkManager manager, boolean state, IClientEventListener.ClientType type) {
		if (state) {
			if (manager == null) {
				try {
					NetworkManager result = NetworkManager.NetworkManagerFactory.build(type, parent, this, channel);
					result.onStart();
					return result;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		} else {
			if (manager != null) {
				manager.onStop();
				return null;
			}
		}
		return manager;
	}

	public void setRecordingState(InterconState activeState) {
		activeState.setListener(new IRecordingListener() {
			//Audio
			@Override
			public void onRecordingStarted() {

			}

			@Override
			public void onRawAudio(byte[] frame, int numberOfReadBytes) {
				sendData(DataFrame.buildFrame(frame, numberOfReadBytes, DataFrame.FrameType.DATA));
			}

			@Override
			public void onAudio(final short[] frame, int numberOfReadBytes, double energy, double rms) {

			}

			@Override
			public void onRecordingDone() {

			}
		});
	}

	private static class Client {
		public Client(IClientEventListener.ClientType type, String address) {
			this.type = type;
			this.address = address;
			playbackThread = new PlaybackThread(queue);
			playbackThread.start();
		}

		PlaybackThread playbackThread;
		final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
		String address;
		IClientEventListener.ClientType type;
	}

	public class LocalBinder extends Binder {
		public IntercomBackgroundService getService(){
			return IntercomBackgroundService.this;
		}
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String CHANNEL_ID = "intercomService";
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
					"Channel human readable title",
					NotificationManager.IMPORTANCE_DEFAULT);

			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

			Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
					.setContentTitle(getText(R.string.walkie_talkie_notification))
					.setContentText(getText(R.string.walkie_talkie_notification_rational))
					.setSmallIcon(R.drawable.ic_intercom)
					.build();

			startForeground(SERVICE_ID, notification);
		}

		this.parent = getApplicationContext();
		clients.addMapListener(new ObservableHashMap.MapChangeEventListener<String, Client>() {
			@Override
			public void onItemPut(String key, Client value) {
				if (uiEventListener != null) {
					uiEventListener.onClientConnected(value.type, key);
				}
			}

			@Override
			public void onItemRemove(String key, Client value) {
				value.playbackThread.stopPlayback();
				if (uiEventListener != null) {
					uiEventListener.onClientDisconnected(value.type, key);
				}
			}
		});
	}

	@Override
	public void onDestroy() {
		if (wifiManager != null) {
			wifiManager.onStop();
			wifiManager = null;
		}

		if (bluetoothManager != null) {
			bluetoothManager.onStop();
			bluetoothManager = null;
		}

		if (wifiDirectManager != null) {
			wifiDirectManager.onStop();
			wifiDirectManager = null;
		}

		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		clients.clear();

		uiEventListener = null;

		super.onDestroy();
	}

	// network
	@Override
	public void onData(DataFrame data, String address) {
		if (!clients.containsKey(address)) {
			return;
		}

		if (data.getFrameType() == DataFrame.FrameType.DATA) {
			Objects.requireNonNull(clients.get(address)).queue.add(data.getData());
			return;
		}

		if (data.getFrameType() == DataFrame.FrameType.SIGNAL && uiEventListener!= null) {
			uiEventListener.onData(data, address);
			return;
		}
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		clients.put(address, new Client(type, address));
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
		clients.remove(address);
	}

	public void sendData(DataFrame frame) {
		if (wifiManager != null) {
			wifiManager.sendData(frame);
		}

		if (bluetoothManager != null) {
			bluetoothManager.sendData(frame);
		}

		if (wifiDirectManager != null) {
			wifiDirectManager.sendData(frame);
		}
	}
}
