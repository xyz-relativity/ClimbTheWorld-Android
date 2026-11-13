package com.climbtheworld.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import needle.UiRelatedTask;

public class ScreenLoadsTest {
	/** A load that is never run: only whether it is cancelled is looked at. */
	private static final class Load extends UiRelatedTask<Void> {
		@Override
		protected Void doWork() {
			return null;
		}

		@Override
		protected void thenDoUiRelatedWork(Void result) {
		}

		boolean cancelled() {
			return isCanceled();
		}
	}

	@Test
	public void handsALoadBackAsItIsWhileTheScreenIsOpen() {
		ScreenLoads loads = new ScreenLoads();
		Load load = new Load();
		assertSame(load, loads.add(load));
		assertFalse(load.cancelled());
	}

	@Test
	public void cancelsEveryLoadAdded() {
		ScreenLoads loads = new ScreenLoads();
		Load first = new Load();
		Load second = new Load();
		loads.add(first);
		loads.add(second);
		loads.cancel();
		assertTrue(first.cancelled());
		assertTrue(second.cancelled());
	}

	@Test
	public void cancelsALoadAddedOnceTheScreenIsClosed() {
		ScreenLoads loads = new ScreenLoads();
		loads.cancel();
		Load late = new Load();
		// Still handed back: the executor it goes to then finds it cancelled.
		assertSame(late, loads.add(late));
		assertTrue(late.cancelled());
	}

	@Test
	public void leavesTheLoadsOfAnotherScreenAlone() {
		ScreenLoads closed = new ScreenLoads();
		ScreenLoads open = new ScreenLoads();
		Load load = new Load();
		open.add(load);
		closed.cancel();
		assertFalse(load.cancelled());
	}
}
