package com.climbtheworld.app.utils;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import needle.UiRelatedTask;

/**
 * The loads of a screen: the background tasks whose result only matters to the screen, which are
 * cancelled when it closes. A cancelled task that has not started does no work, and none comes
 * back to the UI thread, see UiRelatedTask, where it would fill the views, move the map or show a
 * dialog of an activity that is destroyed.
 * <p>
 * Not for a task that stores something: cancelled before it starts, it would store nothing.
 * <p>
 * Used on the UI thread only.
 */
public final class ScreenLoads {
	// Held weakly, as nothing tells when a task is done: until then its executor, and after its
	// work the UI thread's queue, keep it.
	private final Set<UiRelatedTask<?>> tasks = Collections.newSetFromMap(new WeakHashMap<>());
	private boolean cancelled;

	/**
	 * Takes a load to cancel with the screen, and hands it back to be run. One started once the
	 * screen is closed, by something reaching it late, is cancelled right away.
	 */
	public <Result> UiRelatedTask<Result> add(UiRelatedTask<Result> task) {
		if (cancelled) {
			task.cancel();
		} else {
			tasks.add(task);
		}
		return task;
	}

	/** Cancels the loads queued or running, and the ones added from here on. */
	public void cancel() {
		cancelled = true;
		for (UiRelatedTask<?> task : tasks) {
			task.cancel();
		}
		tasks.clear();
	}
}
