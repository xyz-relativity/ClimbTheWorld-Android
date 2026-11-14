package com.climbtheworld.app.augmentedreality.placement;

import java.util.function.Consumer;

/**
 * What one screen hands over to the next one to open, as an A.R. its ARCore world: offered, it
 * waits to be taken, for a while only, and is let go of then, being costly to keep. The next
 * screen mostly opens at once and takes it. It may also never open, or close before it takes
 * anything, and nothing tells the offer, which would otherwise wait for as long as the app runs.
 * <p>
 * One at a time: offering another lets go of the one waiting. It knows nothing of the screens,
 * so it keeps none of them alive. Only to be used from one thread.
 */
public final class HandoffOffer<T> {
	private final Timer timer;
	private final long keptMillis;
	private final Consumer<T> letGo;
	// Null without one waiting.
	private T offered;
	// Ends the wait of the offer waiting; null without one.
	private Runnable timeUp;

	/**
	 * @param keptMillis how long an offer waits to be taken
	 * @param letGo      given each offer nobody took: its time is up, another took its place, or
	 *                   it was withdrawn
	 */
	public HandoffOffer(Timer timer, long keptMillis, Consumer<T> letGo) {
		this.timer = timer;
		this.keptMillis = keptMillis;
		this.letGo = letGo;
	}

	/**
	 * Offers something to whoever takes it next, in place of what was waiting.
	 */
	public void offer(T value) {
		withdraw();
		offered = value;
		// One for each offer, which tells them apart, rather than what is offered, which may be
		// offered again once taken.
		timeUp = new Runnable() {
			@Override
			public void run() {
				// Not for a timer that ran what was cancelled: the offer was taken or let go
				// of, and another may be waiting, which has its own time.
				if (timeUp == this) {
					withdraw();
				}
			}
		};
		timer.schedule(timeUp, keptMillis);
	}

	/**
	 * @return what was offered, which is the taker's from now on, and no longer let go of here;
	 * null with nothing waiting
	 */
	public T take() {
		T taken = offered;
		clear();
		return taken;
	}

	/**
	 * Lets go of what is waiting, if anything, as nobody is to take it.
	 */
	public void withdraw() {
		T untaken = offered;
		if (untaken == null) {
			return;
		}
		clear();
		letGo.accept(untaken);
	}

	private void clear() {
		if (timeUp != null) {
			timer.cancel(timeUp);
		}
		timeUp = null;
		offered = null;
	}

	/** Runs tasks later, on the thread of the offer. */
	public interface Timer {
		void schedule(Runnable task, long delayMillis);

		/** Takes back the task, when it is scheduled and has not run yet. */
		void cancel(Runnable task);
	}
}
