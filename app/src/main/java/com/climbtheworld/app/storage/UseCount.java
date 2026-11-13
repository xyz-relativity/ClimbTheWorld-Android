package com.climbtheworld.app.storage;

/**
 * Counts who uses something shared that is costly to keep, to let go of it once nobody does. It
 * waits a while first, as one user often makes way for the next: a screen that restarts itself
 * is destroyed just before the new one is created, or only after, and what the first one loaded
 * is still there for the next either way.
 * <p>
 * It knows nothing of its users, so it keeps none of them alive. Only to be used from one
 * thread.
 */
final class UseCount {
	private final Timer timer;
	private final long graceMillis;
	private final Runnable whenUnused;
	private int users;

	/**
	 * @param graceMillis how long nobody has to use it before it is let go of
	 * @param letGo       run once nobody used it for that long, and again each time that
	 *                    happens
	 */
	UseCount(Timer timer, long graceMillis, Runnable letGo) {
		this.timer = timer;
		this.graceMillis = graceMillis;
		this.whenUnused = () -> {
			// Not for a timer that ran what was cancelled.
			if (users == 0) {
				letGo.run();
			}
		};
	}

	/** A user more, to be matched by a {@link #release}. */
	void acquire() {
		if (users == 0) {
			timer.cancel(whenUnused);
		}
		users++;
	}

	/** A user less. One too many is ignored, so the count never goes wrong for the others. */
	void release() {
		if (users == 0) {
			return;
		}
		users--;
		if (users == 0) {
			timer.schedule(whenUnused, graceMillis);
		}
	}

	/** Runs tasks later, on the thread of the count. */
	interface Timer {
		void schedule(Runnable task, long delayMillis);

		/** Takes back the task, when it is scheduled and has not run yet. */
		void cancel(Runnable task);
	}
}
