package com.climbtheworld.app.intercom.audiotools;

import java.util.ArrayList;

public class AdaptiveVoiceDetector implements IVoiceDetector {
	private enum DetectorState {
		BEFORE,
		DURING,
		AFTER
	}

	public class NoiseLevel {
		public double mean = 0;
		public double standardDeviation = 0;

		public NoiseLevel(double mean, double standardDeviation) {
			this.mean = mean;
			this.standardDeviation = standardDeviation;
		}
	}

	private boolean voiceState = false;

	private DetectorState detectorState = DetectorState.BEFORE;
	private final int backgroundNoiseFrames;
	private final int startOfSpeechVoicedFrameCount;
	private final int endOfSpeechVoicedFrameCount;
	private final double thresholdRatio;
	private final int beginDelayFrames;
	private final int startOfSpeechHistoryFrameCount;
	private final int endOfSpeechHistoryFrameCount;
	private final NoiseLevel noiseLevel;
	private final double noiseLevelStdevFactor;

	private int beginDelayCount = 0;
	private int counter;

	private static class Tuple {
		final double energy;
		final double smoothedAverage;
		final double smoothedMedian;

		boolean voiced;

		Tuple(double energy, double smoothedAverage, double smoothedMedian) {
			this.energy = energy;
			this.smoothedAverage = smoothedAverage;
			this.smoothedMedian = smoothedMedian;
		}
	}

	private final ArrayList<Tuple> history = new ArrayList<>();

	private static final int SMOOTHING_LENGTH = 5; // Must be at least 2
	private final double[] smoothingBuffer = new double[SMOOTHING_LENGTH];
	private int frameCount = 0;

	public AdaptiveVoiceDetector(int backgroundNoiseFrames, int startOfSpeechVoicedFrameCount, int startOfSpeechHistoryFrameCount,
	                             int endOfSpeechVoicedFrameCount, int endOfSpeechHistoryFrameCount, double thresholdRatio) {
		this.backgroundNoiseFrames = backgroundNoiseFrames;

		this.startOfSpeechVoicedFrameCount = startOfSpeechVoicedFrameCount;
		this.endOfSpeechVoicedFrameCount = endOfSpeechVoicedFrameCount;
		this.thresholdRatio = thresholdRatio;

		this.beginDelayFrames = 1; // FIXME not hard coded
		this.startOfSpeechHistoryFrameCount = startOfSpeechHistoryFrameCount;
		this.endOfSpeechHistoryFrameCount = endOfSpeechHistoryFrameCount;

		this.noiseLevel = new NoiseLevel(0, 0);
		noiseLevelStdevFactor = 0.0;
	}


	// 0: Average
	// 1: Stdev
	private double[] computeRecentStats(ArrayList<Tuple> tuples, int begin, int count) {
		double mean = 0.0;
		double m2 = 0.0;
		for (int i = 0; i < count; i++) {
			final int bi = begin + i;
			final Tuple tuple = tuples.get(bi);
			final double energy = tuple.energy;
			final double delta = energy - mean;
			mean += delta / count;
			m2 += delta * (energy - mean);
		}

		final double[] ret = new double[2];

		ret[0] = mean;
		ret[1] = Math.sqrt(m2 / (count - 1));

		return ret;
	}

	private Tuple createTuple(double energy) {
		double sum = energy;

		smoothingBuffer[0] = energy;

		final int count = Math.min(1 + history.size(), SMOOTHING_LENGTH);
		for (int c = 1, i = history.size() - 1; c < count; c++, i--) {
			final Tuple t = history.get(i);
			double e = t.energy;
			sum += e;

			int j;
			for (j = 0; j < c; j++) {
				if (e < smoothingBuffer[j]) {
					final double tmp = smoothingBuffer[j];
					smoothingBuffer[j] = e;
					e = tmp;
				}
			}
			smoothingBuffer[j] = e;
		}


		final double average = sum / count;
		final double median = smoothingBuffer[count >> 1];

		return new Tuple(energy, average, median);
	}

	private boolean isVoiced(Tuple tuple) {
		final double average = null != noiseLevel ? noiseLevel.mean : lowSequenceAverage;
		final double stdevFactor = null != noiseLevel ? noiseLevelStdevFactor : lowSequenceStdevFactor;

		final double smoothedAverageRatio = (tuple.smoothedAverage - average) * stdevFactor;
		final double smoothedMedianRatio = (tuple.smoothedMedian - average) * stdevFactor;

		return smoothedAverageRatio >= thresholdRatio && smoothedMedianRatio >= thresholdRatio;
	}


	private double lowSequenceAverage = Double.MAX_VALUE;
	private double lowSequenceStdev = 0.0;
	private double lowSequenceStdevFactor = 0.0;
	private double maxEnergy = 0.0;

	private double computeEnergy(byte[] frame) {
		double sum = 0.0;
		int count = 0;

		int i = 0;
		while (i + 1 < frame.length) {
			int l = frame[i++] & 0xff;
			int h = frame[i++];
			int s = (h << 8) | l;
			sum += s * s;
			count += 1;
		}

		return Math.sqrt(sum / (double) count);
	}

	public boolean onAudio(byte[] frame, int numberOfReadBytes, double signalEnergy) {
		double energy = computeEnergy(frame);

		System.out.println(energy);

		if (beginDelayCount < beginDelayFrames) {
			beginDelayCount += 1;
			return voiceState;
		}

		if (frameCount == 0 && energy == 0.0) {
			return voiceState;
		}

		frameCount += 1;
		final Tuple tuple = createTuple(energy);
		history.add(tuple);

		if (frameCount < backgroundNoiseFrames) {
			return voiceState;
		}

		final int compareSequenceStart = frameCount - backgroundNoiseFrames;
		final double[] compareResult = computeRecentStats(history, compareSequenceStart, backgroundNoiseFrames);

		final boolean lowSequenceChanged = lowSequenceAverage > compareResult[0];
		if (lowSequenceChanged) {
			lowSequenceAverage = compareResult[0];
			lowSequenceStdev = compareResult[1];
			lowSequenceStdevFactor = 1.0 / lowSequenceStdev;

			for (final Tuple t : history) {
				t.voiced = isVoiced(t);
			}
		} else {
			tuple.voiced = isVoiced(tuple);
		}

		maxEnergy = Math.max(maxEnergy, energy);
		final double maxEnergyThreshold = (maxEnergy - lowSequenceAverage) * lowSequenceStdevFactor;

		if (maxEnergyThreshold < thresholdRatio) {
			return voiceState;
		}

		int voicedCount;
		switch (detectorState) {
			case BEFORE:

				if (!lowSequenceChanged) {
					voicedCount = 0;
					for (int i = 0, p = history.size() - 1; i < startOfSpeechHistoryFrameCount && p >= 0;
					     i += 1, p -= 1) {
						if (history.get(p).voiced) {
							voicedCount++;
						}
					}

					if (voicedCount >= startOfSpeechVoicedFrameCount) {
						counter = 0;
						detectorState = DetectorState.DURING;
						notifySpeechStart();
					}

					break;
				} else {
					//TODO: use index trickery instead of array list
					final ArrayList<Tuple> considering = new ArrayList<>(startOfSpeechHistoryFrameCount);
					voicedCount = 0;
					int index;
					for (index = 0; index < history.size(); index++) {
						if (considering.size() == startOfSpeechHistoryFrameCount) {
							final Tuple r = considering.remove(0);
							if (r.voiced) {
								voicedCount -= 1;
							}
						}

						final Tuple t = history.get(index);
						considering.add(t);

						if (t.voiced) {
							voicedCount += 1;
						}

						if (voicedCount >= startOfSpeechVoicedFrameCount) {
							counter = 0;
							detectorState = DetectorState.DURING;
							notifySpeechStart();

							for (; index < history.size(); index++) {
								if (counter < endOfSpeechHistoryFrameCount) {
									counter++;
								}
							}

							break; // the for loop
						}
					}
				}

				// Fall through to process for EoS

			case DURING:
				if (counter < endOfSpeechHistoryFrameCount) {
					counter++;
					break;
				}

				voicedCount = 0;
				for (int i = 0, p = history.size() - 1; i < endOfSpeechHistoryFrameCount;
				     i += 1, p -= 1) {
					if (history.get(p).voiced) {
						voicedCount++;
					}
				}

				if (voicedCount < endOfSpeechVoicedFrameCount) {
					detectorState = DetectorState.AFTER;
					notifySpeechEnd();
				}
				break;

			case AFTER:
				// Nothing to do
				break;

			default:
				// Should never happen
				break;
		}

		return voiceState;
	}

	private void notifySpeechEnd() {
		voiceState = false;
	}

	private void notifySpeechStart() {
		voiceState = true;
	}
}
