package com.climbtheworld.app.walkietalkie.application.states;

/**
 * The microphone level as the level bar shows it: the peak of each audio frame, falling off
 * over the next frames, from 0 to 100. It tells when the bar is to change, so that the bar is
 * not set for every frame, 50 times a second for as long as the walkie-talkie runs: only when
 * the level it would show differs, a few times a second at most, and never while nobody can
 * see it.
 */
final class LevelMeter {
	/** The bar stays as it is. */
	static final int NONE = -1;
	// How much of the last peak is left a frame later, under a quieter one.
	private static final double FALL_OFF = 0.575;
	// The bar changes no more often than this.
	static final long MIN_INTERVAL_MS = 50;

	private double lastPeak;
	// The level the bar was last told, NONE when it may show another, see barChanged.
	private int told = NONE;
	private long toldAtMs;

	/**
	 * Takes the peak of an audio frame.
	 *
	 * @param peak    the peak of the frame, from 0 to 1
	 * @param nowMs   the time of the frame, on a clock that only goes forward
	 * @param visible whether the bar is on screen
	 * @return the level for the bar to show, from 0 to 100, or NONE to leave it as it is
	 */
	synchronized int next(double peak, long nowMs, boolean visible) {
		if (lastPeak > peak) {
			peak = lastPeak * FALL_OFF;
		}
		lastPeak = peak;

		int level = (int) (peak * 100);
		if (!visible || level == told || (told != NONE && nowMs - toldAtMs < MIN_INTERVAL_MS)) {
			return NONE;
		}
		told = level;
		toldAtMs = nowMs;
		return level;
	}

	/**
	 * Takes it that the bar was set by something else, as to 0 once a recording is done, so the
	 * next level is shown whatever it is.
	 */
	synchronized void barChanged() {
		told = NONE;
	}
}
