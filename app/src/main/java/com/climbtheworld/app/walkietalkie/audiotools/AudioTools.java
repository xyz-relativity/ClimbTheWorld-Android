package com.climbtheworld.app.walkietalkie.audiotools;

public class AudioTools {
	private AudioTools() {
		//hide utility class constructor.
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

	public static int PEAK_INDEX = 0;
	public static int RMS_INDEX = 1;

	public static double[] getSignalCharacteristics(short[] samples) {
		double rms = 0f;
		double peak = 0f;
		for (short sample : samples) {
			double normSample = sample / 32768f;
			double abs = Math.abs(normSample);
			if (abs > peak) {
				peak = abs;
			}

			rms += normSample * normSample;
		}

		rms = (float) Math.sqrt(rms / samples.length);

		return new double[]{peak, rms};
	}
}
