package com.climbtheworld.app.walkietalkie.application.audiotools;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.BlockingQueue;

public class PlaybackThread extends Thread {
	private static final String TAG = PlaybackThread.class.getSimpleName();
	private final BlockingQueue<byte[]> queue;
	private volatile boolean isPlaying = false;

	public PlaybackThread(BlockingQueue<byte[]> queue) {
		this.queue = queue;
	}

	public void stopPlayback() {
		isPlaying = false;
		queue.add(new byte[0]); // Wake the thread up.
	}

	@Override
	public void run() {
		AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build();

		AudioFormat audioFormat = new AudioFormat.Builder()
				.setSampleRate(IRecordingListener.AUDIO_SAMPLE_RATE)
				.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
				.setChannelMask(IRecordingListener.AUDIO_CHANNELS_OUT)
				.build();

		int minBufferSize = AudioTrack.getMinBufferSize(IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_OUT, AudioFormat.ENCODING_PCM_16BIT);

		AudioTrack track = new AudioTrack.Builder()
				.setAudioAttributes(audioAttributes)
				.setAudioFormat(audioFormat)
				.setBufferSizeInBytes(minBufferSize)
				.setTransferMode(AudioTrack.MODE_STREAM)
				.build();
		OpusTools.Decoder decoder = null;

		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

		try {
			decoder = OpusTools.createDecoder();
			track.play();
			isPlaying = true;

			while (isPlaying) {
				byte[] packet;
				try {
					packet = queue.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				if (!isPlaying) {
					break;
				}

				try {
					for (short[] samples : decoder.decode(packet)) {
						track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
					}
				} catch (IllegalArgumentException | IllegalStateException e) {
					Log.w(TAG, "Unable to decode an Opus audio packet.", e);
				}
			}
		} catch (IllegalStateException e) {
			Log.w(TAG, "Opening playback stream failed.", e);
		} finally {
			if (decoder != null) {
				decoder.close();
			}
			if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
				try {
					track.stop();
				} catch (IllegalStateException e) {
					Log.w(TAG, "The playback stream was already stopped.", e);
				}
			}
			track.release();
		}
	}
}
