package com.climbtheworld.app.intercom;

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
import com.climbtheworld.app.activities.IntercomActivity;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.NetworkManager;
import com.climbtheworld.app.intercom.states.InterconState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IntercomBackgroundService extends Service implements IClientEventListener, IRecordingListener {
	private Context parent;
	private NetworkManager lanManager;
	private NetworkManager bluetoothManager;
	private NetworkManager p2pWifiManager;
	Map<String, Client> clients = new HashMap<>();
	private final DataFrame dataFrame = new DataFrame();
	private IClientEventListener uiEventListener;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;

	public void startIntercom(IClientEventListener uiEventListener, Configs configs, InterconState activeState) {
		this.uiEventListener = uiEventListener;
		this.configs = configs;

		PowerManager pm = (PowerManager) getSystemService(IntercomActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
		wakeLock.acquire(); // we want to be able to stream audio when the screen is off.

		updateConfigs();
		setRecordingState(activeState);
	}

	public void updateConfigs() {
		lanManager = updateBackend(lanManager, configs.getBoolean(Configs.ConfigKey.intercomAllowWiFi), ClientType.LAN);
		bluetoothManager = updateBackend(bluetoothManager, configs.getBoolean(Configs.ConfigKey.intercomAllowBluetooth), ClientType.BLUETOOTH);
		p2pWifiManager = updateBackend(p2pWifiManager, configs.getBoolean(Configs.ConfigKey.intercomAllowWiFiDirect), ClientType.P2P_WIFI);
	}

	private NetworkManager updateBackend(NetworkManager manager, boolean state, IClientEventListener.ClientType type) {
		if (state) {
			if (manager == null) {
				try {
					NetworkManager result = NetworkManager.NetworkManagerFactory.build(type, parent, this);
					result.onStart();
					return result;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		} else {
			if (manager != null) {
				manager.onStop();
			}
		}
		return null;
	}

	public void setRecordingState(InterconState activeState) {
		activeState.setListener(this);
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
					.setContentTitle(getText(R.string.intercom_notification))
					.setContentText(getText(R.string.intercom_notification_rational))
					.setSmallIcon(R.drawable.ic_intercom)
					.build();

			startForeground(1, notification);
		}

		this.parent = getApplicationContext();
	}

	@Override
	public void onDestroy() {
		for (Client client: clients.values()) {
			client.playbackThread.stopPlayback();
		}

		if (lanManager != null) {
			lanManager.onStop();
		}

		if (bluetoothManager != null) {
			bluetoothManager.onStop();
		}

		if (p2pWifiManager != null) {
			p2pWifiManager.onStop();
		}

		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}

		super.onDestroy();
	}

	// network
	@Override
	public void onData(DataFrame data, String address) {
		if (!clients.containsKey(address)) {
			return;
		}

		if (data.getFrameType() == DataFrame.FrameType.DATA) {
			Objects.requireNonNull(clients.get(address)).queue.offer(data.getData());
			return;
		}

		if (data.getFrameType() == DataFrame.FrameType.SIGNAL) {
			uiEventListener.onData(data, address);
			return;
		}
	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		clients.put(address, new Client(type, address));
		uiEventListener.onClientConnected(type, address);
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
		if (clients.containsKey(address)) {
			Objects.requireNonNull(clients.get(address)).playbackThread.stopPlayback();
			clients.remove(address);
			uiEventListener.onClientDisconnected(type, address);
		}
	}

	//Audio
	@Override
	public void onRecordingStarted() {

	}

	@Override
	public void onRawAudio(byte[] frame, int numberOfReadBytes) {
		sendData(dataFrame.setFields(frame, DataFrame.FrameType.DATA));
	}

	@Override
	public void onAudio(final byte[] frame, int numberOfReadBytes, double energy, double rms) {

	}

	@Override
	public void onRecordingDone() {

	}

	public void sendData(DataFrame frame) {
		if (lanManager != null) {
			lanManager.sendData(frame);
		}

		if (bluetoothManager != null) {
			bluetoothManager.sendData(frame);
		}

		if (p2pWifiManager != null) {
			p2pWifiManager.sendData(frame);
		}
	}
}
