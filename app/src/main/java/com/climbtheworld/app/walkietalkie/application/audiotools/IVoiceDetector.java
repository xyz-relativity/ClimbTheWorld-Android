package com.climbtheworld.app.walkietalkie.application.audiotools;

public interface IVoiceDetector {
	boolean isVoiceDetected(short[] frame, int numberOfReadBytes, double energy);
}
