package com.climbtheworld.app.walkietalkie.audiotools;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import org.concentus.OpusDecoder;
import org.concentus.OpusException;

import java.util.concurrent.BlockingQueue;

public class PlaybackThread extends Thread {
	private final BlockingQueue<byte[]> queue;
	private volatile boolean isPlaying = false;

	public PlaybackThread(BlockingQueue<byte[]> queue) {
		this.queue = queue;
	}

	public void stopPlayback() {
		isPlaying = false;
		queue.add(new byte[0]); //wake the thread up.
	}

	@Override
	public void run() {
		AudioAttributes audioAttributes =
				new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
						.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();

		AudioFormat audioFormat =
				new AudioFormat.Builder().setSampleRate(IRecordingListener.AUDIO_SAMPLE_RATE)
						.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
						.setChannelMask(IRecordingListener.AUDIO_CHANNELS_OUT).build();

		int minBufferSize = AudioTrack.getMinBufferSize(IRecordingListener.AUDIO_SAMPLE_RATE,
				IRecordingListener.AUDIO_CHANNELS_OUT, AudioFormat.ENCODING_PCM_16BIT);
		int trackMode = AudioTrack.MODE_STREAM;

		AudioTrack track =
				new AudioTrack(audioAttributes, audioFormat, minBufferSize, trackMode, 0);

		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

		short[] decodedBuffer = new short[IRecordingListener.AUDIO_BUFFER_SIZE];
		OpusDecoder decoder = OpusTools.getDecoder();

		// Start Playback
		track.play();
		isPlaying = true;

		while (isPlaying) {
			byte[] data = new byte[0];
			try {
				data = queue.take(); //wait for data
				int samplesDecoded = decoder.decode(data, 0, data.length, decodedBuffer, 0,
						IRecordingListener.AUDIO_BUFFER_SIZE, false);
				track.write(decodedBuffer, 0, samplesDecoded);
			} catch (InterruptedException | OpusException e) {
				Log.w("INTERCOM", "Opening playback stream failed.", e);
			}
		}

		track.stop();
		track.release();
	}
}
