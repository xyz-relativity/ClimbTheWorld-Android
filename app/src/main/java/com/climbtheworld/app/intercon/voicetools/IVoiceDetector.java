package com.climbtheworld.app.intercon.voicetools;

public interface IVoiceDetector {
    boolean onAudio(byte[] frame, int numberOfReadBytes, double energy);
}
