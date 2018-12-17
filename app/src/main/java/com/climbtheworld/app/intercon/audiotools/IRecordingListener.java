package com.climbtheworld.app.intercon.audiotools;

import android.media.AudioFormat;
import android.media.AudioTrack;

public interface IRecordingListener {
    int AUDIO_SAMPLE_RATE = 8000;
    int AUDIO_CHANNELS_IN = AudioFormat.CHANNEL_IN_MONO;
    int AUDIO_CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    int AUDIO_BUFFER_SIZE = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNELS_IN, AUDIO_ENCODING);
    void onRecordingStarted();
    void onRawAudio(byte[] frame, int numberOfReadBytes);
    void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms);
    void onRecordingDone();
}
