package com.climbtheworld.app.walkietalkie.audiotools;

public interface IVoiceDetector {
	boolean isVoiceDetected(short[] frame, int numberOfReadBytes, double energy);
}
