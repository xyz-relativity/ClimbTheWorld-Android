package com.climbtheworld.app.map.marker;

import com.climbtheworld.app.storage.database.GeoNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where each climbing style sits on the 3x3 grid of the style block, see
 * {@link MarkerUtils#getStyleIcon}, and the order the styles are listed in everywhere, read off
 * that grid. The middle row holds the unroped styles:
 * <pre>
 * sport   | multipitch | trad
 * boulder | rope dot   | deepwater  &lt;- unroped
 * ice     | toprope    | mixed
 * </pre>
 * The centre cell holds a dot drawn when at least one roped style is present.
 */
public final class StyleGrid {
	/** A style's cell, from the top left, and whether the style needs a rope or gear. */
	public record Cell(int column, int row, boolean roped) {
	}

	private static final Map<GeoNode.ClimbingStyle, Cell> CELLS =
			new EnumMap<>(GeoNode.ClimbingStyle.class);

	static {
		CELLS.put(GeoNode.ClimbingStyle.sport, new Cell(0, 0, true));
		CELLS.put(GeoNode.ClimbingStyle.multipitch, new Cell(1, 0, true));
		CELLS.put(GeoNode.ClimbingStyle.trad, new Cell(2, 0, true));
		CELLS.put(GeoNode.ClimbingStyle.boulder, new Cell(0, 1, false));
		CELLS.put(GeoNode.ClimbingStyle.deepwater, new Cell(2, 1, false));
		CELLS.put(GeoNode.ClimbingStyle.ice, new Cell(0, 2, true));
		CELLS.put(GeoNode.ClimbingStyle.toprope, new Cell(1, 2, true));
		CELLS.put(GeoNode.ClimbingStyle.mixed, new Cell(2, 2, true));
	}

	/**
	 * The order of the styles in every list: first those that need a rope or gear, then the
	 * others, each row by row from the top and left to right. So the top row, then the bottom
	 * row, then the middle one.
	 */
	public static final Comparator<GeoNode.ClimbingStyle> ORDER =
			Comparator.comparing((GeoNode.ClimbingStyle style) -> !cell(style).roped())
					.thenComparingInt(style -> cell(style).row())
					.thenComparingInt(style -> cell(style).column());

	private StyleGrid() {
	}

	public static Cell cell(GeoNode.ClimbingStyle style) {
		return CELLS.get(style);
	}

	/** Every style, in {@link #ORDER}. */
	public static List<GeoNode.ClimbingStyle> all() {
		return sorted(CELLS.keySet());
	}

	/** The styles in {@link #ORDER}, in a new list. */
	public static List<GeoNode.ClimbingStyle> sorted(Collection<GeoNode.ClimbingStyle> styles) {
		List<GeoNode.ClimbingStyle> result = new ArrayList<>(styles);
		result.sort(ORDER);
		return result;
	}
}
