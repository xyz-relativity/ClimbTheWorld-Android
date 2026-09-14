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
import com.climbtheworld.app.utils.constants.Constants;
import com.climbtheworld.app.walkietalkie.ITransportEvents;
import com.climbtheworld.app.walkietalkie.ITransportLayer;
import com.climbtheworld.app.walkietalkie.application.audiotools.IRecordingListener;
import com.climbtheworld.app.walkietalkie.application.audiotools.PlaybackThread;
import com.climbtheworld.app.walkietalkie.application.audiotools.RecordingThread;
import com.climbtheworld.app.walkietalkie.application.states.WalkietalkieHandler;
import com.climbtheworld.app.walkietalkie.transport.wifi.aware.WifiAwareTransport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WalkietalkieBackgroundService extends Service {
	private static final String TAG = WalkietalkieBackgroundService.class.getSimpleName();
	private static final int SERVICE_ID = 682987;
	private final Map<UUID, Client> activeClients = new ConcurrentHashMap<>();
	private final Map<UUID, PlaybackThread> playbackThreads = new ConcurrentHashMap<>();
	private final List<ITransportLayer> transportLayers = new ArrayList<>();
	private RecordingThread recordingThread;
	private Context parent;
	private PowerManager.WakeLock wakeLock;
	private Configs configs;
	private volatile IUiClientEvent uiEventListener;
	private boolean intercomStarted;

	public synchronized void startIntercom(IUiClientEvent uiEventListener, Configs configs) {
		this.uiEventListener = uiEventListener;
		this.configs = configs;

		if (intercomStarted) {
			updateConfigs();
			return;
		}
		intercomStarted = true;

		PowerManager pm = (PowerManager) getSystemService(WalkieTalkieActivity.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercom");
		wakeLock.acquire();

		recordingThread = new RecordingThread();
		Constants.AUDIO_RECORDER_EXECUTOR.execute(recordingThread);

		initializeWifiAware(configs);
		updateConfigs();
	}

	private void initializeWifiAware(Configs configs) {
		transportLayers.add(new WifiAwareTransport(parent, configs, new ITransportEvents() {
			@Override
			public void onClientEvent(ITransportLayer transport, TransportPeer peer,
			                          ClientEvent event) {
				Log.d(TAG, "Got client backend event: " + event + " for: " + peer.callsign + ".");

				if (event == ClientEvent.CONNECT || event == ClientEvent.UPDATE) {
					activeClients.put(peer.clientUUID,
							new Client(peer.clientUUID.toString(), peer.callsign, transport)
									.withDistance(peer.distanceMeters));
					startPlayback(peer.clientUUID);
				} else if (event == ClientEvent.DISCONNECT) {
					activeClients.remove(peer.clientUUID);
					stopPlayback(peer.clientUUID);
				}

				IUiClientEvent listener = uiEventListener;
				if (listener != null) {
					listener.notifyClientChange();
				}
			}

			@Override
			public void onData(UUID clientUUID, byte[] data) {
				PlaybackThread playbackThread = playbackThreads.get(clientUUID);
				if (playbackThread == null) {
					Log.w(TAG, "Dropping voice packet for inactive client: " + clientUUID + ".");
					return;
				}
				playbackThread.enqueuePacket(data);
			}
		}));
	}

	private void startPlayback(UUID clientUUID) {
		PlaybackThread newPlaybackThread = new PlaybackThread(clientUUID);
		PlaybackThread existingPlaybackThread = playbackThreads.putIfAbsent(clientUUID,
				newPlaybackThread);
		if (existingPlaybackThread == null) {
			newPlaybackThread.start();
		}
	}

	private void stopPlayback(UUID clientUUID) {
		PlaybackThread playbackThread = playbackThreads.remove(clientUUID);
		if (playbackThread != null) {
			playbackThread.stopPlayback();
		}
	}

	public void updateConfigs() {
		for (ITransportLayer transport : transportLayers) {
			transport.notifyConfigChange();
		}
	}

	public void setRecordingState(WalkietalkieHandler activeState) {
		if (recordingThread == null || activeState == null) {
			return;
		}

		recordingThread.setAudioListener((IRecordingListener) activeState);
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
	public boolean onUnbind(Intent intent) {
		uiEventListener = null;
		if (recordingThread != null) {
			recordingThread.setAudioListener(null);
		}
		return super.onUnbind(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		String channelId = "intercomService";
		NotificationChannel channel = new NotificationChannel(channelId,
				"Walkie-talkie", NotificationManager.IMPORTANCE_DEFAULT);
		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
				.createNotificationChannel(channel);

		Notification notification = new NotificationCompat.Builder(this, channelId)
				.setContentTitle(getText(R.string.walkie_talkie_notification))
				.setContentText(getText(R.string.walkie_talkie_notification_rational))
				.setSmallIcon(R.drawable.ic_walkietalkie)
				.build();

		startForeground(SERVICE_ID, notification);
		parent = getApplicationContext();
	}

	@Override
	public synchronized void onDestroy() {
		uiEventListener = null;
		intercomStarted = false;

		for (Client client : activeClients.values()) {
			client.onDestroy();
		}
		activeClients.clear();

		for (ITransportLayer transport : transportLayers) {
			transport.onDestroy();
		}
		transportLayers.clear();

		for (PlaybackThread playbackThread : playbackThreads.values()) {
			playbackThread.stopPlayback();
		}
		playbackThreads.clear();

		if (recordingThread != null) {
			recordingThread.cancel();
			recordingThread = null;
		}

		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		wakeLock = null;

		super.onDestroy();
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
