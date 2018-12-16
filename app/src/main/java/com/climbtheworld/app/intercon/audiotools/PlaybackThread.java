package com.climbtheworld.app.intercon.audiotools;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.concurrent.BlockingQueue;

public class PlaybackThread implements Runnable {
    private final AudioTrack track;
    private final BlockingQueue<byte[]> queue;
    private volatile boolean isPlaying = false;

    public PlaybackThread(BlockingQueue<byte[]> queue) {
        track = new AudioTrack(AudioManager.STREAM_MUSIC,
                IRecordingListener.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, IRecordingListener.BUFFER_SIZE, AudioTrack.MODE_STREAM);
        this.queue = queue;
    }

    public void stopPlayback() {
        if (track != null) {
            isPlaying = false;
            track.stop();
        }
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
    }
}
