package com.climbtheworld.app.walkietalkie.audiotools;

import org.concentus.OpusApplication;
import org.concentus.OpusConstants;
import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.concentus.OpusException;
import org.concentus.OpusSignal;

public class OpusTools {
	private OpusTools() {
		//hide constructor
	}

	public static OpusEncoder getEncoder() {
		OpusEncoder encoder = null;
		try {
			encoder = new OpusEncoder(IRecordingListener.AUDIO_SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
			encoder.setBitrate(OpusConstants.OPUS_AUTO);
			encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
			encoder.setUseVBR(true);
			encoder.setComplexity(10);
		} catch (OpusException e) {
			e.printStackTrace();
		}

		return encoder;
	}

	public static OpusDecoder getDecoder() {
		OpusDecoder decoder = null;
		try {
			decoder = new OpusDecoder(IRecordingListener.AUDIO_SAMPLE_RATE, 1);
		} catch (OpusException e) {
			e.printStackTrace();
		}

		return decoder;
	}
}
