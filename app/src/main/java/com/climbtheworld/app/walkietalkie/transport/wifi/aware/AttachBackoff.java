package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

/**
 * When to attach again after attaching to Wi-Fi Aware failed, and whether to tell the user. On a
 * phone where attaching keeps failing, retrying every few seconds keeps the radio busy for nothing
 * and a message on each failure piles up toasts. Retrying never stops: Wi-Fi Aware often comes back
 * once Wi-Fi is on again or the hotspot is off.
 */
final class AttachBackoff {
	static final long FIRST_DELAY_MS = 2_000;
	static final long MAX_DELAY_MS = 60_000;

	private long nextDelayMs = FIRST_DELAY_MS;
	private boolean failureShown;

	/** The delay before attaching again after a failure; each failure doubles the next one. */
	long nextDelayMs() {
		long delayMs = nextDelayMs;
		nextDelayMs = Math.min(delayMs * 2, MAX_DELAY_MS);
		return delayMs;
	}

	/** True for the first failure since the start or the last attach, so the user is told once. */
	boolean showFailure() {
		boolean show = !failureShown;
		failureShown = true;
		return show;
	}

	void onAttached() {
		nextDelayMs = FIRST_DELAY_MS;
		failureShown = false;
	}

	/** Wi-Fi Aware came back: whatever made the attaches fail may be gone, so try again soon. */
	void onAvailable() {
		nextDelayMs = FIRST_DELAY_MS;
	}
}
