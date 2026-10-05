package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.media.AudioFormat;
import android.media.AudioRecord;

public interface IRecordingListener {
	int AUDIO_SAMPLE_RATE = RecorderHelper.AUDIO_SAMPLE_RATE;
	int AUDIO_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
	int AUDIO_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
	int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	int AUDIO_BUFFER_SIZE = RecorderHelper.AUDIO_BUFFER_SIZE;
	// 20 ms is one Opus frame and a valid WebRTC VAD frame at every supported sample rate.
	int AUDIO_FRAME_DURATION_MS = 20;
	int AUDIO_FRAME_SIZE = AUDIO_SAMPLE_RATE * AUDIO_FRAME_DURATION_MS / 1000;

	void onRecordingStarted();

	void onRawAudio(short[] frame, int numberOfReadBytes);

	void onRecordingDone();

	class RecorderHelper {
		static int AUDIO_SAMPLE_RATE = 8000;
		static int AUDIO_BUFFER_SIZE = 0;

		static {
			// Only rates supported by both Opus and the WebRTC VAD.
			for (int rate : new int[]{8000, 16000, 48000}) {
				int bufferSize =
						AudioRecord.getMinBufferSize(rate, AUDIO_CHANNELS_IN, AUDIO_ENCODING);
				if (bufferSize > 0) {
					AUDIO_SAMPLE_RATE = rate;
					AUDIO_BUFFER_SIZE = bufferSize;
					break;
				}
			}
		}
	}
}
