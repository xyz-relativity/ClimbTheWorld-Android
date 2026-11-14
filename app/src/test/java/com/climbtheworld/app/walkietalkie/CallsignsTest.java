package com.climbtheworld.app.walkietalkie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class CallsignsTest {
	private static final String FALLBACK = "Guest17";

	@Test
	public void emptyFallsBack() {
		assertEquals(FALLBACK, Callsigns.orFallback("", FALLBACK));
	}

	@Test
	public void nullFallsBack() {
		assertEquals(FALLBACK, Callsigns.orFallback(null, FALLBACK));
	}

	@Test
	public void blankFallsBack() {
		assertEquals(FALLBACK, Callsigns.orFallback("   ", FALLBACK));
		assertEquals(FALLBACK, Callsigns.orFallback("\t", FALLBACK));
		// No-break and ideographic spaces show nothing either.
		assertEquals(FALLBACK, Callsigns.orFallback(" 　", FALLBACK));
	}

	@Test
	public void nameIsKept() {
		assertEquals("Alice", Callsigns.orFallback("Alice", FALLBACK));
	}

	@Test
	public void spacesOfANameAreKept() {
		assertEquals("Al ice", Callsigns.orFallback("Al ice", FALLBACK));
		assertEquals(" Alice ", Callsigns.orFallback(" Alice ", FALLBACK));
	}

	@Test
	public void nameAtTheLengthCapIsKept() {
		// The field cuts a name at 10 UTF-16 units, which can leave half of a surrogate pair.
		String name = "Climber12🧗".substring(0, 10);
		assertEquals(name, Callsigns.orFallback(name, FALLBACK));
		assertEquals("Climber123", Callsigns.orFallback("Climber123", FALLBACK));
	}

	@Test
	public void fallbackIsTheSameOnEveryCall() {
		String first = Callsigns.orFallback("", FALLBACK);
		for (int i = 0; i < 5; i++) {
			assertSame(first, Callsigns.orFallback("", FALLBACK));
		}
	}

	@Test
	public void defaultCallsignAtTheEndsOfTheRandomRange() {
		assertEquals("Guest1000", Callsigns.defaultCallsign(0.0));
		assertEquals("Guest9999", Callsigns.defaultCallsign(Math.nextDown(1.0)));
	}

	@Test
	public void defaultCallsignInBetween() {
		assertEquals("Guest5500", Callsigns.defaultCallsign(0.5));
		Random random = new Random(42);
		for (int i = 0; i < 1000; i++) {
			String callsign = Callsigns.defaultCallsign(random.nextDouble());
			assertTrue(callsign, callsign.matches("Guest[1-9][0-9]{3}"));
		}
	}

	@Test
	public void defaultCallsignFitsTheEditField() {
		for (double random : new double[]{0.0, 0.5, Math.nextDown(1.0)}) {
			String callsign = Callsigns.defaultCallsign(random);
			// The edit field cuts a name at 10 UTF-16 units.
			assertTrue(callsign, callsign.length() <= 10);
			assertFalse(Callsigns.isBlank(callsign));
		}
	}

	@Test
	public void isBlank() {
		assertTrue(Callsigns.isBlank(null));
		assertTrue(Callsigns.isBlank(""));
		assertTrue(Callsigns.isBlank(" "));
		assertFalse(Callsigns.isBlank("a"));
		assertFalse(Callsigns.isBlank(" a "));
	}
}
