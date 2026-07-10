package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import needle.CancelableTask;

@SuppressLint("MissingPermission") //permission checked at WalkieTalkieActivity activity startup
public class RecordingThread extends CancelableTask {
	private final AudioRecord recorder;
	private final int audioSessionId;
	private IRecordingListener audioListener;
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

	public int getAudioSessionId() {
		return audioSessionId;
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		short[] recordingBuffer = new short[IRecordingListener.AUDIO_BUFFER_SIZE / 2];

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			return;
		}

		if (AcousticEchoCanceler.isAvailable()) {
			acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId);
			if (acousticEchoCanceler != null)
				acousticEchoCanceler.setEnabled(true);
		}

		// Start Recording
		recorder.startRecording();

		// Infinite loop until microphone button is released
		while (!isCanceled()) {
			int numberOfSamples =
					recorder.read(recordingBuffer, 0, IRecordingListener.AUDIO_BUFFER_SIZE / 2);

			audioListener.onRawAudio(recordingBuffer, numberOfSamples);
		}

		try {
			recorder.stop();
		} catch (IllegalStateException e) {
			// do nothing for now
		} finally {
			recorder.release();
		}

		if (acousticEchoCanceler != null) {
			acousticEchoCanceler.setEnabled(false);
			acousticEchoCanceler.release();
			acousticEchoCanceler = null;
		}
	}
}
