package com.climbtheworld.app.networking.voicetools;

public interface IVoiceDetector {
    boolean onAudio(byte[] frame, int numberOfReadBytes, double energy);
}
