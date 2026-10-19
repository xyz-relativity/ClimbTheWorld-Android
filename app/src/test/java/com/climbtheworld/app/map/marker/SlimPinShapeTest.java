package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The slim pins are anchored by the bottom edge of their bitmap, on the map and in the augmented
 * reality view, so the tip has to be that edge, half way across.
 */
public class SlimPinShapeTest {
	private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	private static final Pattern PATH_TOKEN = Pattern.compile("[A-Za-z]|-?(?:\\d+\\.?\\d*|\\.\\d+)");

	@Test
	public void routePinTipIsTheBottomCentre() throws Exception {
		assertTipIsTheBottomCentre("ic_poi_route.xml");
	}

	@Test
	public void routeTopPinTipIsTheBottomCentre() throws Exception {
		assertTipIsTheBottomCentre("ic_poi_route_top.xml");
	}

	@Test
	public void routePinViewportIsInDp() throws Exception {
		// PoiMarkerDrawable places the grade, name and style icon in dp on this viewport.
		Element vector = loadVector("ic_poi_route.xml");

		assertEquals(vector.getAttributeNS(ANDROID_NS, "viewportWidth") + "dp",
				vector.getAttributeNS(ANDROID_NS, "width"));
		assertEquals(vector.getAttributeNS(ANDROID_NS, "viewportHeight") + "dp",
				vector.getAttributeNS(ANDROID_NS, "height"));
	}

	static void assertTipIsTheBottomCentre(String drawable) throws Exception {
		Element vector = loadVector(drawable);
		// The outline is the first path; anything after it is drawn inside.
		Element outline = (Element) vector.getElementsByTagName("path").item(0);
		float halfStroke = Float.parseFloat(outline.getAttributeNS(ANDROID_NS, "strokeWidth")) / 2;

		float[] tip = null;
		for (float[] point : pathPoints(outline.getAttributeNS(ANDROID_NS, "pathData"))) {
			if (tip == null || point[1] > tip[1]) {
				tip = point;
			}
		}

		assertTrue(drawable + " has no outline", tip != null);
		assertEquals(drawable + " tip is off centre",
				Float.parseFloat(vector.getAttributeNS(ANDROID_NS, "viewportWidth")) / 2, tip[0],
				0.01f);
		assertEquals(drawable + " tip is not the bottom edge",
				Float.parseFloat(vector.getAttributeNS(ANDROID_NS, "viewportHeight")),
				tip[1] + halfStroke, 0.01f);
	}

	static Element loadVector(String drawable) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder()
				.parse(new File("src/main/res/drawable/" + drawable))
				.getDocumentElement();
	}

	/**
	 * The end point of each segment of a path made of absolute moves, lines and arcs, which is
	 * how the pin outlines are written.
	 */
	private static List<float[]> pathPoints(String pathData) {
		List<String> tokens = new ArrayList<>();
		Matcher matcher = PATH_TOKEN.matcher(pathData);
		while (matcher.find()) {
			tokens.add(matcher.group());
		}

		List<float[]> points = new ArrayList<>();
		String command = null;
		int index = 0;
		while (index < tokens.size()) {
			if (Character.isLetter(tokens.get(index).charAt(0))) {
				command = tokens.get(index++);
				continue;
			}
			int arguments;
			switch (command) {
				case "M":
				case "L":
					arguments = 2;
					break;
				case "A":
					arguments = 7;
					break;
				default:
					fail("Unsupported path command " + command);
					return points;
			}
			index += arguments;
			points.add(new float[]{Float.parseFloat(tokens.get(index - 2)),
					Float.parseFloat(tokens.get(index - 1))});
		}
		return points;
	}
}
