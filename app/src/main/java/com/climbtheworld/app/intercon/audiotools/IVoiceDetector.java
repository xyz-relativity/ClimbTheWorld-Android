package com.climbtheworld.app.intercon.audiotools;

public interface IVoiceDetector {
    boolean onAudio(byte[] frame, int numberOfReadBytes, double energy);
}
