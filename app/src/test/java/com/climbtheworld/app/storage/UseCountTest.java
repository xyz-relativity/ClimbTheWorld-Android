package com.climbtheworld.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class UseCountTest {
	private static final long GRACE_MILLIS = 10_000;

	/** Holds the one task scheduled, to be run by the test as time would. */
	private static class FakeTimer implements UseCount.Timer {
		Runnable scheduled;
		long delayMillis;

		@Override
		public void schedule(Runnable task, long delayMillis) {
			scheduled = task;
			this.delayMillis = delayMillis;
		}

		@Override
		public void cancel(Runnable task) {
			if (scheduled == task) {
				scheduled = null;
			}
		}

		void elapse() {
			Runnable task = scheduled;
			scheduled = null;
			task.run();
		}
	}

	private final FakeTimer timer = new FakeTimer();
	private int letGo;
	private final UseCount count = new UseCount(timer, GRACE_MILLIS, () -> letGo++);

	@Test
	public void letsGoAWhileAfterTheLastUserLeft() {
		count.acquire();
		count.release();
		// Not at once: the next user may be about to come.
		assertEquals(0, letGo);
		assertEquals(GRACE_MILLIS, timer.delayMillis);

		timer.elapse();
		assertEquals(1, letGo);
	}

	@Test
	public void keepsWhileOneOfSeveralUsersIsLeft() {
		// The A.R. view, then the editor opened from it.
		count.acquire();
		count.acquire();
		count.release();
		assertNull(timer.scheduled);

		count.release();
		timer.elapse();
		assertEquals(1, letGo);
	}

	@Test
	public void keepsWhenTheNextUserComesBeforeTheLastOneLeaves() {
		// A screen restarting itself, the new one created before the old one is destroyed.
		count.acquire();
		count.acquire();
		count.release();

		assertNull(timer.scheduled);
		assertEquals(0, letGo);
	}

	@Test
	public void keepsWhenTheNextUserComesJustAfterTheLastOneLeft() {
		// A screen restarting itself, the old one destroyed before the new one is created.
		count.acquire();
		count.release();
		count.acquire();

		assertNull(timer.scheduled);
		assertEquals(0, letGo);
	}

	@Test
	public void keepsWhenTheTimerRunsWhatWasTakenBack() {
		count.acquire();
		count.release();
		Runnable task = timer.scheduled;
		count.acquire();

		// As a timer would that had already started the task.
		task.run();
		assertEquals(0, letGo);
	}

	@Test
	public void letsGoAgainAfterBeingUsedAgain() {
		count.acquire();
		count.release();
		timer.elapse();

		count.acquire();
		count.release();
		timer.elapse();
		assertEquals(2, letGo);
	}

	@Test
	public void ignoresAReleaseTooMany() {
		count.acquire();
		count.release();
		Runnable task = timer.scheduled;
		count.release();
		// Neither scheduled twice, nor counted against the next user.
		assertSame(task, timer.scheduled);

		count.acquire();
		count.acquire();
		count.release();
		assertNull(timer.scheduled);
		assertEquals(0, letGo);
	}
}
