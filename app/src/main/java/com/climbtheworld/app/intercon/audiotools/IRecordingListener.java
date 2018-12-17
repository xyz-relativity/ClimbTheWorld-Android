package com.climbtheworld.app.intercon.audiotools;

import android.media.AudioFormat;
import android.media.AudioTrack;

public interface IRecordingListener {
    int SAMPLE_RATE = 22050;
    int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    void onRecordingStarted();
    void onRawAudio(byte[] frame, int numberOfReadBytes);
    void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms);
    void onRecordingDone();
}
