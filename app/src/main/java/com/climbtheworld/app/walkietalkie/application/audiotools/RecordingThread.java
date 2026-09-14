package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.util.Log;

import needle.CancelableTask;

@SuppressLint("MissingPermission") //permission checked at WalkieTalkieActivity activity startup
public class RecordingThread extends CancelableTask {
	private static final String TAG = RecordingThread.class.getSimpleName();
	private final AudioRecord recorder;
	private final int audioSessionId;
	private volatile IRecordingListener audioListener;
	private AcousticEchoCanceler acousticEchoCanceler;

	public RecordingThread() {
		recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
				IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE);

		audioSessionId = recorder.getAudioSessionId();
	}

	public void setAudioListener(
			IRecordingListener audioListener) {
		this.audioListener = audioListener;
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		short[] recordingBuffer = new short[IRecordingListener.AUDIO_BUFFER_SIZE / 2];

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			Log.w(TAG, "Audio recorder failed to initialize.");
			recorder.release();
			return;
		}

		boolean recordingStarted = false;
		try {
			initializeAcousticEchoCanceler();
			recorder.startRecording();
			recordingStarted = true;

			while (!isCanceled()) {
				int numberOfSamples = recorder.read(recordingBuffer, 0, recordingBuffer.length);
				if (numberOfSamples <= 0) {
					Log.w(TAG, "Audio recorder read failed with status: " + numberOfSamples);
					continue;
				}

				IRecordingListener listener = audioListener;
				if (listener != null) {
					listener.onRawAudio(recordingBuffer, numberOfSamples);
				}
			}
		} catch (IllegalStateException e) {
			Log.w(TAG, "Audio recording failed.", e);
		} finally {
			if (recordingStarted) {
				try {
					recorder.stop();
				} catch (IllegalStateException e) {
					Log.w(TAG, "Audio recorder was already stopped.", e);
				}
			}
			recorder.release();
			releaseAcousticEchoCanceler();
		}
	}

	private void initializeAcousticEchoCanceler() {
		boolean available = AcousticEchoCanceler.isAvailable();
		Log.i(TAG, "Acoustic echo cancellation available: " + available);
		if (!available) {
			return;
		}

		try {
			acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId);
			if (acousticEchoCanceler == null) {
				Log.w(TAG, "Acoustic echo cancellation could not be created.");
				return;
			}

			boolean enabledByDefault = acousticEchoCanceler.getEnabled();
			int enableResult = acousticEchoCanceler.setEnabled(true);
			Log.i(TAG, "Acoustic echo cancellation default enabled: " + enabledByDefault
					+ ", enable result: " + enableResult
					+ ", final enabled: " + acousticEchoCanceler.getEnabled());
		} catch (RuntimeException e) {
			Log.w(TAG, "Acoustic echo cancellation initialization failed.", e);
		}
	}

	private void releaseAcousticEchoCanceler() {
		if (acousticEchoCanceler == null) {
			return;
		}

		try {
			acousticEchoCanceler.setEnabled(false);
		} catch (RuntimeException e) {
			Log.w(TAG, "Acoustic echo cancellation could not be disabled.", e);
		}
		acousticEchoCanceler.release();
		acousticEchoCanceler = null;
	}
}
