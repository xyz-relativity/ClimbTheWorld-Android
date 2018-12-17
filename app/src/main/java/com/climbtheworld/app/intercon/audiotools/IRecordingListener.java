package com.climbtheworld.app.intercon.audiotools;

import android.media.AudioFormat;
import android.media.AudioTrack;

public interface IRecordingListener {
    int SAMPLE_RATE = 8000;
    int IN_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    int OUT_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, IN_CHANNELS, ENCODING);
    void onRecordingStarted();
    void onRawAudio(byte[] frame, int numberOfReadBytes);
    void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms);
    void onRecordingDone();
}
