package com.climbtheworld.app.walkietalkie.audiotools;

import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.concurrent.BlockingQueue;

public class PlaybackThread extends Thread {
	private final BlockingQueue<byte[]> queue;
	private volatile boolean isPlaying = false;

	public PlaybackThread(BlockingQueue<byte[]> queue) {
		this.queue = queue;
	}

	public void stopPlayback() {
		isPlaying = false;
		queue.offer(new byte[0]); //wake the thread up.
	}

	@Override
	public void run() {
		AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
				IRecordingListener.AUDIO_SAMPLE_RATE, IRecordingListener.AUDIO_CHANNELS_OUT, IRecordingListener.AUDIO_ENCODING,
				IRecordingListener.AUDIO_BUFFER_SIZE, AudioTrack.MODE_STREAM);

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
