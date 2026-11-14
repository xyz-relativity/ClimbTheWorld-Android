package com.climbtheworld.app.storage.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SharedLoadTest {
	private final SharedLoad<String> load = new SharedLoad<>();

	@Test
	public void theFirstAskOpensARun() {
		assertTrue(load.join("local"));
		assertEquals(Collections.singletonList("local"), load.begin());
	}

	@Test
	public void anAskBeforeTheRunStartsJoinsIt() {
		// Both tabs created by the pager, or both told a download ended.
		assertTrue(load.join("local"));
		assertFalse(load.join("remote"));

		assertEquals(Arrays.asList("local", "remote"), load.begin());
	}

	@Test
	public void aTabAskingTwiceIsDeliveredToOnce() {
		// A tab the pager creates again and selects at once.
		assertTrue(load.join("local"));
		assertFalse(load.join("local"));

		assertEquals(Collections.singletonList("local"), load.begin());
	}

	@Test
	public void anAskOnceTheRunStartedOpensTheNextRun() {
		// The run may have read before the download that made the tab ask ended.
		assertTrue(load.join("local"));
		assertEquals(Collections.singletonList("local"), load.begin());

		assertTrue(load.join("remote"));
		assertFalse(load.join("local"));
		assertEquals(Arrays.asList("remote", "local"), load.begin());
	}

	@Test
	public void anAskAfterDeliveryOpensANewRun() {
		load.join("local");
		load.begin();

		assertTrue(load.join("local"));
		assertEquals(Collections.singletonList("local"), load.begin());
	}

	@Test
	public void aRunThatFailedDoesNotBlockTheNext() {
		load.join("local");
		// The run starts, then fails before delivering.
		load.begin();

		assertTrue(load.join("remote"));
	}

	@Test
	public void keepsNoListenerOnceTheRunStarted() {
		load.join("local");
		load.join("remote");
		load.begin();

		// A second start, which never happens, finds nobody left to deliver to.
		assertTrue(load.begin().isEmpty());
	}
}
