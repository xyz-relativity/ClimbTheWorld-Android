package com.climbtheworld.app.walkietalkie.application;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class WalkietalkieBackgroundService extends Service {
	private static final String TAG = WalkietalkieBackgroundService.class.getSimpleName();
	private static final int SERVICE_ID = 682987;
	private final Map<UUID, Client> activeClients = new ConcurrentHashMap<>();
	private final Map<UUID, PlaybackThread> playbackThreads = new ConcurrentHashMap<>();
	// Read by the audio thread as it sends, see sendData, while the service may be ending.
	private final List<ITransportLayer> transportLayers = new CopyOnWriteArrayList<>();
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

			@Override
			public void onLayerStatusChanged(ITransportLayer transport,
			                                 ITransportLayer.LayerStatus status) {
				Log.d(TAG, "Transport " + transport.getType() + " status: " + status + ".");

				IUiClientEvent listener = uiEventListener;
				if (listener != null) {
					listener.notifyClientChange();
				}
			}
		}));
	}

	private void startPlayback(UUID clientUUID) {
		// Every update of a peer comes through here: a thread is only built for one without.
		if (playbackThreads.containsKey(clientUUID)) {
			return;
		}

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

		activeState.setPlaybackMonitor(this::getLastAudioPlayedMs);
		recordingThread.setAudioListener((IRecordingListener) activeState);
		activeState.setDataChannelListener(new WalkietalkieHandler.IDataEvent() {
			@Override
			public void onData(byte[] frame, int numberOfReadBytes) {
				sendData(Arrays.copyOfRange(frame, 0, numberOfReadBytes));
			}
		});
	}

	/**
	 * Lets go of a screen that is going away: its listener and its recording state hold the
	 * activity and its views, and the service outlives them. The screen says so itself, before
	 * each unbind, as Android does not tell the service of every unbind, see onUnbind.
	 * <p>
	 * With no screen, voice is still received and played. None is sent until a screen binds
	 * again, as the recorder then has no state to hand its audio to.
	 *
	 * @param uiEventListener the screen's listener, as given to startIntercom
	 * @param recordingState  the screen's state, as last given to setRecordingState, if any
	 */
	public synchronized void detachUi(IUiClientEvent uiEventListener,
	                                  WalkietalkieHandler recordingState) {
		// What another screen has put in place since, by binding in its turn, stays.
		if (this.uiEventListener == uiEventListener) {
			this.uiEventListener = null;
			// A screen that is gone shows nothing, even if it never said so, see setUiVisible.
			setDistanceShown(false);
		}
		if (recordingThread != null && recordingState != null) {
			recordingThread.removeAudioListener((IRecordingListener) recordingState);
		}
	}

	/**
	 * Says whether the screen is showing. The distance to the peers is only measured while it
	 * is: with the screen off, or another app in front, nobody reads it, and measuring it is a
	 * radio burst to every peer, which the service would repeat for as long as it runs.
	 *
	 * @param uiEventListener the screen's listener, as given to startIntercom
	 */
	public synchronized void setUiVisible(IUiClientEvent uiEventListener, boolean visible) {
		// A screen that another one has replaced, by binding in its turn, no longer has a say:
		// it is stopped after the new one has started.
		if (this.uiEventListener != uiEventListener) {
			return;
		}
		setDistanceShown(visible);
	}

	private void setDistanceShown(boolean shown) {
		for (ITransportLayer transport : transportLayers) {
			transport.setDistanceShown(shown);
		}
	}

	private long getLastAudioPlayedMs() {
		long lastAudioPlayedMs = 0;
		for (PlaybackThread playbackThread : playbackThreads.values()) {
			lastAudioPlayedMs = Math.max(lastAudioPlayedMs, playbackThread.getLastAudioPlayedMs());
		}
		return lastAudioPlayedMs;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new LocalBinder();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// Only the first unbind of the service's life gets here: as this returns false, Android
		// hands the same binder to the later binds and no longer says when they are gone. So the
		// screens are let go of in detachUi, and this is left for one that did not call it.
		uiEventListener = null;
		setDistanceShown(false);
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

		// Without explicit types every manifest type is claimed, and starting a location or
		// microphone service without its runtime permission throws on Android 14+.
		int serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
				== PackageManager.PERMISSION_GRANTED) {
			serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
		}
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				== PackageManager.PERMISSION_GRANTED
				|| ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
				== PackageManager.PERMISSION_GRANTED) {
			serviceTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
		}
		ServiceCompat.startForeground(this, SERVICE_ID, notification, serviceTypes);
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

	/**
	 * Sends a voice packet to every peer: once through each transport, which sends it to all of
	 * its peers. Sent once for each peer, it would reach each of them as many times as there
	 * are peers.
	 */
	public void sendData(byte[] data) {
		if (activeClients.isEmpty()) {
			return;
		}
		for (ITransportLayer transport : transportLayers) {
			transport.sendData(data);
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
