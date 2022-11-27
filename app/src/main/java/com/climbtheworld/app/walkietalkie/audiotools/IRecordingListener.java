package com.climbtheworld.app.walkietalkie.audiotools;

import android.media.AudioFormat;
import android.media.AudioRecord;

public interface IRecordingListener {
	int AUDIO_SAMPLE_RATE = RecorderHelper.AUDIO_SAMPLE_RATE;
	int AUDIO_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
	int AUDIO_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
	int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	int AUDIO_BUFFER_SIZE = RecorderHelper.AUDIO_BUFFER_SIZE;

	void onRecordingStarted();

	void onRawAudio(byte[] frame, int numberOfReadBytes);

	void onAudio(short[] frame, int numberOfReadBytes, double energy, double rms);

	void onRecordingDone();

	class RecorderHelper {
		static int AUDIO_SAMPLE_RATE = 8000;
		static int AUDIO_BUFFER_SIZE = 0;

		static {
			for (int rate : new int[]{8000, 11025, 16000, 22050, 44100}) {  // add the rates you wish to check against
				int bufferSize = AudioRecord.getMinBufferSize(rate, AUDIO_CHANNELS_IN, AUDIO_ENCODING);
				if (bufferSize > 0) {
					AUDIO_SAMPLE_RATE = rate;
					AUDIO_BUFFER_SIZE = bufferSize;
					break;
				}
			}
		}
	}
}
