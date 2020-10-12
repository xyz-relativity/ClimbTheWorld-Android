package com.climbtheworld.app.intercom.audiotools;

import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.concurrent.BlockingQueue;

public class PlaybackThread implements Runnable {
	private final AudioTrack track;
	private final BlockingQueue<byte[]> queue;
	private volatile boolean isPlaying = false;

	public PlaybackThread(BlockingQueue<byte[]> queue) {
		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				IRecordingListener.AUDIO_SAMPLE_RATE, IRecordingListener.AUDIO_CHANNELS_OUT, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE, AudioTrack.MODE_STREAM);
		this.queue = queue;
	}

	public void stopPlayback() {
		isPlaying = false;
	}

	@Override
	public void run() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

		// Start Recording
		track.play();
		isPlaying = true;

		while (isPlaying) {
			byte[] data = new byte[0];
			try {
				data = queue.take(); //wait for data
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			track.write(data, 0, data.length);
		}

		track.stop();
		track.release();
	}
}
