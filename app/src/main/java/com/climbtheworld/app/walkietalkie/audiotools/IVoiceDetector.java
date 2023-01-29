package com.climbtheworld.app.walkietalkie.audiotools;

public interface IVoiceDetector {
	boolean onAudio(short[] frame, int numberOfReadBytes, double energy);
}
