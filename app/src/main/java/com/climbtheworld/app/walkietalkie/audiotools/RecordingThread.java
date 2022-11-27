package com.climbtheworld.app.walkietalkie.audiotools;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.concentus.OpusEncoder;
import org.concentus.OpusException;

import needle.CancelableTask;

@SuppressLint("MissingPermission") //permission checked at activity startup
public class RecordingThread extends CancelableTask {
	private final IRecordingListener audioListener;

	public RecordingThread(IRecordingListener audioListener) {
		this.audioListener = audioListener;
	}

	@Override
	protected void doWork() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
		short[] recordingBuffer = new short[IRecordingListener.AUDIO_BUFFER_SIZE];
		byte[] dataEncoded = new byte[1275];

		// Start Recording
		AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE);

		if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
			return;
		}

		OpusEncoder encoder = OpusTools.getEncoder();

		recorder.startRecording();

		audioListener.onRecordingStarted();

		while (!isCanceled()) {
			int numberOfShort = recorder.read(recordingBuffer, 0, IRecordingListener.AUDIO_BUFFER_SIZE/2); //we are storing "short" so buffer size is half

			try {
				int bytesEncoded = encoder.encode(recordingBuffer, 0, numberOfShort, dataEncoded, 0, dataEncoded.length);
				audioListener.onRawAudio(dataEncoded, bytesEncoded);
			} catch (OpusException e) {
				e.printStackTrace();
			}

			// convert bytes to samples here
			float rms = 0f;
			float peak = 0f;

			for (short sample : recordingBuffer) {
				float normalized = sample / 32768f;
				float abs = Math.abs(normalized);
				if (abs > peak) {
					peak = abs;
				}

				rms += normalized * normalized;
			}

			rms = (float) Math.sqrt(rms / recordingBuffer.length);
			audioListener.onAudio(recordingBuffer, numberOfShort, peak, rms);
		}

		recorder.stop();
		recorder.release();

		audioListener.onRecordingDone();
	}
}
