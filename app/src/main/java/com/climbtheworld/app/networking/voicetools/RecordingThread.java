package com.climbtheworld.app.networking.voicetools;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RecordingThread implements Runnable {
    private static final int SAMPLE_RATE = 16000;
    private final AudioRecord recorder;
    private byte recordingBuffer[] = null;
    private int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private int bufferSize = minSize;
    private volatile boolean isRecording = false;
    private List<IRecordingListener> audioListeners = new LinkedList<>();

    public RecordingThread (IRecordingListener ... listeners) {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        audioListeners.addAll(Arrays.asList(listeners));
    }

    public void stopRecording() {
        if (recorder != null) {
            recorder.stop();
        }
        isRecording = false;
    }

    @Override
    public void run() {
        isRecording = true;
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        recordingBuffer = new byte[bufferSize];

        // Infinite loop until microphone button is released
        float[] samples = new float[bufferSize / 2];

        // Start Recording
        recorder.startRecording();

        for (IRecordingListener listener: audioListeners) {
            listener.onRecordingStarted();
        }

        while (isRecording) {
            int numberOfShort = recorder.read(recordingBuffer, 0, bufferSize);

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

        for (IRecordingListener listener: audioListeners) {
            listener.onRecordingDone();
        }
    }
}
