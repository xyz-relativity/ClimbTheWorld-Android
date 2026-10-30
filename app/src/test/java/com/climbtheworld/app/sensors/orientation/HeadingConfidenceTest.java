package com.climbtheworld.app.sensors.orientation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HeadingConfidenceTest {
	@Test
	public void stepsWithTheUncertainty() {
		assertEquals(HeadingConfidence.SURE, HeadingConfidence.of(0, null));
		assertEquals(HeadingConfidence.SURE,
				HeadingConfidence.of(HeadingConfidence.SURE_DEGREES, null));
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(HeadingConfidence.SURE_DEGREES + 0.1, null));
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(HeadingConfidence.ROUGH_DEGREES, null));
		assertEquals(HeadingConfidence.UNSURE,
				HeadingConfidence.of(HeadingConfidence.ROUGH_DEGREES + 0.1, null));
		assertEquals(HeadingConfidence.UNSURE, HeadingConfidence.of(180, null));
	}

	@Test
	public void sureWhenNothingTells() {
		assertEquals(HeadingConfidence.SURE, HeadingConfidence.of(Double.NaN, null));
		assertEquals(HeadingConfidence.SURE,
				HeadingConfidence.of(Double.NaN, HeadingConfidence.UNSURE));
	}

	@Test
	public void keepsItsLevelAboutAStep() {
		double step = HeadingConfidence.SURE_DEGREES;
		double leeway = HeadingConfidence.HYSTERESIS_DEGREES;
		// Sure, and wavering just past the step: still sure.
		assertEquals(HeadingConfidence.SURE,
				HeadingConfidence.of(step + leeway, HeadingConfidence.SURE));
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(step + leeway + 0.1, HeadingConfidence.SURE));
		// Rough, and wavering just under the step: still rough.
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(step - leeway + 0.1, HeadingConfidence.ROUGH));
		assertEquals(HeadingConfidence.SURE,
				HeadingConfidence.of(step - leeway, HeadingConfidence.ROUGH));
	}

	@Test
	public void keepsItsLevelAboutTheRoughStep() {
		double step = HeadingConfidence.ROUGH_DEGREES;
		double leeway = HeadingConfidence.HYSTERESIS_DEGREES;
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(step + leeway, HeadingConfidence.ROUGH));
		assertEquals(HeadingConfidence.UNSURE,
				HeadingConfidence.of(step + leeway + 0.1, HeadingConfidence.ROUGH));
		assertEquals(HeadingConfidence.UNSURE,
				HeadingConfidence.of(step - leeway + 0.1, HeadingConfidence.UNSURE));
		assertEquals(HeadingConfidence.ROUGH,
				HeadingConfidence.of(step - leeway, HeadingConfidence.UNSURE));
	}

	@Test
	public void canStepTwoLevelsAtOnce() {
		assertEquals(HeadingConfidence.UNSURE,
				HeadingConfidence.of(180, HeadingConfidence.SURE));
		assertEquals(HeadingConfidence.SURE,
				HeadingConfidence.of(0, HeadingConfidence.UNSURE));
	}
}
