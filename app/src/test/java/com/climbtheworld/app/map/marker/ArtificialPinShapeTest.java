package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Element;

/**
 * The pins of artificial climbing are anchored like the others, by the bottom edge of their
 * bitmap, and MarkerUtils draws each glyph over its pin with both filling the same layout, so they
 * have to share the viewport.
 */
public class ArtificialPinShapeTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final String[] PINS = {
			"ic_poi_artificial_house.xml",
			"ic_poi_artificial_panel.xml",
			"ic_poi_artificial_tower.xml",
			"ic_poi_artificial_block.xml"};
	private static final String[] GLYPHS = {
			"ic_artificial_gym.xml",
			"ic_artificial_boulder_gym.xml",
			"ic_artificial_wall.xml",
			"ic_artificial_tower.xml",
			"ic_artificial_block.xml"};

	@Test
	public void pinTipsAreTheBottomCentre() throws Exception {
		for (String pin : PINS) {
			SlimPinShapeTest.assertTipIsTheBottomCentre(pin);
		}
	}

	@Test
	public void pinsAndGlyphsShareOneViewport() throws Exception {
		Element reference = SlimPinShapeTest.loadVector(PINS[0]);
		String width = reference.getAttributeNS(ANDROID_NS, "viewportWidth");
		String height = reference.getAttributeNS(ANDROID_NS, "viewportHeight");

		for (String[] drawables : new String[][]{PINS, GLYPHS}) {
			for (String drawable : drawables) {
				Element vector = SlimPinShapeTest.loadVector(drawable);
				assertEquals(drawable, width, vector.getAttributeNS(ANDROID_NS, "viewportWidth"));
				assertEquals(drawable, height, vector.getAttributeNS(ANDROID_NS, "viewportHeight"));
			}
		}
	}
}
