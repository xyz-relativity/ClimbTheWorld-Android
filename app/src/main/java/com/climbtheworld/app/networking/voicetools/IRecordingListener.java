package com.climbtheworld.app.networking.voicetools;

public interface IRecordingListener {
    void onRecordingStarted();
    void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms);
    void onRecordingDone();
}
