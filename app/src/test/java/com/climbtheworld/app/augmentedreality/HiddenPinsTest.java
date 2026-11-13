package com.climbtheworld.app.augmentedreality;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HiddenPinsTest {
	private static final int CAPACITY = 3;

	private final HiddenPins<Long, String> pins = new HiddenPins<>(CAPACITY);

	@Test
	public void aHiddenPinIsKeptUntilItIsTaken() {
		assertNull(pins.hide(1L, "first"));

		assertEquals("first", pins.take(1L));
		assertNull(pins.take(1L));
		assertEquals(0, pins.size());
	}

	@Test
	public void noPinIsKeptForAKeyNeverHidden() {
		pins.hide(1L, "first");

		assertNull(pins.take(2L));
		assertEquals(1, pins.size());
	}

	@Test
	public void pinsAreKeptUpToTheCapacity() {
		assertNull(pins.hide(1L, "first"));
		assertNull(pins.hide(2L, "second"));
		assertNull(pins.hide(3L, "third"));

		assertEquals(CAPACITY, pins.size());
	}

	@Test
	public void pastTheCapacityThePinHiddenTheLongestIsDropped() {
		pins.hide(1L, "first");
		pins.hide(2L, "second");
		pins.hide(3L, "third");

		assertEquals("first", pins.hide(4L, "fourth"));
		assertEquals("second", pins.hide(5L, "fifth"));

		assertEquals(CAPACITY, pins.size());
		assertNull(pins.take(1L));
		assertNull(pins.take(2L));
		assertEquals("third", pins.take(3L));
		assertEquals("fourth", pins.take(4L));
		assertEquals("fifth", pins.take(5L));
	}

	@Test
	public void aPinShownAndHiddenAgainCountsAsHiddenJustNow() {
		pins.hide(1L, "first");
		pins.hide(2L, "second");
		pins.hide(3L, "third");
		assertEquals("first", pins.take(1L));
		assertNull(pins.hide(1L, "first"));

		assertEquals("second", pins.hide(4L, "fourth"));
		assertEquals("third", pins.hide(5L, "fifth"));
		assertEquals("first", pins.hide(6L, "sixth"));
	}

	@Test
	public void takingAPinMakesRoomForAnother() {
		pins.hide(1L, "first");
		pins.hide(2L, "second");
		pins.hide(3L, "third");
		pins.take(2L);

		assertNull(pins.hide(4L, "fourth"));
		assertEquals(CAPACITY, pins.size());
	}

	@Test
	public void anotherPinHiddenForTheSameKeyTakesThePlaceOfTheOneKept() {
		pins.hide(1L, "first");
		pins.hide(2L, "second");

		assertEquals("first", pins.hide(1L, "first again"));

		assertEquals(2, pins.size());
		assertEquals("first again", pins.take(1L));
	}

	@Test
	public void aKeyHiddenAgainCountsAsHiddenJustNow() {
		pins.hide(1L, "first");
		pins.hide(2L, "second");
		pins.hide(3L, "third");
		pins.hide(1L, "first again");

		assertEquals("second", pins.hide(4L, "fourth"));
		assertEquals("first again", pins.take(1L));
	}

	@Test
	public void thePinKeptIsNotDroppedWhenHiddenAgain() {
		String pin = "first";
		pins.hide(1L, pin);

		assertNull(pins.hide(1L, pin));
		assertEquals(1, pins.size());
		assertEquals(pin, pins.take(1L));
	}

	@Test
	public void thePinsOfTheKeysMatchingAreTakenOutTheLongestHiddenFirst() {
		pins.hide(3L, "third");
		pins.hide(2L, "second");
		pins.hide(1L, "first");

		assertEquals(Arrays.asList("third", "first"), pins.takeIf(key -> key % 2 == 1));

		assertEquals(1, pins.size());
		assertNull(pins.take(1L));
		assertNull(pins.take(3L));
		assertEquals("second", pins.take(2L));
	}

	@Test
	public void noPinIsTakenOutWhenNoKeyMatches() {
		pins.hide(1L, "first");

		assertEquals(Collections.emptyList(), pins.takeIf(key -> false));
		assertEquals(1, pins.size());
	}

	@Test
	public void withoutCapacityEveryPinIsDroppedAtOnce() {
		HiddenPins<Long, String> none = new HiddenPins<>(0);

		assertEquals("first", none.hide(1L, "first"));

		assertEquals(0, none.size());
		assertNull(none.take(1L));
	}
}
