package com.climbtheworld.app.walkietalkie.audiotools;

import org.concentus.OpusApplication;
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
			encoder.setBitrate(96000);
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

	public static short[] BytesToShorts(byte[] input) {
		return BytesToShorts(input, 0, input.length);
	}

	public static short[] BytesToShorts(byte[] input, int offset, int length) {
		short[] processedValues = new short[length / 2];
		for (int c = 0; c < processedValues.length; c++) {
			short a = (short) (((int) input[(c * 2) + offset]) & 0xFF);
			short b = (short) (((int) input[(c * 2) + 1 + offset]) << 8);
			processedValues[c] = (short) (a | b);
		}

		return processedValues;
	}

	public static byte[] ShortsToBytes(short[] input) {
		return ShortsToBytes(input, 0, input.length);
	}

	public static byte[] ShortsToBytes(short[] input, int offset, int length) {
		byte[] processedValues = new byte[length * 2];
		for (int c = 0; c < length; c++) {
			processedValues[c * 2] = (byte) (input[c + offset] & 0xFF);
			processedValues[c * 2 + 1] = (byte) ((input[c + offset] >> 8) & 0xFF);
		}

		return processedValues;
	}
}
