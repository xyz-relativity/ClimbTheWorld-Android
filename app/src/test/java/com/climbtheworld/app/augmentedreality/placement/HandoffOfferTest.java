package com.climbtheworld.app.augmentedreality.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HandoffOfferTest {
	private static final long KEPT_MILLIS = 30_000;

	/** Holds the tasks scheduled, to be run by the test as time would. */
	private static class FakeTimer implements HandoffOffer.Timer {
		final List<Runnable> scheduled = new ArrayList<>();
		long delayMillis;

		@Override
		public void schedule(Runnable task, long delayMillis) {
			scheduled.add(task);
			this.delayMillis = delayMillis;
		}

		@Override
		public void cancel(Runnable task) {
			scheduled.remove(task);
		}

		void elapse() {
			List<Runnable> due = new ArrayList<>(scheduled);
			scheduled.clear();
			for (Runnable task : due) {
				task.run();
			}
		}
	}

	private final FakeTimer timer = new FakeTimer();
	private final List<String> letGo = new ArrayList<>();
	private final HandoffOffer<String> offer = new HandoffOffer<>(timer, KEPT_MILLIS, letGo::add);

	@Test
	public void theNextScreenTakesWhatWasOffered() {
		// The A.R. view is paused for the editor, which opens and takes its world.
		offer.offer("world");

		assertEquals("world", offer.take());
		assertTrue(letGo.isEmpty());
	}

	@Test
	public void nothingIsTakenWithoutAnOffer() {
		assertNull(offer.take());
		assertTrue(timer.scheduled.isEmpty());
	}

	@Test
	public void whatWasOfferedIsTakenOnlyOnce() {
		offer.offer("world");
		offer.take();

		assertNull(offer.take());
	}

	@Test
	public void anOfferNobodyTakesIsLetGoOfOnceItsTimeIsUp() {
		// The editor closes to another app, and the A.R. view never comes back to take it.
		offer.offer("world");
		// Not at once: the next screen is about to open.
		assertTrue(letGo.isEmpty());
		assertEquals(KEPT_MILLIS, timer.delayMillis);

		timer.elapse();
		assertEquals(Collections.singletonList("world"), letGo);
		assertNull(offer.take());
	}

	@Test
	public void whatWasTakenIsNotLetGoOf() {
		offer.offer("world");
		offer.take();

		// Its time is no longer counted.
		assertTrue(timer.scheduled.isEmpty());
		timer.elapse();
		assertTrue(letGo.isEmpty());
	}

	@Test
	public void whatWasTakenIsKeptWhenTheTimerRunsWhatWasTakenBack() {
		offer.offer("world");
		Runnable task = timer.scheduled.get(0);
		offer.take();

		// As a timer would that had already started the task.
		task.run();
		assertTrue(letGo.isEmpty());
	}

	@Test
	public void anotherOfferLetsGoOfTheOneWaiting() {
		offer.offer("first");
		offer.offer("second");

		assertEquals(Collections.singletonList("first"), letGo);
		// Only the second one's time is counted.
		assertEquals(1, timer.scheduled.size());
		assertEquals("second", offer.take());
	}

	@Test
	public void theTimeOfAnOfferTakenDoesNotEndTheNextOne() {
		// The editor took the view's world, and hands it back as it closes.
		offer.offer("to the editor");
		Runnable first = timer.scheduled.get(0);
		offer.take();
		offer.offer("back to the view");

		first.run();
		assertTrue(letGo.isEmpty());
		assertEquals("back to the view", offer.take());
	}

	@Test
	public void theTimeOfAnOfferReplacedDoesNotEndTheNextOne() {
		offer.offer("first");
		Runnable first = timer.scheduled.get(0);
		offer.offer("second");

		first.run();
		assertEquals(Collections.singletonList("first"), letGo);
		assertEquals("second", offer.take());
	}

	@Test
	public void theSameThingOfferedAgainHasItsOwnTime() {
		// The world goes to the editor, comes back, and is offered again.
		String world = "world";
		offer.offer(world);
		Runnable first = timer.scheduled.get(0);
		offer.take();
		offer.offer(world);

		first.run();
		assertTrue(letGo.isEmpty());
		assertSame(world, offer.take());
	}

	@Test
	public void eachOfferWaitsItsFullTime() {
		offer.offer("first");
		offer.take();
		offer.offer("second");
		assertEquals(1, timer.scheduled.size());
		assertEquals(KEPT_MILLIS, timer.delayMillis);

		timer.elapse();
		assertEquals(Collections.singletonList("second"), letGo);
	}

	@Test
	public void anOfferWithdrawnIsLetGoOfAtOnceAndOnlyOnce() {
		// The A.R. view starts over with ARCore turned off, so nobody is to take it.
		offer.offer("world");
		Runnable task = timer.scheduled.get(0);
		offer.withdraw();
		assertEquals(Collections.singletonList("world"), letGo);
		assertTrue(timer.scheduled.isEmpty());

		task.run();
		offer.withdraw();
		assertEquals(Collections.singletonList("world"), letGo);
		assertNull(offer.take());
	}

	@Test
	public void withdrawingWithoutAnOfferDoesNothing() {
		offer.withdraw();

		assertTrue(letGo.isEmpty());
	}

	@Test
	public void whatWasTakenIsNotLetGoOfByWithdrawing() {
		// The editor took the world; the old A.R. view, destroyed after, withdraws nothing.
		offer.offer("world");
		offer.take();
		offer.withdraw();

		assertTrue(letGo.isEmpty());
	}

	@Test
	public void offersAreLetGoOfInTheOrderTheyWentUntaken() {
		offer.offer("first");
		offer.offer("second");
		timer.elapse();
		offer.offer("third");
		offer.withdraw();

		assertEquals(Arrays.asList("first", "second", "third"), letGo);
	}
}
