package com.climbtheworld.app.converter.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class GradeTableTest {
	@Test
	public void gradesAreConvertedAsInWikipediasTables() {
		assertSameRow(GradeSystem.french, "6a", GradeSystem.yds, "5.10a");
		assertSameRow(GradeSystem.french, "6a", GradeSystem.uiaa, "6+");
		assertSameRow(GradeSystem.french, "9a", GradeSystem.uiaa, "11+");
		assertSameRow(GradeSystem.french, "9a", GradeSystem.yds, "5.14d");
		assertSameRow(GradeSystem.french, "7b", GradeSystem.polish, "VI.4");
		assertSameRow(GradeSystem.french, "7b", GradeSystem.nordic, "8-");
		assertSameRow(GradeSystem.french, "7a+", GradeSystem.ukAdj, "E5");
		assertSameRow(GradeSystem.saxon, "VIIIa", GradeSystem.yds, "5.10c");
		assertSameRow(GradeSystem.vGrade, "V6", GradeSystem.french, "7b+");
		assertSameRow(GradeSystem.vGrade, "V6", GradeSystem.font, "7A");
	}

	@Test
	public void aGradeBetweenTwoShowsAsTheEasierOne() {
		// Wikipedia gives French 7b no UIAA grade, between VIII+ and IX-.
		assertEquals("8+", GradeSystem.uiaa.getGrade(GradeSystem.french.indexOf("7b")));
		assertEquals("7b", GradeSystem.french.getGrade(GradeSystem.font.indexOf("6C+")));
	}

	@Test
	public void everyFrenchGradeHasItsOwnRow() {
		String[] grades = {"4a", "4b", "4c", "5a", "5a+", "5b", "5b+", "5c", "5c+", "6a", "6a+",
				"6b", "6b+", "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b",
				"8b+", "8c", "8c+", "9a", "9a+", "9b", "9b+", "9c"};
		Set<Integer> rows = new HashSet<>();
		for (String grade : grades) {
			int row = GradeSystem.french.indexOf(grade);
			assertNotEquals(grade, -1, row);
			assertEquals(grade, GradeSystem.french.getGrade(row));
			rows.add(row);
		}
		assertEquals(grades.length, rows.size());
	}

	@Test
	public void uiaaHasNoHalfGrades() {
		for (String grade : GradeSystem.uiaa.getAllGrades()) {
			assertFalse(grade, grade.contains("/"));
		}
	}

	@Test
	public void everyRowShowsAGradeInEverySystem() {
		for (GradeSystem system : GradeSystem.printableValues()) {
			assertEquals(system.name(), GradeSystem.maxGrades, system.data.length);
			for (int i = 0; i < GradeSystem.maxGrades; ++i) {
				assertFalse(system.name() + " " + i, system.getGrade(i).isEmpty());
			}
		}
	}

	@Test
	public void eachGradeIsListedOnceWhereItStarts() {
		for (GradeSystem system : GradeSystem.printableValues()) {
			List<String> grades = system.getAllGrades();
			Set<String> seen = new HashSet<>();
			for (int position = 0; position < grades.size(); ++position) {
				String grade = grades.get(position);
				assertTrue(system.name() + " " + grade, seen.add(grade.toLowerCase()));
				int row = system.indexAt(position);
				assertEquals(row, system.indexOf(grade));
				assertEquals(position, system.positionOf(row));
			}
			for (int i = 0; i < GradeSystem.maxGrades; ++i) {
				assertEquals(system.getGrade(i), grades.get(system.positionOf(i)));
			}
		}
		assertEquals(-1, GradeSystem.uiaa.positionOf(-1));
		assertEquals(-1, GradeSystem.uiaa.indexAt(-1));
	}

	@Test
	public void gradesBetweenTwoAreReadAsTheHarderOne() {
		assertSameRow(GradeSystem.uiaa, "7+/8-", GradeSystem.uiaa, "8-");
		assertSameRow(GradeSystem.uiaa, " 7+ / 8- ", GradeSystem.uiaa, "8-");
		assertSameRow(GradeSystem.yds, "5.10b/c", GradeSystem.yds, "5.10c");
		assertSameRow(GradeSystem.yds, "5.12d/13a", GradeSystem.yds, "5.13a");
		assertSameRow(GradeSystem.french, "7a+/b", GradeSystem.french, "7b");
		assertSameRow(GradeSystem.french, "8a/a+", GradeSystem.french, "8a+");
		assertSameRow(GradeSystem.polish, "VI.3/3+", GradeSystem.polish, "VI.3+");
		assertSameRow(GradeSystem.polish, "VI+/1", GradeSystem.polish, "VI.1");
		assertSameRow(GradeSystem.vGrade, "V3/V4", GradeSystem.vGrade, "V4");
		assertEquals(-1, GradeSystem.uiaa.indexOf("x/y"));
		assertEquals(-1, GradeSystem.uiaa.indexOf(""));
	}

	@Test
	public void gradesWrittenOtherwiseAreReadButNeverListed() {
		assertSameRow(GradeSystem.french, "4", GradeSystem.french, "4b");
		assertSameRow(GradeSystem.french, "5+", GradeSystem.french, "5c");
		assertSameRow(GradeSystem.french, "3a", GradeSystem.french, "3");
		assertSameRow(GradeSystem.saxon, "VII", GradeSystem.saxon, "VIIb");
		assertSameRow(GradeSystem.yds, "5.10", GradeSystem.yds, "5.10b");
		assertSameRow(GradeSystem.yds, "5.11+", GradeSystem.yds, "5.11d");
		assertSameRow(GradeSystem.yds, "5.9+", GradeSystem.yds, "5.9");
		assertSameRow(GradeSystem.vGrade, "V8+", GradeSystem.vGrade, "V8");
		assertSameRow(GradeSystem.ukAdj, "HVD", GradeSystem.ukAdj, "VD");
		assertFalse(GradeSystem.french.getAllGrades().contains("4"));
		assertFalse(GradeSystem.saxon.getAllGrades().contains("VII"));
	}

	private static void assertSameRow(GradeSystem system, String grade, GradeSystem otherSystem,
	                                  String otherGrade) {
		int row = system.indexOf(grade);
		assertNotEquals(grade, -1, row);
		assertEquals(grade + " = " + otherGrade, otherSystem.indexOf(otherGrade), row);
	}
}
