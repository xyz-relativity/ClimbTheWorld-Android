package com.climbtheworld.app.intercon.audiotools;

import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.LinkedList;
import java.util.List;

public class RecordingThread implements Runnable {
    private final AudioRecord recorder;
    private volatile boolean isRecording = false;
    private List<IRecordingListener> audioListeners = new LinkedList<>();

    public RecordingThread () {
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, IRecordingListener.AUDIO_SAMPLE_RATE,
                IRecordingListener.AUDIO_CHANNELS_IN, IRecordingListener.AUDIO_ENCODING,
                IRecordingListener.AUDIO_BUFFER_SIZE);
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
        byte[] recordingBuffer = new byte[IRecordingListener.AUDIO_BUFFER_SIZE];

        // Infinite loop until microphone button is released
        float[] samples = new float[IRecordingListener.AUDIO_BUFFER_SIZE / 2];

        // Start Recording
        recorder.startRecording();

        for (IRecordingListener listener: audioListeners) {
            listener.onRecordingStarted();
        }

        while (isRecording) {
            int numberOfShort = recorder.read(recordingBuffer, 0, IRecordingListener.AUDIO_BUFFER_SIZE);

            for (IRecordingListener listener: audioListeners) {
                listener.onRawAudio(recordingBuffer, numberOfShort);
            }

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
