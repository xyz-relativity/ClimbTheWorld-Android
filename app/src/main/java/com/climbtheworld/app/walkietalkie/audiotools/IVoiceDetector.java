package com.climbtheworld.app.walkietalkie.audiotools;

public interface IVoiceDetector {
	boolean onAudio(byte[] frame, int numberOfReadBytes, double energy);
}
