package com.climbtheworld.app.intercom.audiotools;

import android.media.AudioRecord;
import android.media.MediaRecorder;

import needle.CancelableTask;

public class RecordingThread extends CancelableTask {
	private final IRecordingListener audioListener;

	public RecordingThread(IRecordingListener audioListener) {
		this.audioListener = audioListener;
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		byte[] recordingBuffer = new byte[IRecordingListener.AUDIO_BUFFER_SIZE];

		// Infinite loop until microphone button is released
		float[] samples = new float[IRecordingListener.AUDIO_BUFFER_SIZE / 2];

		// Start Recording
		AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE);

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			return;
		}

		recorder.startRecording();

		audioListener.onRecordingStarted();

		while (!isCanceled()) {
			int numberOfShort = recorder.read(recordingBuffer, 0, IRecordingListener.AUDIO_BUFFER_SIZE);

			audioListener.onRawAudio(recordingBuffer, numberOfShort);

			// convert bytes to samples here
			for (int i = 0, s = 0; i < numberOfShort; ) {
				int sample = 0;

				sample |= recordingBuffer[i++] & 0xFF; // (reverse these two lines
				sample |= recordingBuffer[i++] << 8;   //  if the format is big endian)

				// normalize to range of +/-1.0f
				samples[s++] = sample / 32768f;
			}

			float rms = 0f;
			float peak = 0f;
			for (float sample : samples) {

				float abs = Math.abs(sample);
				if (abs > peak) {
					peak = abs;
				}

				rms += sample * sample;
			}

			rms = (float) Math.sqrt(rms / samples.length);
			audioListener.onAudio(recordingBuffer, numberOfShort, peak, rms);
		}

		recorder.stop();
		recorder.release();

		audioListener.onRecordingDone();
	}
}
