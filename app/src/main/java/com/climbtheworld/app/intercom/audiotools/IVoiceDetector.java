package com.climbtheworld.app.intercom.audiotools;

public interface IVoiceDetector {
    boolean onAudio(byte[] frame, int numberOfReadBytes, double energy);
}
