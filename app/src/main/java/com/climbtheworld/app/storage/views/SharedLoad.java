package com.climbtheworld.app.storage.views;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the views that ask for the same load at about the same time share one run of it. One that
 * asks while a run has not started reading joins it: the run reads after the ask, so its result is
 * as fresh as one of its own. One that asks once a run has started reading opens the next run, as
 * that one may have read before what made the view ask, such as a download ending.
 * <p>
 * A run that fails without delivering only loses its own views, as their own run would have, and
 * the next ask opens a new one. Keeps the views only until their run starts, the run then holding
 * them until it delivers, as a run of their own would.
 *
 * @param <Listener> what a run delivers to
 */
final class SharedLoad<Listener> {
	// The views of the run that has not started reading yet, in the order they asked; null when
	// there is none.
	private Set<Listener> waiting;

	/**
	 * @return true when this opens a run, which the caller then has to start; false when the
	 * listener joined a run that has not started reading yet, or was already in it
	 */
	synchronized boolean join(Listener listener) {
		if (waiting != null) {
			waiting.add(listener);
			return false;
		}
		waiting = new LinkedHashSet<>();
		waiting.add(listener);
		return true;
	}

	/**
	 * To be called by a run before it reads anything. From here on, an ask opens the next run.
	 *
	 * @return the listeners the run delivers to, in the order they asked
	 */
	synchronized List<Listener> begin() {
		List<Listener> listeners = waiting != null ? new ArrayList<>(waiting) : new ArrayList<>();
		waiting = null;
		return listeners;
	}
}
