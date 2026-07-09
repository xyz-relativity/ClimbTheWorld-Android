package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import needle.CancelableTask;

@SuppressLint("MissingPermission") //permission checked at WalkieTalkieActivity activity startup
public class RecordingThread extends CancelableTask {
	private final IRecordingListener audioListener;
	AcousticEchoCanceler acousticEchoCanceler;

	public RecordingThread(IRecordingListener audioListener) {
		this.audioListener = audioListener;
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		short[] recordingBuffer = new short[IRecordingListener.AUDIO_BUFFER_SIZE / 2];

		AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
				IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE);

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			return;
		}

		if (AcousticEchoCanceler.isAvailable()) {
			acousticEchoCanceler = AcousticEchoCanceler.create(recorder.getAudioSessionId());
			if (!acousticEchoCanceler.getEnabled())
				acousticEchoCanceler.setEnabled(true);
		}

		// Start Recording
		recorder.startRecording();

		audioListener.onRecordingStarted();

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

		audioListener.onRecordingDone();
	}
}
