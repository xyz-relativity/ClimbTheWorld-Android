package com.climbtheworld.app.walkietalkie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
	public void isBlank() {
		assertTrue(Callsigns.isBlank(null));
		assertTrue(Callsigns.isBlank(""));
		assertTrue(Callsigns.isBlank(" "));
		assertFalse(Callsigns.isBlank("a"));
		assertFalse(Callsigns.isBlank(" a "));
	}
}
