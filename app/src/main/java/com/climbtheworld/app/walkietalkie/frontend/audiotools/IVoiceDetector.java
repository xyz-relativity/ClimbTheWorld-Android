package com.climbtheworld.app.walkietalkie.frontend.audiotools;

public interface IVoiceDetector {
	boolean isVoiceDetected(short[] frame, int numberOfReadBytes, double energy);
}
