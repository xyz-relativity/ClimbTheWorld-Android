package com.climbtheworld.app.intercon.voicetools;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RecordingThread implements Runnable {
    private static final int SAMPLE_RATE = 22050;
    private final AudioRecord recorder;
    public static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private volatile boolean isRecording = false;
    private List<IRecordingListener> audioListeners = new LinkedList<>();

    public RecordingThread () {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);
    }

    public void stopRecording() {
        if (recorder != null) {
            recorder.stop();
        }
        isRecording = false;
    }

    public void addListener (IRecordingListener listener) {
        audioListeners.add(listener);
    }

    public void removeListener (IRecordingListener listener) {
        audioListeners.remove(listener);
    }

    @Override
    public void run() {
        isRecording = true;
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] recordingBuffer = new byte[BUFFER_SIZE];

        // Infinite loop until microphone button is released
        float[] samples = new float[BUFFER_SIZE / 2];

        // Start Recording
        recorder.startRecording();

        for (IRecordingListener listener: audioListeners) {
            listener.onRecordingStarted();
        }

        while (isRecording) {
            int numberOfShort = recorder.read(recordingBuffer, 0, BUFFER_SIZE);

            // convert bytes to samples here
            for(int i = 0, s = 0; i < numberOfShort;) {
                int sample = 0;

                sample |= recordingBuffer[i++] & 0xFF; // (reverse these two lines
                sample |= recordingBuffer[i++] << 8;   //  if the format is big endian)

                // normalize to range of +/-1.0f
                samples[s++] = sample / 32768f;
            }

            float rms = 0f;
            float peak = 0f;
            for(float sample : samples) {

                float abs = Math.abs(sample);
                if(abs > peak) {
                    peak = abs;
                }

                rms += sample * sample;
            }

            rms = (float)Math.sqrt(rms / samples.length);
            for (IRecordingListener listener: audioListeners) {
                listener.onAudio(recordingBuffer, numberOfShort, peak, rms);
            }
        }

        recorder.stop();

        for (IRecordingListener listener: audioListeners) {
            listener.onRecordingDone();
        }
    }
}
