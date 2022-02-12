package com.climbtheworld.app.intercom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.P2PWiFiManager;
import com.climbtheworld.app.intercom.networking.wifi.LanManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IntercomBackgroundService extends Service implements IClientEventListener, IRecordingListener {
	private LocalBroadcastManager broadcaster;
	private Context parent;
	private LanManager lanManager;
	private BluetoothManager bluetoothManager;
	private P2PWiFiManager p2pWifiManager;
	Map<String, Client> clients = new HashMap<>();
	private final DataFrame dataFrame = new DataFrame();

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

	@Override
	public void onData(DataFrame data, String address) {

	}

	@Override
	public void onClientConnected(ClientType type, String address) {
		clients.put(address, new Client(type, address));
	}

	@Override
	public void onClientDisconnected(ClientType type, String address) {
		clients.remove(address);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		lanManager.onStart();
		bluetoothManager.onStart();
		p2pWifiManager.onStart();

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (Build.VERSION.SDK_INT >= 26) {
			String CHANNEL_ID = "intercomService";
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
					"Channel human readable title",
					NotificationManager.IMPORTANCE_DEFAULT);

			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

			Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
					.setContentTitle(getText(R.string.app_name))
					.setContentText(getText(R.string.intercom_audio_permission_rational))
					.setSmallIcon(R.drawable.ic_view_topo_ar)
					.build();

			startForeground(1, notification);
		}

		this.parent = getApplicationContext();

		lanManager = new LanManager(parent, this);
		bluetoothManager = new BluetoothManager(parent, this);
		p2pWifiManager = new P2PWiFiManager(parent, this);
	}

	@Override
	public void onDestroy() {
		lanManager.onDestroy();
		bluetoothManager.onDestroy();
		p2pWifiManager.onDestroy();

		for (Client client: clients.values()) {
			client.playbackThread.stopPlayback();
		}

		super.onDestroy();
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

	private void sendData(DataFrame frame) {
		lanManager.sendData(frame);
		bluetoothManager.sendData(frame);
	}
}
